package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class ParticleEffect(
    public val id: String,
    public val displayName: String,
    public val renderStyle: ParticleRenderStyle = ParticleRenderStyle.SHAPE
) {

    override fun toString(): String = id

}
