package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
enum class GpuRenderKind(val code: Float) {
    SHAPE(0f),
    DUST(1f),
    SPRITE(2f),
    ANIMATED_SPRITE(3f),
    BLOB(4f),
    BUBBLE(5f),
    DRIP(6f)
}
