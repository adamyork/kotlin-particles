package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.DrawFunctionData

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class GpuParticleRenderEngine<CanvasType>(
    private val particleEffectRegistry: ParticleEffectRegistry,
    private val drawFunction: ParticleDrawFunction<CanvasType>
) : ParticleRenderEngine<CanvasType> {

    override fun draw(viewPort: ParticleViewPort, canvas: CanvasType) {
        particleEffectRegistry.seal()
        drawFunction(DrawFunctionData(particleEffectRegistry.generators(), viewPort, canvas))
    }

}
