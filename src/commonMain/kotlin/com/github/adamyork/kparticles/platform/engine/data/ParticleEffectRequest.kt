package com.github.adamyork.kparticles.platform.engine.data

import com.github.adamyork.kparticles.platform.common.data.ViewPort

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ParticleEffectRequest(
    val x: Double,
    val y: Double,
    val viewPort: ViewPort,
    val destinationX: Double,
    val destinationY: Double,
    val direction: Direction
)
