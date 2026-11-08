package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class ParticleSpriteSheet(
    public val image: CommonImage,
    public val encodedBytes: ByteArray,
    public val frameWidth: Int,
    public val frameHeight: Int,
    public val frameCount: Int
)
