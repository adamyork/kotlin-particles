package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CpuStyleRenderers<CanvasType>(renderers: Collection<CpuStyleRenderer<CanvasType>>) {

    private val renderersByStyle = HashMap<ParticleRenderStyle, CpuStyleRenderer<CanvasType>>()

    init {
        for (renderer in renderers) {
            if (renderersByStyle.put(renderer.renderStyle, renderer) != null) {
                throw EngineException("More than one CPU renderer is registered for render style ${renderer.renderStyle.id}")
            }
        }
    }

    public fun rendererFor(renderStyle: ParticleRenderStyle): CpuStyleRenderer<CanvasType> =
        renderersByStyle[renderStyle] ?: throw EngineException("No CPU renderer is registered for render style ${renderStyle.id}")

}
