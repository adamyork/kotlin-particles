package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface ParticleCollision {

    public fun applyParticleCollision(generators: List<ParticleEffectGenerator>, viewPort: ParticleViewPort, deltaTime: Double)

    public fun getAllPlacedParticles(): List<Particle>

    public fun forEachParticleWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit)
}
