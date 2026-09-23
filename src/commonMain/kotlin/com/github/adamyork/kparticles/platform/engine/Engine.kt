package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.DrawResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.data.ImageAsset

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface Engine {

    suspend fun initialize(collectibleAsset: ImageAsset)

    fun manageMap(particles: List<Particle>, viewPort: ViewPort)

    fun draw(particles: List<Particle>, viewPort: ViewPort, timestamp: Double): DrawResult

}
