package com.github.adamyork.kparticles.platform.harness

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface Engine {

    var selectedParticleEffect: ParticleEffect?

    suspend fun initialize(collectibleAsset: ImageAsset)

    fun manageMap(viewPort: ParticleViewPort)

    fun draw(viewPort: ParticleViewPort, timestamp: Double): DrawResult

}
