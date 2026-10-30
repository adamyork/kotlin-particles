package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ParticleLifecycle(
    var age: Double = 0.0,
    val delay: Double = 0.0,
    val lifetime: Double,
    val pinnedUntil: Double = 0.0
) {

    fun isVisible(): Boolean = age >= delay

    fun isExpired(): Boolean = age >= lifetime + delay
}
