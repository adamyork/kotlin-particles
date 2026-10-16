package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticlePhysics {

    fun applyParticlePhysics(
        generators: List<ParticleEffectGenerator>,
        viewPort: ViewPort,
        completedParticleResults: ArrayList<CompletedParticleResult>
    )

    fun applyGravitation(collision: ParticleCollision)

    fun applyParticleAccretion(firstParticle: Particle, secondParticle: Particle): Boolean

}
