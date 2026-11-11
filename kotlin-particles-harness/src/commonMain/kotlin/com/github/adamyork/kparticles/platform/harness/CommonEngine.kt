package com.github.adamyork.kparticles.platform.harness

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.ParticleEngineDiagnostics
import com.github.adamyork.particles.engine.EngineException
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ParticlePhysicsEngine
import com.github.adamyork.particles.engine.ParticleRenderEngine
import com.github.adamyork.particles.engine.data.CommonImage
import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.service.RuntimeService
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.engine.data.ParticleImageSource

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class CommonEngine<CanvasType> @ParticleScope @Inject constructor(
    val particlePhysicsEngine: ParticlePhysicsEngine,
    val particleEffectRegistry: ParticleEffectRegistry,
    val assetService: AssetService,
    val runtimeService: RuntimeService,
    val platformInterop: PlatformInterop,
    protected val particleRenderEngine: ParticleRenderEngine<CanvasType>,
    protected val imageSource: ParticleImageSource,
    protected val particleEngineDiagnostics: ParticleEngineDiagnostics,
) : Engine {

    override var selectedParticleEffect: ParticleEffect? = null

    abstract var mapItemImage: CommonImage

    abstract var foregroundSurface: Any?

    abstract val mapElementPaint: Any

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        throw EngineException("must implemented")
    }

    override fun manageMap(viewPort: ParticleViewPort) {
        manageParticles(viewPort)
    }

    override fun draw(viewPort: ParticleViewPort, timestamp: Double): DrawResult {
        throw Exception("must be implemented")
    }

    abstract fun getOrCreateForegroundSurface(viewPort: ParticleViewPort): Any

    protected open fun manageParticles(viewPort: ParticleViewPort) {
        throw Exception("must be implemented $viewPort")
    }

}
