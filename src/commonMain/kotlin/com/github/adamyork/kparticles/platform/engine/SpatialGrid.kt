package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface SpatialGrid {

    fun beginTick(viewPort: ViewPort)

    fun place(particle: Particle)

    fun forEachCollisionPair(action: (Particle, Particle) -> Unit)

    fun forEachParticleWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit)

    fun placedParticles(): List<Particle>
}
