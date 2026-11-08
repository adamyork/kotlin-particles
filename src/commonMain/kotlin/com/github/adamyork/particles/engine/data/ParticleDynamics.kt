package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class ParticleDynamics(
    val velocity: ParticleVector = ParticleVector(),
    val thrust: ParticleVector = ParticleVector(),
    var angularVelocity: Double = 0.0
)
