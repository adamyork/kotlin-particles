package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.ParticleCollision
import com.github.adamyork.kparticles.platform.engine.EngineException
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.data.DrawFunctionData
import com.github.adamyork.kparticles.platform.engine.data.RenderType
import com.github.adamyork.kparticles.platform.service.AssetService
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
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class WasmJsGpuEngine(
    particlePhysics: ParticlePhysics,
    particleFactory: ParticleFactory,
    private val particleLayer: WasmJsUiParticleLayer,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    collision: ParticleCollision,
    gpuRenderers: WasmGpuRenderers
) : WasmJsEngine(
    particlePhysics,
    particleFactory,
    assetService,
    runtimeService,
    platformInterop,
    collision,
    gpuRenderers
) {

    private companion object {
        const val OVERLAY_CANVAS_WAIT_TIMEOUT_MS = 3_000
        const val OVERLAY_CANVAS_WAIT_STEP_MS = 16L
    }

    private val logger = KotlinLogging.logger {}

    private val gpuParticleRenderer = gpuRenderers.gpuParticleRenderer
    private val gpuParticleBufferCapacity = ParticleFactory.DEFAULT_GPU_PARTICLE_CAPACITY
    private val gpuParticleSpawnBuffer = particleFactory.createGpuParticleStateBuffer(gpuParticleBufferCapacity)
    private var gpuRendererReady: Boolean = false
    private var nextGpuSpawnSlot: Int = 0
    private var previousGpuWrittenSlots: List<Int> = emptyList()
    private var worldTime: Double = 0.0

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

    override fun manageParticles(viewPort: ViewPort) {
        val deltaTime = runtimeService.getDeltaTimeCoefficient()
        worldTime += deltaTime
        val spawnWriteResult = particleFactory.writeGpuParticleStateSpawnBuffer(
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
        val engineProperties = assetService.appProperties.engine
        val spriteTicksPerFrame = (engineProperties.tickTargetPerSec.toDouble() /
                engineProperties.spriteAnimationFramePerSec.toDouble()).coerceAtLeast(1.0)
        gpuParticleRenderer.updateGpuParticleBuffer(
            sourceBuffer = gpuParticleSpawnBuffer,
            dirtySlotRanges = spawnWriteResult.dirtySlotRanges,
            deltaTime = deltaTime,
            worldTime = worldTime,
            spriteTicksPerFrame = spriteTicksPerFrame,
            viewPort = viewPort
        )
    }

    override fun drawParticles(
        viewPort: ViewPort,
        canvas: Canvas
    ) {
        renderers.getForType(RenderType.SIMPLE_SHAPE)(DrawFunctionData(particleFactory.generators(), viewPort, canvas, spriteSheet))
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
