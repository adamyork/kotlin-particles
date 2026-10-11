package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.data.DrawFunctionData
import com.github.adamyork.kparticles.platform.engine.data.RenderType

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface Renderers<CanvasType> {

    fun getForType(renderType: RenderType): ParticleDrawFunction<CanvasType>

}

typealias ParticleDrawFunction<CanvasType> = (DrawFunctionData<CanvasType>) -> Unit