package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticlePhysics {

    fun applyParticlePhysics(
        particles: ArrayList<Particle>,
        viewPort: ViewPort,
        completedParticleResults: ArrayList<CompletedParticleResult>
    )

    fun applyParticleCollisionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        distance: Double,
        overlapDistance: Double,
        normalX: Double,
        normalY: Double
    )

    fun applyParticleAttractionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        distance: Double,
        normalX: Double,
        normalY: Double
    )

}
