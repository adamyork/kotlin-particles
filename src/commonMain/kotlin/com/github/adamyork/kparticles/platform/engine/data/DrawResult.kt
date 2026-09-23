package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class DrawResult(
    val foregroundImage: CommonImage?,
) {
    companion object {
        val EMPTY_DRAW_RESULT = DrawResult(
            null,
        )
    }
}
