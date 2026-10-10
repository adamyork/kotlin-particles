package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.Collision
import com.github.adamyork.kparticles.platform.engine.EngineException
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.data.CommonImage
import com.github.adamyork.kparticles.platform.engine.data.DrawResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import com.github.adamyork.kparticles.wasm.engine.data.WasmJsImage
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
    private val physicsSettingsService: PhysicsSettingsService,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    collision: Collision
) : WasmJsEngine(
    particlePhysics,
    particleFactory,
    assetService,
    runtimeService,
    platformInterop,
    collision
) {

    private companion object {
        const val OVERLAY_CANVAS_WAIT_TIMEOUT_MS = 3_000
        const val OVERLAY_CANVAS_WAIT_STEP_MS = 16L
    }

    private val logger = KotlinLogging.logger {}

    private val gpuParticleRenderer = WasmJsGpuParticleRenderer()
    private val gpuParticleBufferCapacity = ParticleFactory.DEFAULT_GPU_PARTICLE_CAPACITY
    private val gpuParticleSpawnBuffer = particleFactory.createGpuParticleComputeBuffer(gpuParticleBufferCapacity)
    private var gpuRendererReady: Boolean = false
    private var nextGpuSpawnSlot: Int = 0
    private var previousGpuWrittenSlots: List<Int> = emptyList()

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

    override fun manageParticles(particles: ArrayList<Particle>, viewPort: ViewPort) {
        particlePhysics.applyParticlePhysics(particles, viewPort, completedParticleResults)
        val spawnWriteResult = particleFactory.writeGpuParticleSpawnBuffer(
            particles,
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
        val deltaTimeSeconds = runtimeService.getDeltaTimeSeconds()
        gpuParticleRenderer.updateGpuParticleBuffer(
            activeParticleCount = spawnedParticleCount,
            sourceBuffer = gpuParticleSpawnBuffer,
            dirtySlotRanges = spawnWriteResult.dirtySlotRanges,
            deltaTimeSeconds = deltaTimeSeconds,
            viewPort = viewPort,
            particleFactory = particleFactory,
            gravity = physicsSettingsService.gravity.toFloat(),
            tickTargetPerSecond = assetService.appProperties.engine.tickTargetPerSec,
            speedCoefficient = physicsSettingsService.collisionParticleSpeedCoefficient.toFloat()
        )
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

    override fun draw(
        particles: ArrayList<Particle>,
        viewPort: ViewPort,
        timestamp: Double
    ): DrawResult {
        val foregroundSurface = getOrCreateForegroundSurface(viewPort)
        val foregroundCanvas = foregroundSurface.canvas
        foregroundCanvas.clear(0x00000000)
        drawParticles(particles, viewPort, foregroundCanvas, mapItemImage)
        val foregroundImage = foregroundSurface.makeImageSnapshot()
        runtimeService.lastPaintTime = timestamp
        return DrawResult(
            foregroundImage = WasmJsImage(foregroundImage)
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
