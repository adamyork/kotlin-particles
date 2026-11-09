package com.github.adamyork.kparticles.simulation

import com.github.adamyork.particles.engine.data.Particle
import com.github.adamyork.particles.engine.data.ParticleEffectRequest
import com.github.adamyork.particles.engine.AbstractGenerator
import com.github.adamyork.kparticles.platform.effects.ParticleEffects

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class ManualGenerator : AbstractGenerator(ParticleEffects.PROJECTILE) {

    override fun create(request: ParticleEffectRequest): MutableList<Particle> = mutableListOf()

}
