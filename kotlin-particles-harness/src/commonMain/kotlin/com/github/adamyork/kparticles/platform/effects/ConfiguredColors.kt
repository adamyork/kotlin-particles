package com.github.adamyork.kparticles.platform.effects

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.RGBA

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
object ConfiguredColors {

    fun createColor(configuredColor: RGBA): Color {
        return Color(
            configuredColor.r.toFloat() / 255f,
            configuredColor.g.toFloat() / 255f,
            configuredColor.b.toFloat() / 255f,
            configuredColor.a.toFloat() / 255f
        )
    }

}
