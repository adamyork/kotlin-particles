package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.DrawFunctionData

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public typealias ParticleDrawFunction<CanvasType> = (DrawFunctionData<CanvasType>) -> Unit
