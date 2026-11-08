package com.github.adamyork.particles.engine.shader

import com.github.adamyork.particles.engine.GpuStyleUniformSource
import com.github.adamyork.particles.engine.data.ParticleImageSource
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class GpuStyleShader(
    public val renderStyle: ParticleRenderStyle,
    public val wgslSource: String,
    public val glslVertexSource: String,
    public val glslFragmentSource: String,
    public val blendMode: GpuBlendMode = GpuBlendMode.ALPHA,
    public val uniformSource: GpuStyleUniformSource? = null,
    public val imageSource: ParticleImageSource? = null
)
