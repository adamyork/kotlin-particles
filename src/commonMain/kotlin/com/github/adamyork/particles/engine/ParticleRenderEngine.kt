package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface ParticleRenderEngine<CanvasType> {

    public fun draw(viewPort: ParticleViewPort, canvas: CanvasType)

}
