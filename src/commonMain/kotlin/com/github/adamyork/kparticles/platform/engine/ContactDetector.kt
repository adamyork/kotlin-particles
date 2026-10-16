package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.data.CollisionContact
import com.github.adamyork.kparticles.platform.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ContactDetector {

    fun detect(firstParticle: Particle, secondParticle: Particle, contact: CollisionContact): Boolean
}
