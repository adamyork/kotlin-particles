package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.CommonImage
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.DrawFunctionData
import com.github.adamyork.kparticles.platform.engine.data.DrawResult
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect
import com.github.adamyork.kparticles.platform.engine.data.RenderType
import com.github.adamyork.kparticles.platform.engine.data.SpriteSheet
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class CommonEngine<CanvasType> @AppScope @Inject constructor(
    val particlePhysics: ParticlePhysics,
    val particleFactory: ParticleFactory,
    val assetService: AssetService,
    val runtimeService: RuntimeService,
    val platformInterop: PlatformInterop,
    val collision: ParticleCollision,
    protected val renderers: Renderers<CanvasType>,
) : Engine {

    private val logger = KotlinLogging.logger {}

    protected val completedParticleResults: ArrayList<CompletedParticleResult> = ArrayList()

    override var selectedParticleEffect: ParticleEffect? = null

    abstract var mapItemImage: CommonImage

    protected var spriteSheet: SpriteSheet? = null

    private val generatorsByRenderType: Map<RenderType, List<ParticleEffectGenerator>> by lazy {
        RenderType.entries.associateWith { renderType ->
            particleFactory.generators().filter { generator -> generator.effect.renderType == renderType }
        }
    }

    abstract var foregroundSurface: Any?

    abstract val mapElementPaint: Any

    abstract fun getOrCreateForegroundSurface(viewPort: ViewPort): Any

    protected open fun drawParticles(
        viewPort: ViewPort,
        canvas: CanvasType
    ) {
        for (renderType in RenderType.entries) {
            val renderTypeGenerators = generatorsByRenderType.getValue(renderType)
            if (renderTypeGenerators.none { generator -> generator.particles.isNotEmpty() }) continue
            val drawFunctionData = DrawFunctionData(renderTypeGenerators, viewPort, canvas, spriteSheet)
            renderers.getForType(renderType)(drawFunctionData)
        }
    }

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        throw EngineException("must implemented")
    }

    override fun manageMap(viewPort: ViewPort) {
        manageParticles(viewPort)
    }

    protected open fun manageParticles(viewPort: ViewPort) {
        throw Exception("must be implemented")
    }

    override fun draw(viewPort: ViewPort, timestamp: Double): DrawResult {
        throw Exception("must be implemented")
    }

}
