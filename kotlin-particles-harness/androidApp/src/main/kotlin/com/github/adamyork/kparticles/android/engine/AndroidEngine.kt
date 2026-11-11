package com.github.adamyork.kparticles.android.engine

import com.github.adamyork.particles.engine.data.ParticleEngineType
import android.graphics.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.createBitmap
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.assets.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.harness.CommonEngine
import com.github.adamyork.kparticles.platform.harness.DrawResult
import com.github.adamyork.particles.android.engine.data.AndroidImage
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.ParticleEngineDiagnostics
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ParticlePhysicsEngine
import com.github.adamyork.particles.engine.ParticleRenderEngine
import com.github.adamyork.particles.engine.data.CommonImage
import com.github.adamyork.particles.engine.data.ParticleImageSource
import com.github.adamyork.particles.engine.data.ParticleSpriteSheet
import com.github.adamyork.particles.service.RuntimeService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
open class AndroidEngine(
    particlePhysicsEngine: ParticlePhysicsEngine,
    particleEffectRegistry: ParticleEffectRegistry,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    particleRenderEngine: ParticleRenderEngine<Canvas>,
    imageSource: ParticleImageSource,
    particleEngineDiagnostics: ParticleEngineDiagnostics,
) : CommonEngine<Canvas>(
    particlePhysicsEngine,
    particleEffectRegistry,
    assetService,
    runtimeService,
    platformInterop,
    particleRenderEngine,
    imageSource,
    particleEngineDiagnostics
) {

    override var foregroundSurface: Any? = null

    override val mapElementPaint: Any = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    override var mapItemImage: CommonImage = AndroidImage(
        AbstractPlatformAssetService.getTmpImageBitmap().asAndroidBitmap()
    )

    private val logger = KotlinLogging.logger {}

    override fun getOrCreateForegroundSurface(viewPort: ParticleViewPort): Any {
        val current = foregroundSurface as? Bitmap
        if (current == null || current.width != viewPort.width || current.height != viewPort.height) {
            foregroundSurface = createBitmap(viewPort.width, viewPort.height)
        }
        return foregroundSurface as Bitmap
    }

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing CPU engine" }
        particleEngineDiagnostics.engineType = ParticleEngineType.CPU
        withContext(Dispatchers.Default) {
            mapItemImage = AndroidImage(collectibleAsset.imageAndBytes.imageBitmap.asAndroidBitmap())
            val sheetFrameCount =
                (collectibleAsset.imageAndBytes.imageBitmap.width / collectibleAsset.width.coerceAtLeast(1)).coerceAtLeast(
                    1
                )
            imageSource.spriteSheet = ParticleSpriteSheet(
                mapItemImage,
                collectibleAsset.imageAndBytes.bytes,
                collectibleAsset.width,
                collectibleAsset.height,
                sheetFrameCount
            )
        }
    }

    override fun draw(
        viewPort: ParticleViewPort,
        timestamp: Double
    ): DrawResult {
        val foregroundBitmap = getOrCreateForegroundSurface(viewPort) as Bitmap
        val foregroundCanvas = Canvas(foregroundBitmap)
        foregroundCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        particleRenderEngine.draw(viewPort, foregroundCanvas)
        particleEngineDiagnostics.endFrame(timestamp)
        runtimeService.lastPaintTime = timestamp
        return DrawResult(
            foregroundImage = AndroidImage(foregroundBitmap),
        )
    }

    override fun manageParticles(viewPort: ParticleViewPort) {
        particlePhysicsEngine.applyPhysics(viewPort)
    }

}
