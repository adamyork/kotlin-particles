package com.github.adamyork.kparticles.platform.engine.data

import com.github.adamyork.kparticles.platform.common.data.ViewPort

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class DrawFunctionData<CanvasType>(
    val particles: List<Particle>,
    val viewPort: ViewPort,
    val canvas: CanvasType,
    val spriteSheet: SpriteSheet?
)