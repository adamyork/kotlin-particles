package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticleCollision {

    fun applyParticleCollision(generators: List<ParticleEffectGenerator>, viewPort: ViewPort)

    fun placedParticles(): List<Particle>

    fun forEachParticleWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit)
}
