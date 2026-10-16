package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.data.CollisionContact
import com.github.adamyork.kparticles.platform.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ContactSolver {

    fun solve(
        firstParticle: Particle,
        secondParticle: Particle?,
        contact: CollisionContact,
        restitution: Double,
        friction: Double,
        staticFriction: Double,
        restitutionThreshold: Double
    )
}
