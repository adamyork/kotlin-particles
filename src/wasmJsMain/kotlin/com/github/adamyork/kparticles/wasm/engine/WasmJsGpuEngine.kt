package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.EngineException
import com.github.adamyork.kparticles.platform.engine.Particles
import com.github.adamyork.kparticles.platform.engine.Physics
import com.github.adamyork.kparticles.platform.engine.data.CommonImage
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import com.github.adamyork.kparticles.wasm.gui.WasmJsUiParticleLayer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.browser.window
import kotlinx.coroutines.delay
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.Canvas
import org.w3c.dom.HTMLCanvasElement
import kotlin.time.Duration.Companion.milliseconds

/**
 * Experimental wasm engine that renders particles through WebGL overlay buffers.
 */
@AppScope
@Inject
class WasmJsGpuEngine(
    physics: Physics,
    particles: Particles,
    private val particleLayer: WasmJsUiParticleLayer,
    private val physicsSettingsService: PhysicsSettingsService,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop
) : WasmJsEngine(
    physics,
    particles,
    assetService,
    runtimeService,
    platformInterop
) {

    private companion object {
        const val OVERLAY_CANVAS_WAIT_TIMEOUT_MS = 3_000
        const val OVERLAY_CANVAS_WAIT_STEP_MS = 16L
    }

    private val logger = KotlinLogging.logger {}

    private val gpuParticleRenderer = WasmJsGpuParticleRenderer()
    private val gpuParticleBufferCapacity = Particles.DEFAULT_GPU_PARTICLE_CAPACITY
    private val gpuParticleSpawnBuffer = particles.createGpuParticleComputeBuffer(gpuParticleBufferCapacity)
    private var gpuRendererReady: Boolean = false
    private var nextGpuSpawnSlot: Int = 0
    private var previousGpuWrittenSlots: List<Int> = emptyList()
    private val fireworkTailLifecycleParticles = arrayListOf<Particle>()
    private val completedFireworkTailResults = arrayListOf<CompletedParticleResult>()

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing GPU engine" }
        super.initialize(collectibleAsset)
        val overlayCanvas = waitForOverlayCanvasReady()
            ?: throw IllegalStateException("WebGPU particle overlay canvas could not be created")
        gpuRendererReady = gpuParticleRenderer.initialize(
            maxParticleCapacity = gpuParticleBufferCapacity,
            particlesShaderSource = assetService.particleShaderSource,
            overlayCanvas = overlayCanvas,
            mapItemTextureBytes = collectibleAsset.imageAndBytes.bytes,
            mapItemFirstCellWidth = collectibleAsset.width,
            mapItemFirstCellHeight = collectibleAsset.height
        )
        if (!gpuRendererReady) {
            val dpr = window.devicePixelRatio
            val userAgent = window.navigator.userAgent
            throw EngineException(
                "WebGPU particle renderer failed to initialize; WasmJsGpuEngine requires WebGPU. " +
                        "dpr=$dpr, userAgent=$userAgent"
            )
        }
        logger.info { "GPU particle renderer initialized" }
    }

    override fun manageMapParticles(particles: ArrayList<Particle>, viewPort: ViewPort) {
        physics.applyParticlePhysics(particles, viewPort, completedParticleResults)
//        val allCollectiblesFound = scoreService.allFound()
//        gameMap.state = when (gameMap.state) {
//            GameMapState.COLLECTING if allCollectiblesFound -> GameMapState.COMPLETING
//            GameMapState.COMPLETING if !allCollectiblesFound -> GameMapState.COLLECTING
//            else -> gameMap.state
//        }
//        val spawnWriteResult = particles.writeGpuParticleSpawnBuffer(
//            player,
//            gameMap.particles,
//            gpuParticleSpawnBuffer,
//            gpuParticleBufferCapacity,
//            startSlot = nextGpuSpawnSlot,
//            previouslyWrittenSlots = previousGpuWrittenSlots
//        )
//        val spawnedParticleCount = spawnWriteResult.activeCount
//        previousGpuWrittenSlots = spawnWriteResult.writtenSlots
//        if (spawnedParticleCount > 0) {
//            nextGpuSpawnSlot = (nextGpuSpawnSlot + spawnedParticleCount) % gpuParticleBufferCapacity
//        }
//        (particles as ArrayList<Particle>).clear()
//        val deltaTimeSeconds = runtimeService.getDeltaTimeSeconds()
//        gpuParticleRenderer.updateGpuParticleBuffer(
//            activeParticleCount = spawnedParticleCount,
//            sourceBuffer = gpuParticleSpawnBuffer,
//            dirtySlotRanges = spawnWriteResult.dirtySlotRanges,
//            deltaTimeSeconds = deltaTimeSeconds,
//            viewPort = viewPort,
//            particles = particles,
//            gravity = physicsSettingsService.gravity.toFloat(),
//            tickTargetPerSecond = assetService.appProperties.engine.tickTargetPerSec,
//            speedCoefficient = physicsSettingsService.collisionParticleSpeedCoefficient.toFloat(),
//            dustSpeedCoefficient = physicsSettingsService.dustParticleSpeedCoefficient.toFloat(),
//            projectileSpeed = physicsSettingsService.projectileSpeed.toFloat(),
//            mapItemReturnSpeed = physicsSettingsService.mapItemReturnParticleSpeed.toFloat(),
//            mapItemReturnMinTravelDist = physicsSettingsService.mapItemReturnParticleMinTravelDist.toFloat()
//        )
    }

    override fun drawParticles(
        particles: ArrayList<Particle>,
        viewPort: ViewPort,
        canvas: Canvas,
        mapItemImage: CommonImage?
    ) {
        gpuParticleRenderer.draw(
            viewPort = viewPort,
            sizeMultiplier = physicsSettingsService.collisionParticleSizeMultiplier
        )
    }

    private suspend fun waitForOverlayCanvasReady(): HTMLCanvasElement? {
        val deadline = window.performance.now() + OVERLAY_CANVAS_WAIT_TIMEOUT_MS
        while (window.performance.now() < deadline) {
            val overlayCanvas = particleLayer.getOverlayCanvas()
            if (overlayCanvas != null) {
                return overlayCanvas
            }
            delay(OVERLAY_CANVAS_WAIT_STEP_MS.milliseconds)
        }
        return particleLayer.getOverlayCanvas()
    }

}
