package com.github.adamyork.kparticles.android.engine

import android.graphics.Canvas
import com.github.adamyork.kparticles.android.engine.data.AndroidPendingGpuFrame
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.ParticleCollision
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.data.DrawFunctionData
import com.github.adamyork.kparticles.platform.engine.data.RenderType
import com.github.adamyork.kparticles.platform.service.AssetService
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
    collision: ParticleCollision,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    particleFactory: ParticleFactory,
    private val gpuRenderers: AndroidGpuRenderers
) : AndroidEngine(
    particlePhysics,
    collision,
    particleFactory,
    assetService,
    runtimeService,
    platformInterop,
    gpuRenderers
) {

    private val logger = KotlinLogging.logger {}

    private val gpuParticleBufferCapacity = gpuRenderers.gpuParticleBufferCapacity
    private val gpuParticleSpawnBuffer = gpuRenderers.gpuParticleSpawnBuffer
    private val gpuParticleRuntime = gpuRenderers.gpuParticleRuntime
    private var nextGpuSpawnSlot: Int = 0
    private var previousGpuWrittenSlots: List<Int> = emptyList()
    private var worldTime: Double = 0.0

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

    override fun manageParticles(viewPort: ViewPort) {
        val deltaTime = runtimeService.getDeltaTimeCoefficient()
        worldTime += deltaTime
        val spawnWriteResult = particleFactory.writeGpuParticleStateSpawnBuffer(
            gpuParticleSpawnBuffer,
            gpuParticleBufferCapacity,
            startSlot = nextGpuSpawnSlot,
            previouslyWrittenSlots = previousGpuWrittenSlots
        )
        previousGpuWrittenSlots = spawnWriteResult.writtenSlots
        if (spawnWriteResult.activeCount > 0) {
            nextGpuSpawnSlot = (nextGpuSpawnSlot + spawnWriteResult.activeCount) % gpuParticleBufferCapacity
        }
        val engineProperties = assetService.appProperties.engine
        val spriteTicksPerFrame = (engineProperties.tickTargetPerSec.toDouble() /
                engineProperties.spriteAnimationFramePerSec.toDouble()).coerceAtLeast(1.0)
        gpuRenderers.pendingGpuFrame = AndroidPendingGpuFrame(
            dirtySlotRanges = spawnWriteResult.dirtySlotRanges,
            deltaTime = deltaTime,
            worldTime = worldTime,
            spriteTicksPerFrame = spriteTicksPerFrame
        )
    }

    override fun drawParticles(
        viewPort: ViewPort,
        canvas: Canvas
    ) {
        renderers.getForType(RenderType.SIMPLE_SHAPE)(DrawFunctionData(particleFactory.generators(), viewPort, canvas, spriteSheet))
    }

}
