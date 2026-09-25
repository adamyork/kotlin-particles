package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface Collision {

    fun applyParticleCollision(particles: ArrayList<Particle>)
}
