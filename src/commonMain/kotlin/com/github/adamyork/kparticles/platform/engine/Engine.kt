package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.DrawResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect
import com.github.adamyork.kparticles.platform.service.data.ImageAsset

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface Engine {

    var selectedParticleEffect: ParticleEffect?

    suspend fun initialize(collectibleAsset: ImageAsset)

    fun manageMap(particles: ArrayList<Particle>, viewPort: ViewPort)

    fun draw(particles: ArrayList<Particle>, viewPort: ViewPort, timestamp: Double): DrawResult

}
