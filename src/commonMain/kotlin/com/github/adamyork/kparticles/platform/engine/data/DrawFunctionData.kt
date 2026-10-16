package com.github.adamyork.kparticles.platform.engine.data

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class DrawFunctionData<CanvasType>(
    val generators: List<ParticleEffectGenerator>,
    val viewPort: ViewPort,
    val canvas: CanvasType,
    val spriteSheet: SpriteSheet?
)