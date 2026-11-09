package com.github.adamyork.kparticles.wasm.engine

import androidx.compose.ui.graphics.asSkiaBitmap
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.kparticles.platform.harness.DrawResult
import com.github.adamyork.kparticles.platform.assets.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.particles.service.RuntimeService
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import com.github.adamyork.particles.wasm.engine.data.WasmJsImage
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.*
import com.github.adamyork.particles.engine.data.DiagnosticPhase
import com.github.adamyork.particles.engine.ParticlePhysicsEngine
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ParticleRenderEngine
import com.github.adamyork.kparticles.platform.harness.CommonEngine

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
open class WasmJsEngine(
    particlePhysicsEngine: ParticlePhysicsEngine,
    particleEffectRegistry: ParticleEffectRegistry,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    particleRenderEngine: ParticleRenderEngine<Canvas>,
    imageSource: ParticleImageSource,
    engineDiagnostics: EngineDiagnostics,
) : CommonEngine<Canvas>(
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

    override var mapItemImage: CommonImage = WasmJsImage(
        Image.makeFromBitmap(AbstractPlatformAssetService.getTmpImageBitmap().asSkiaBitmap())
    )
    override var foregroundSurface: Any? = null
    override val mapElementPaint = Paint().apply { isAntiAlias = true }

    override fun getOrCreateForegroundSurface(viewPort: ParticleViewPort): Surface =
        (foregroundSurface as Surface?) ?: Surface.makeRaster(ImageInfo.makeN32Premul(viewPort.width, viewPort.height))
            .also {
                foregroundSurface = it
            }

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing CPU engine" }
        engineDiagnostics.engineLabel = "Wasm CPU"
        mapItemImage = WasmJsImage(
            Image.makeFromBitmap(collectibleAsset.imageAndBytes.imageBitmap.asSkiaBitmap())
        )
        val sheetFrameCount = (collectibleAsset.imageAndBytes.imageBitmap.width / collectibleAsset.width.coerceAtLeast(1)).coerceAtLeast(1)
        imageSource.spriteSheet = ParticleSpriteSheet(
            mapItemImage,
            collectibleAsset.imageAndBytes.bytes,
            collectibleAsset.width,
            collectibleAsset.height,
            sheetFrameCount
        )
    }

    override fun manageParticles(viewPort: ParticleViewPort) {
        particlePhysicsEngine.applyPhysics(viewPort)
    }

    override fun draw(
        viewPort: ParticleViewPort,
        timestamp: Double
    ): DrawResult {
        val foregroundSurface = getOrCreateForegroundSurface(viewPort)
        val foregroundCanvas = foregroundSurface.canvas
        foregroundCanvas.clear(0x00000000)
        val drawMark = engineDiagnostics.startTimer()
        particleRenderEngine.draw(viewPort, foregroundCanvas)
        engineDiagnostics.stopTimer(DiagnosticPhase.DRAW_PARTICLES, drawMark)
        val snapshotMark = engineDiagnostics.startTimer()
        val foregroundImage = foregroundSurface.makeImageSnapshot()
        engineDiagnostics.stopTimer(DiagnosticPhase.SNAPSHOT, snapshotMark)
        for (generator in particleEffectRegistry.generators()) {
            engineDiagnostics.countEffectParticles(generator.effect, generator.particles.size)
        }
        engineDiagnostics.endFrame(timestamp)
        runtimeService.lastPaintTime = timestamp
        return DrawResult(
            foregroundImage = WasmJsImage(foregroundImage),
        )
    }

}
