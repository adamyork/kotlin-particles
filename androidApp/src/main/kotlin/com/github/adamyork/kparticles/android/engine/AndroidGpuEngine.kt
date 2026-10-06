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
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
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

    private val logger = KotlinLogging.logger {}

    private val gpuParticleBufferCapacity = ParticleFactory.DEFAULT_GPU_PARTICLE_CAPACITY
    private val gpuParticleSpawnBuffer = particleFactory.createGpuParticleComputeBuffer(gpuParticleBufferCapacity)
    private val gpuParticleRuntime = AndroidGpuParticleRuntime()
    private var nextGpuSpawnSlot: Int = 0
    private var previousGpuWrittenSlots: List<Int> = emptyList()
    private var androidPendingGpuFrame: AndroidPendingGpuFrame? = null

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing GPU engine" }
        super.initialize(collectibleAsset)
        assetService.loadParticleGlShaders()
        gpuParticleRuntime.enable(
            maxParticleCapacity = gpuParticleBufferCapacity,
            computeShader = assetService.particleComputeShaderSource,
            vertexShader = assetService.particleVertexShaderSource,
            fragmentShader = assetService.particleFragmentShaderSource,
            mapItemTextureBytes = collectibleAsset.imageAndBytes.bytes,
            mapItemSpriteWidth = collectibleAsset.width,
            mapItemSpriteHeight = collectibleAsset.height
        )
        gpuParticleRuntime.setAsActiveRuntime()
        logger.info { "Android GL particle runtime initialized with $gpuParticleBufferCapacity slots" }
    }

    override fun manageParticles(particles: ArrayList<Particle>, viewPort: ViewPort) {
        particlePhysics.applyParticlePhysics(particles, viewPort, completedParticleResults)
        val spawnWriteResult = particleFactory.writeGpuParticleSpawnBuffer(
            mapParticles = particles,
            targetBuffer = gpuParticleSpawnBuffer,
            maxParticles = gpuParticleBufferCapacity,
            startSlot = nextGpuSpawnSlot,
            previouslyWrittenSlots = previousGpuWrittenSlots
        )
        previousGpuWrittenSlots = spawnWriteResult.writtenSlots
        val spawnedParticleCount = spawnWriteResult.activeCount
        if (spawnedParticleCount > 0) {
            nextGpuSpawnSlot = (nextGpuSpawnSlot + spawnedParticleCount) % gpuParticleBufferCapacity
        }
        particles.clear()
        val tunedSpeed = physicsSettingsService.collisionParticleSpeedCoefficient.toFloat().coerceAtLeast(0.05f)
        androidPendingGpuFrame = AndroidPendingGpuFrame(
            activeParticleCount = spawnedParticleCount,
            viewPortWidth = viewPort.width.toFloat(),
            viewPortHeight = viewPort.height.toFloat(),
            deltaTimeSeconds = runtimeService.getDeltaTimeSeconds(),
            gravity = physicsSettingsService.gravity.toFloat(),
            tickRate = assetService.appProperties.engine.tickTargetPerSec.toFloat(),
            simulationSpeed = (1f + (tunedSpeed * 8f)).coerceAtLeast(1f),
            gravityBoost = (1.5f + (tunedSpeed * 6f)).coerceAtLeast(1f),
            lifetimeDecay = (1f + (tunedSpeed * 6f)).coerceAtLeast(1f)
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
            tickRate = frame.tickRate,
            simulationSpeed = frame.simulationSpeed,
            gravityBoost = frame.gravityBoost,
            lifetimeDecay = frame.lifetimeDecay
        )
    }
}
