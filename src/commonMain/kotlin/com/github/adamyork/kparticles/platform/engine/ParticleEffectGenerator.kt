package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AssetService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticleEffectGenerator {

    val effect: ParticleEffect

    fun populateColor(assetService: AssetService) {}

    fun create(request: ParticleEffectRequest): MutableList<Particle>

}
