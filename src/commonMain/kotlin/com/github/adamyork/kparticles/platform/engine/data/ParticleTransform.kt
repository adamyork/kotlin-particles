package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ParticleTransform(
    val position: ParticleVector,
    val origin: ParticleVector = ParticleVector(),
    var angle: Double = 0.0
)
