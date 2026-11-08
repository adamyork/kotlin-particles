package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface SpatialGrid {

    public fun beginTick(viewPort: ParticleViewPort)

    public fun place(particle: Particle)

    public fun forEachCollisionPair(action: (Particle, Particle) -> Unit)

    public fun forEachParticleWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit)

    public fun getAllPlacedParticles(): List<Particle>
}
