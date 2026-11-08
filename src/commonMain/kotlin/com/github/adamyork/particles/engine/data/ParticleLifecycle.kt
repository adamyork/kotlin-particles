package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class ParticleLifecycle(
    var age: Double = 0.0,
    val delay: Double = 0.0,
    val lifetime: Double,
    val pinnedUntil: Double = 0.0
) {

    public fun isVisible(): Boolean = age >= delay

    public fun isExpired(): Boolean = age >= lifetime + delay
}
