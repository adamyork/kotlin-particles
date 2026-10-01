package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
enum class ParticleType(
    val displayName: String
) {
    DUST("Dust"),
    PROJECTILE("Projectile"),
    ITEM_RETURN("Item Return"),
    COLLISION("Collision"),
    FIREWORK_TAIL("Firework Tails"),
    FIREWORK_BURST("Firework Burst"),
    COLLIDING_BITS("Colliding Bits"),
    GOBBLER("Gobbler"),
    BLACK_HOLE("Black Hole"),
    STRESS_TEST("Stress Test")
}

