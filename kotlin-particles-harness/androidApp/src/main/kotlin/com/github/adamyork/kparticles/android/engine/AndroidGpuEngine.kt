package com.github.adamyork.kparticles.android.engine

import android.graphics.Canvas
import com.github.adamyork.particles.android.engine.data.AndroidPendingGpuFrame
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.particles.service.RuntimeService
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.particles.engine.data.DiagnosticCounter
import com.github.adamyork.particles.engine.ParticlePhysicsEngine
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ParticleRenderEngine
import com.github.adamyork.particles.engine.GpuParticleStateWriter
import com.github.adamyork.particles.engine.shader.GpuRenderPass
import com.github.adamyork.particles.engine.shader.GpuShaderComposer
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.android.engine.AndroidGpuRenderers
import com.github.adamyork.particles.engine.data.ParticleImageSource

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AndroidGpuEngine(
    particlePhysicsEngine: ParticlePhysicsEngine,
    particleEffectRegistry: ParticleEffectRegistry,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    particleRenderEngine: ParticleRenderEngine<Canvas>,
    imageSource: ParticleImageSource,
    private val gpuRenderers: AndroidGpuRenderers,
    private val gpuParticleStateWriter: GpuParticleStateWriter,
    private val gpuStyleShaders: List<GpuStyleShader>,
    engineDiagnostics: EngineDiagnostics
) : AndroidEngine(
    particlePhysicsEngine,
    particleEffectRegistry,
    assetService,
    runtimeService,
    platformInterop,
    particleRenderEngine,
    imageSource,
    engineDiagnostics
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
        particleEffectRegistry.seal()
        val styleSlotRanges = gpuParticleStateWriter.getStyleSlotRanges(gpuParticleBufferCapacity)
        val renderPasses = GpuRenderPass.create(gpuStyleShaders, styleSlotRanges)
        val composedVertexShader = GpuShaderComposer.composeGlslVertex(assetService.particleVertexShaderSource, gpuStyleShaders)
        val composedFragmentShader = GpuShaderComposer.composeGlslFragment(assetService.particleFragmentShaderSource, gpuStyleShaders)
        logger.info {
            "[GPU][Passes] capacity=$gpuParticleBufferCapacity passes=${
                renderPasses.joinToString { pass -> "${pass.renderStyle.id}(code=${pass.renderStyle.code}, ${pass.blendMode}, first=${pass.firstInstance}, count=${pass.instanceCount})" }
            }"
        }
        gpuParticleRuntime.enable(
            maxParticleCapacity = gpuParticleBufferCapacity,
            computeShader = assetService.particleComputeShaderSource,
            vertexShader = composedVertexShader,
            fragmentShader = composedFragmentShader,
            renderPasses = renderPasses,
            styleShaders = gpuStyleShaders
        )
        gpuParticleRuntime.setAsActiveRuntime()
        logger.info { "Android GL particle runtime initialized with $gpuParticleBufferCapacity slots" }
    }

    override fun manageParticles(viewPort: ParticleViewPort) {
        val subframeSchedule = runtimeService.getSubframeSchedule()
        engineDiagnostics.count(DiagnosticCounter.SUBFRAMES, subframeSchedule.subframeCount)
        val frameStartWorldTime = worldTime
        worldTime += subframeSchedule.subframeCount * subframeSchedule.subframeDeltaTime
        val spawnWriteResult = gpuParticleStateWriter.writeGpuParticleStateSpawnBuffer(
            gpuParticleSpawnBuffer,
            gpuParticleBufferCapacity,
            startSlot = nextGpuSpawnSlot,
            previouslyWrittenSlots = previousGpuWrittenSlots
        )
        previousGpuWrittenSlots = spawnWriteResult.writtenSlots
        if (spawnWriteResult.activeCount > 0) {
            nextGpuSpawnSlot = (nextGpuSpawnSlot + spawnWriteResult.activeCount) % gpuParticleBufferCapacity
        }
        val spriteTicksPerFrame = runtimeService.getSpriteTicksPerFrame()
        gpuRenderers.pendingGpuFrame = AndroidPendingGpuFrame(
            dirtySlotRanges = spawnWriteResult.dirtySlotRanges,
            subframeCount = subframeSchedule.subframeCount,
            deltaTime = subframeSchedule.subframeDeltaTime,
            worldTime = frameStartWorldTime,
            spriteTicksPerFrame = spriteTicksPerFrame
        )
    }

}
