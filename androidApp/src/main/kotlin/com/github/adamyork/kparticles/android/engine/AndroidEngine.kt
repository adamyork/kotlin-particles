package com.github.adamyork.kparticles.android.engine

import android.graphics.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.createBitmap
import com.github.adamyork.kparticles.android.engine.data.AndroidImage
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.ParticleCollision
import com.github.adamyork.kparticles.platform.engine.CommonEngine
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.Renderers
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.kparticles.platform.engine.EngineDiagnostics

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
open class AndroidEngine(
    particlePhysics: ParticlePhysics,
    collision: ParticleCollision,
    particleFactory: ParticleFactory,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    renderers: Renderers<Canvas>,
    engineDiagnostics: EngineDiagnostics
) : CommonEngine<Canvas>(
    particlePhysics,
    particleFactory,
    assetService,
    runtimeService,
    platformInterop,
    collision,
    renderers,
    engineDiagnostics
) {

    private val logger = KotlinLogging.logger {}

    override var foregroundSurface: Any? = null

    override val mapElementPaint: Any = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    override var mapItemImage: CommonImage = AndroidImage(
        AbstractPlatformAssetService.getTmpImageBitmap().asAndroidBitmap()
    )

    override fun getOrCreateForegroundSurface(viewPort: ViewPort): Any {
        val current = foregroundSurface as? Bitmap
        if (current == null || current.width != viewPort.width || current.height != viewPort.height) {
            foregroundSurface = createBitmap(viewPort.width, viewPort.height)
        }
        return foregroundSurface as Bitmap
    }

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing CPU engine" }
        engineDiagnostics.engineLabel = "Android CPU"
        withContext(Dispatchers.Default) {
            mapItemImage = AndroidImage(collectibleAsset.imageAndBytes.imageBitmap.asAndroidBitmap())
            spriteSheet = SpriteSheet(mapItemImage, collectibleAsset.width, collectibleAsset.height)
        }
    }

    override fun manageParticles(viewPort: ViewPort) {
        val subframePlan = runtimeService.getSubframePlan()
        engineDiagnostics.beginEngineWork()
        engineDiagnostics.count(DiagnosticCounter.SUBFRAMES, subframePlan.subframeCount)
        repeat(subframePlan.subframeCount) {
            val physicsMark = engineDiagnostics.startTimer()
            particlePhysics.applyParticlePhysics(
                particleFactory.generators(), viewPort, completedParticleResults, subframePlan.subframeDeltaTime
            )
            engineDiagnostics.stopTimer(DiagnosticPhase.PHYSICS, physicsMark)
            val collisionMark = engineDiagnostics.startTimer()
            collision.applyParticleCollision(particleFactory.generators(), viewPort, subframePlan.subframeDeltaTime)
            engineDiagnostics.stopTimer(DiagnosticPhase.COLLISION_TOTAL, collisionMark)
        }
    }

    override fun draw(
        viewPort: ViewPort,
        timestamp: Double
    ): DrawResult {
        val foregroundBitmap = getOrCreateForegroundSurface(viewPort) as Bitmap
        val foregroundCanvas = Canvas(foregroundBitmap)
        foregroundCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val drawMark = engineDiagnostics.startTimer()
        drawParticles(viewPort, foregroundCanvas)
        engineDiagnostics.stopTimer(DiagnosticPhase.DRAW_PARTICLES, drawMark)
        for (generator in particleFactory.generators()) {
            engineDiagnostics.countEffectParticles(generator.effect, generator.particles.size)
        }
        engineDiagnostics.endFrame(timestamp)
        runtimeService.lastPaintTime = timestamp
        return DrawResult(
            foregroundImage = AndroidImage(foregroundBitmap),
        )
    }

}
