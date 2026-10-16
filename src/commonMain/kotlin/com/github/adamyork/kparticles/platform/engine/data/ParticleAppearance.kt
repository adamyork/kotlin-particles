package com.github.adamyork.kparticles.platform.engine.data

import androidx.compose.ui.graphics.Color

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ParticleAppearance(
    var color: Color,
    val startColor: Color = color,
    val endColor: Color = color,
    var alpha: Double = 1.0,
    val endAlpha: Double = alpha,
    var initialAlpha: Double = alpha,
    var alphaMultiplier: Double = 0.0
)
