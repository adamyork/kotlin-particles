package com.github.adamyork.particles.engine.data

import androidx.compose.ui.graphics.Color

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class ParticleAppearance(
    var color: Color,
    val startColor: Color = color,
    val endColor: Color = color,
    var alpha: Double = 1.0,
    val endAlpha: Double = alpha,
    var initialAlpha: Double = alpha,
    var alphaMultiplier: Double = 0.0
) {

    public fun packedArgb(): Int {
        val alphaChannel = (alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
        val redChannel = (color.red * 255f).toInt().coerceIn(0, 255)
        val greenChannel = (color.green * 255f).toInt().coerceIn(0, 255)
        val blueChannel = (color.blue * 255f).toInt().coerceIn(0, 255)
        return (alphaChannel shl 24) or (redChannel shl 16) or (greenChannel shl 8) or blueChannel
    }

}
