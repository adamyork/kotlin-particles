package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
enum class ParticleEffect(
    val displayName: String,
    val renderType: RenderType
) {
    DUST("Dust", RenderType.SIMPLE_SHAPE),
    PROJECTILE("Projectile", RenderType.SIMPLE_SHAPE),
    BLOB_PROJECTILE("Blob Projectile", RenderType.COMPLEX_SHAPE),
    ITEM_RETURN("Item Return", RenderType.SPRITE),
    ANIMATED_ITEM_RETURN("Animated Item Return", RenderType.ANIMATED_SPRITE),
    BUBBLE("Bubbles", RenderType.COMPLEX_SHAPE),
    COLLISION("Collision", RenderType.SIMPLE_SHAPE),
    FIREWORK_TAIL("Firework Tails", RenderType.SIMPLE_SHAPE),
    FIREWORK_BURST("Firework Burst", RenderType.SIMPLE_SHAPE),
    COLLIDING_BITS("Colliding Bits", RenderType.SIMPLE_SHAPE),
    GOBBLER("Gobbler", RenderType.SIMPLE_SHAPE),
    BLACK_HOLE("Black Hole", RenderType.SIMPLE_SHAPE),
    STRESS_TEST("Stress Test", RenderType.SIMPLE_SHAPE),
    SHATTER("Shatter", RenderType.SIMPLE_SHAPE)
}

