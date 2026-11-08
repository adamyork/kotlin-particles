package com.github.adamyork.particles.engine.shader

import com.github.adamyork.particles.engine.GpuStyleUniformSource
import com.github.adamyork.particles.engine.data.ParticleImageSource
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal class ImageStyleUniformSource(
    override val renderStyle: ParticleRenderStyle,
    private val imageSource: ParticleImageSource
) : GpuStyleUniformSource {

    override val floatCount: Int = 3

    override fun writeUniforms(targetBuffer: FloatArray, startIndex: Int) {
        val spriteSheet = imageSource.spriteSheet
        targetBuffer[startIndex] = (spriteSheet?.frameWidth ?: 1).toFloat()
        targetBuffer[startIndex + 1] = (spriteSheet?.frameHeight ?: 1).toFloat()
        targetBuffer[startIndex + 2] = (spriteSheet?.frameCount ?: 1).toFloat()
    }

}
