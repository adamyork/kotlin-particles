package com.github.adamyork.particles.engine.data

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.ParticleEffectGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class DrawFunctionData<CanvasType>(
    val generators: List<ParticleEffectGenerator>,
    val viewPort: ParticleViewPort,
    val canvas: CanvasType
)