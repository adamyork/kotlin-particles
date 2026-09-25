package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.CommonImage
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.DrawResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class CommonEngine @AppScope @Inject constructor(
    val physics: Physics,
    val particles: Particles,
    val assetService: AssetService,
    val runtimeService: RuntimeService,
    val platformInterop: PlatformInterop,
    val collision: Collision,
) : Engine {

    private val logger = KotlinLogging.logger {}

    protected val completedParticleResults: ArrayList<CompletedParticleResult> = ArrayList()

    abstract var mapItemImage: CommonImage

    abstract var foregroundSurface: Any?

    abstract val mapElementPaint: Any
    abstract val particlePaint: Any
    abstract val mapItemReturnPaint: Any

    abstract fun getOrCreateForegroundSurface(viewPort: ViewPort): Any

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        throw EngineException("must implemented")
    }

    override fun manageMap(particles: ArrayList<Particle>, viewPort: ViewPort) {
        manageMapParticles(particles, viewPort)
    }

    protected open fun manageMapParticles(particles: ArrayList<Particle>, viewPort: ViewPort) {

    }

    override fun draw(particles: ArrayList<Particle>, viewPort: ViewPort, timestamp: Double): DrawResult {
        throw Exception("must be implemented")
    }

}
