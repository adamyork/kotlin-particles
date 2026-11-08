package com.github.adamyork.kparticles.platform.harness

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.EngineDiagnostics
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
    protected val engineDiagnostics: EngineDiagnostics,
) : Engine {

    override var selectedParticleEffect: ParticleEffect? = null

    abstract var mapItemImage: CommonImage

    abstract var foregroundSurface: Any?

    abstract val mapElementPaint: Any

    abstract fun getOrCreateForegroundSurface(viewPort: ParticleViewPort): Any

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        throw EngineException("must implemented")
    }

    override fun manageMap(viewPort: ParticleViewPort) {
        manageParticles(viewPort)
    }

    protected open fun manageParticles(viewPort: ParticleViewPort) {
        throw Exception("must be implemented")
    }

    override fun draw(viewPort: ParticleViewPort, timestamp: Double): DrawResult {
        throw Exception("must be implemented")
    }

}
