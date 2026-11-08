package com.github.adamyork.kparticles.platform.harness

import com.github.adamyork.particles.engine.data.CommonImage

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class DrawResult(
    val foregroundImage: CommonImage?
) {
    companion object {
        val EMPTY_DRAW_RESULT: DrawResult = DrawResult(
            null
        )
    }
}
