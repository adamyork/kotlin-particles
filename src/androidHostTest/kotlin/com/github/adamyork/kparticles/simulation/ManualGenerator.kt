package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffectRequest
import com.github.adamyork.kparticles.platform.engine.effect.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class ManualGenerator : AbstractGenerator(ParticleEffect.PROJECTILE) {

    override fun create(request: ParticleEffectRequest): MutableList<Particle> = mutableListOf()

}
