package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ParticleDynamics(
    val velocity: ParticleVector = ParticleVector(),
    val thrust: ParticleVector = ParticleVector(),
    var angularVelocity: Double = 0.0
)
