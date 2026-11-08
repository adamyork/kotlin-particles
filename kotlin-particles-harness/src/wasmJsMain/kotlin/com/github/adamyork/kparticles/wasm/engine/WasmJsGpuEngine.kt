package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.EngineException
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.particles.service.RuntimeService
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import com.github.adamyork.kparticles.wasm.gui.WasmJsUiParticleLayer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.browser.window
import kotlinx.coroutines.delay
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.Canvas
import org.w3c.dom.HTMLCanvasElement
import kotlin.time.Duration.Companion.milliseconds
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.particles.engine.data.DiagnosticCounter
import com.github.adamyork.particles.engine.ParticlePhysicsEngine
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ParticleRenderEngine
import com.github.adamyork.particles.engine.GpuParticleStateWriter
import com.github.adamyork.particles.engine.shader.GpuRenderPass
import com.github.adamyork.particles.engine.shader.GpuShaderComposer
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.wasm.engine.WasmGpuRenderers
import com.github.adamyork.particles.engine.data.ParticleImageSource

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class WasmJsGpuEngine(
    particlePhysicsEngine: ParticlePhysicsEngine,
    particleEffectRegistry: ParticleEffectRegistry,
    private val particleLayer: WasmJsUiParticleLayer,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    particleRenderEngine: ParticleRenderEngine<Canvas>,
    imageSource: ParticleImageSource,
    gpuRenderers: WasmGpuRenderers,
    private val gpuParticleStateWriter: GpuParticleStateWriter,
    private val gpuStyleShaders: List<GpuStyleShader>,
    engineDiagnostics: EngineDiagnostics
) : WasmJsEngine(
    particlePhysicsEngine,
    particleEffectRegistry,
    assetService,
    runtimeService,
    platformInterop,
    particleRenderEngine,
    imageSource,
    engineDiagnostics
) {

    private companion object {
        const val OVERLAY_CANVAS_WAIT_TIMEOUT_MS = 3_000
        const val OVERLAY_CANVAS_WAIT_STEP_MS = 16L
    }

    private val logger = KotlinLogging.logger {}

    private val gpuParticleRenderer = gpuRenderers.gpuParticleRenderer
    private val gpuParticleBufferCapacity = GpuParticleStateWriter.DEFAULT_GPU_PARTICLE_CAPACITY
    private val gpuParticleSpawnBuffer = gpuParticleStateWriter.createGpuParticleStateBuffer(gpuParticleBufferCapacity)
    private var gpuRendererReady: Boolean = false
    private var nextGpuSpawnSlot: Int = 0
    private var previousGpuWrittenSlots: List<Int> = emptyList()
    private var worldTime: Double = 0.0

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing GPU engine" }
        engineDiagnostics.engineLabel = "Wasm GPU"
        super.initialize(collectibleAsset)
        val overlayCanvas = waitForOverlayCanvasReady()
            ?: throw IllegalStateException("WebGPU particle overlay canvas could not be created")
        particleEffectRegistry.seal()
        val styleSlotRanges = gpuParticleStateWriter.getStyleSlotRanges(gpuParticleBufferCapacity)
        val renderPasses = GpuRenderPass.create(gpuStyleShaders, styleSlotRanges)
        val composedShaderSource = GpuShaderComposer.composeWgsl(assetService.particleShaderSource, gpuStyleShaders)
        logger.info {
            "[GPU][Passes] capacity=$gpuParticleBufferCapacity passes=${
                renderPasses.joinToString { pass -> "${pass.renderStyle.id}(code=${pass.renderStyle.code}, ${pass.blendMode}, first=${pass.firstInstance}, count=${pass.instanceCount})" }
            }"
        }
        gpuRendererReady = gpuParticleRenderer.initialize(
            maxParticleCapacity = gpuParticleBufferCapacity,
            particlesShaderSource = composedShaderSource,
            overlayCanvas = overlayCanvas,
            renderPasses = renderPasses,
            styleShaders = gpuStyleShaders
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
        val spawnedParticleCount = spawnWriteResult.activeCount
        previousGpuWrittenSlots = spawnWriteResult.writtenSlots
        if (spawnedParticleCount > 0) {
            nextGpuSpawnSlot = (nextGpuSpawnSlot + spawnedParticleCount) % gpuParticleBufferCapacity
        }
        val spriteTicksPerFrame = runtimeService.getSpriteTicksPerFrame()
        gpuParticleRenderer.updateGpuParticleBuffer(
            sourceBuffer = gpuParticleSpawnBuffer,
            dirtySlotRanges = spawnWriteResult.dirtySlotRanges,
            subframeCount = subframeSchedule.subframeCount,
            deltaTime = subframeSchedule.subframeDeltaTime,
            worldTime = frameStartWorldTime,
            spriteTicksPerFrame = spriteTicksPerFrame,
            viewPort = viewPort
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
