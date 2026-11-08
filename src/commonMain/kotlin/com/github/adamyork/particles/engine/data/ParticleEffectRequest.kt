package com.github.adamyork.particles.engine.data

import com.github.adamyork.particles.common.data.ParticleViewPort

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class ParticleEffectRequest(
    val x: Double,
    val y: Double,
    val viewPort: ParticleViewPort,
    val destinationX: Double,
    val destinationY: Double,
    val direction: ParticleDirection
)
