package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface GpuStyleUniformSource {

    public val renderStyle: ParticleRenderStyle

    public val floatCount: Int

    public fun writeUniforms(targetBuffer: FloatArray, startIndex: Int)

}
