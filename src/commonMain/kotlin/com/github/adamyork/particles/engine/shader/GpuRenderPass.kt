package com.github.adamyork.particles.engine.shader

import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import com.github.adamyork.particles.engine.data.ParticleSpriteSheet

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class GpuRenderPass(
    public val renderStyle: ParticleRenderStyle,
    public val blendMode: GpuBlendMode,
    public val firstInstance: Int,
    public val instanceCount: Int,
    public val spriteSheet: ParticleSpriteSheet? = null
) {

    public companion object {

        public fun create(styleShaders: List<GpuStyleShader>, styleRanges: Map<ParticleRenderStyle, IntRange>): List<GpuRenderPass> {
            val renderPasses = ArrayList<GpuRenderPass>()
            for (styleShader in styleShaders) {
                val styleRange = styleRanges[styleShader.renderStyle] ?: continue
                renderPasses.add(
                    GpuRenderPass(
                        styleShader.renderStyle,
                        styleShader.blendMode,
                        styleRange.first,
                        styleRange.last - styleRange.first + 1,
                        styleShader.imageSource?.spriteSheet
                    )
                )
            }
            return renderPasses
        }

    }

}
