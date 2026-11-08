package com.github.adamyork.kparticles.platform.gui.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ScreenDimensions(
    val width: Int,
    val height: Int
) {
    companion object {
        const val MAX_WIDTH = 1024
        const val MAX_HEIGHT = 768
        fun fromScreenResolution(screenWidth: Int, screenHeight: Int): ScreenDimensions {
            return ScreenDimensions(
                screenWidth.coerceAtMost(MAX_WIDTH),
                screenHeight.coerceAtMost(MAX_HEIGHT)
            )
        }
    }
}

