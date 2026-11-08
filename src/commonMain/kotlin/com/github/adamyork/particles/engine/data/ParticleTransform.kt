package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class ParticleTransform(
    val position: ParticleVector,
    val origin: ParticleVector = ParticleVector(),
    var angle: Double = 0.0
)
