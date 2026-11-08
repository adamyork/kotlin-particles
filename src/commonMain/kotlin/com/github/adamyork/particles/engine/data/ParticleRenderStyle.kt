package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class ParticleRenderStyle(public val id: String, public val code: Float) {

    override fun toString(): String = id

    public companion object {
        public const val FIRST_CUSTOM_CODE: Float = 16f

        public val SHAPE: ParticleRenderStyle = ParticleRenderStyle("SHAPE", 0f)
        public val IMAGE: ParticleRenderStyle = ParticleRenderStyle("IMAGE", 1f)

        public val BUILT_IN: List<ParticleRenderStyle> = listOf(SHAPE, IMAGE)
    }

}
