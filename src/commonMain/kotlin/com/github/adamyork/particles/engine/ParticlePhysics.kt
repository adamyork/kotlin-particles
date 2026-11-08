package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface ParticlePhysics {

    public fun applyParticlePhysics(
        generators: List<ParticleEffectGenerator>,
        viewPort: ParticleViewPort,
        deltaTime: Double
    )

    public fun applyGravitation(collision: ParticleCollision, deltaTime: Double)

    public fun applyParticleAccretion(firstParticle: Particle, secondParticle: Particle): Boolean

}
