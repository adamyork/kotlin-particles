package com.github.adamyork.kparticles.android.engine

import android.graphics.Canvas
import com.github.adamyork.kparticles.android.engine.data.AndroidPendingGpuFrame
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.Collision
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.data.CommonImage
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject

/**
 * Android engine variant that follows the wasm GPU particle flow.
 *
 * Particle spawn data is written to a packed GPU-style buffer and consumed by an
 * OpenGL ES compute + render pipeline.
 */
@AppScope
@Inject
class AndroidGpuEngine(
    particlePhysics: ParticlePhysics,
    collision: Collision,
    private val physicsSettingsService: PhysicsSettingsService,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    particleFactory: ParticleFactory
) : AndroidEngine(
    particlePhysics,
    collision,
    particleFactory,
    assetService,
    runtimeService,
    platformInterop
) {

    companion object {
        private const val FLOATS_PER_PARTICLE = 16
        private const val IDX_ALIVE = 7
        private const val IDX_KIND = 12
        private const val KIND_DUST = 1f
        private const val ENGINE_DIAGNOSTIC_LOG_EVERY_N_TICKS = 60
        private const val GPU_BURST_RENDER_COOLDOWN_FRAMES = 300
    }

    private val logger = KotlinLogging.logger {}

    private val gpuParticleBufferCapacity = ParticleFactory.DEFAULT_GPU_PARTICLE_CAPACITY
    private val gpuParticleSpawnBuffer = particleFactory.createGpuParticleComputeBuffer(gpuParticleBufferCapacity)
    private val gpuParticleRuntime = AndroidGpuParticleRuntime()
    private var nextGpuSpawnSlot: Int = 0
    private var previousGpuWrittenSlots: List<Int> = emptyList()
    private var androidPendingGpuFrame: AndroidPendingGpuFrame? = null
    private var diagnosticTickCounter: Int = 0
    private val fireworkTailLifecycleParticles = arrayListOf<Particle>()
    private val fireworkBurstLifecycleParticles = arrayListOf<Particle>()
    private val completedFireworkTailResults = arrayListOf<CompletedParticleResult>()
    private var gpuBurstRenderCooldown: Int = 0

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing GPU engine" }
        super.initialize(collectibleAsset)
        assetService.loadParticleGlShaders()
        gpuParticleRuntime.enable(
            maxParticleCapacity = gpuParticleBufferCapacity,
            computeShader = assetService.particleComputeShaderSource,
            vertexShader = assetService.particleVertexShaderSource,
            fragmentShader = assetService.particleFragmentShaderSource
        )
        gpuParticleRuntime.setAsActiveRuntime()
        logger.info { "Android GL particle runtime initialized with $gpuParticleBufferCapacity slots" }
    }

    override fun manageParticles(particles: ArrayList<Particle>, viewPort: ViewPort) {
        particlePhysics.applyParticlePhysics(particles, viewPort, completedParticleResults)
        val gpuFrameParticles = arrayListOf<Particle>().apply {
            addAll(particles)
            addAll(fireworkTailLifecycleParticles)
            addAll(fireworkBurstLifecycleParticles)
        }
        val spawnWriteResult = particleFactory.writeGpuParticleSpawnBuffer(
            mapParticles = gpuFrameParticles,
            targetBuffer = gpuParticleSpawnBuffer,
            maxParticles = gpuParticleBufferCapacity,
            startSlot = nextGpuSpawnSlot,
            previouslyWrittenSlots = previousGpuWrittenSlots
        )
        val mapDustCount = gpuFrameParticles.count { it.type.name == "DUST" }
        previousGpuWrittenSlots = spawnWriteResult.writtenSlots
        val spawnedParticleCount = spawnWriteResult.activeCount
        if (spawnedParticleCount > 0) {
            nextGpuSpawnSlot = (nextGpuSpawnSlot + spawnedParticleCount) % gpuParticleBufferCapacity
        }
        val gpuIsActive = spawnedParticleCount > 0
                || fireworkTailLifecycleParticles.isNotEmpty()
                || gpuBurstRenderCooldown > 0
        val effectiveParticleCount = if (gpuIsActive) spawnedParticleCount.coerceAtLeast(1) else 0
        diagnosticTickCounter++
        if (diagnosticTickCounter % ENGINE_DIAGNOSTIC_LOG_EVERY_N_TICKS == 0) {
            logger.info {
                "[GPU][Engine] mapParticles=${particles.size}, mapDust=$mapDustCount, " +
                        "tailLifecycle=${fireworkTailLifecycleParticles.size}, burstLifecycle=${fireworkBurstLifecycleParticles.size}, " +
                        "spawned=$spawnedParticleCount, effectiveCount=$effectiveParticleCount, " +
                        "burstCooldown=$gpuBurstRenderCooldown, " +
                        "dirtyRanges=${spawnWriteResult.dirtySlotRanges.size}, " +
                        "bufferDust=${countAliveParticlesByKind(gpuParticleSpawnBuffer, KIND_DUST)}"
            }
        }
        particles.clear()
        androidPendingGpuFrame = AndroidPendingGpuFrame(
            activeParticleCount = effectiveParticleCount,
            viewPortWidth = viewPort.width.toFloat(),
            viewPortHeight = viewPort.height.toFloat(),
            deltaTimeSeconds = runtimeService.getDeltaTimeSeconds(),
            gravity = physicsSettingsService.gravity.toFloat(),
            burstFrameGrowthMultiplier = physicsSettingsService.collisionParticleFrameGrowthMultiplier.toFloat(),
            burstSpeedCoefficient = physicsSettingsService.collisionParticleSpeedCoefficient.toFloat(),
            projectileSpeed = physicsSettingsService.projectileSpeed.toFloat(),
            mapItemReturnSpeed = physicsSettingsService.mapItemReturnParticleSpeed.toFloat(),
            mapItemReturnMinTravelDist = physicsSettingsService.mapItemReturnParticleMinTravelDist.toFloat()
        )
    }

    override fun drawParticles(
        particles: ArrayList<Particle>,
        viewPort: ViewPort,
        canvas: Canvas,
        mapItemImage: CommonImage?
    ) {
        val frame = androidPendingGpuFrame ?: return
        androidPendingGpuFrame = null
        gpuParticleRuntime.submitFrame(
            activeParticleCount = frame.activeParticleCount,
            sourceBuffer = gpuParticleSpawnBuffer,
            viewPort = viewPort,
            viewPortWidth = frame.viewPortWidth,
            viewPortHeight = frame.viewPortHeight,
            sizeMultiplier = physicsSettingsService.collisionParticleSizeMultiplier,
            deltaTimeSeconds = frame.deltaTimeSeconds,
            gravity = frame.gravity,
            burstFrameGrowthMultiplier = frame.burstFrameGrowthMultiplier,
            burstSpeedCoefficient = frame.burstSpeedCoefficient,
            projectileSpeed = frame.projectileSpeed,
            mapItemReturnSpeed = frame.mapItemReturnSpeed,
            mapItemReturnMinTravelDist = frame.mapItemReturnMinTravelDist
        )
    }

    private fun countAliveParticlesByKind(buffer: FloatArray, kind: Float): Int {
        if (buffer.isEmpty()) return 0
        var count = 0
        var base = 0
        while ((base + IDX_KIND) < buffer.size) {
            val alive = buffer[base + IDX_ALIVE] > 0.5f
            if (alive && buffer[base + IDX_KIND] == kind) {
                count++
            }
            base += FLOATS_PER_PARTICLE
        }
        return count
    }


}
