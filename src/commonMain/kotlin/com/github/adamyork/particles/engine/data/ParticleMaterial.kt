package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class ParticleMaterial(
    var density: Double,
    val restitution: Double = 0.0,
    val hardness: Int = 0,
    val adhesion: Double = 0.0,
    val friction: Double = DEFAULT_FRICTION,
    val staticFriction: Double = DEFAULT_FRICTION,
    val rollingResistance: Double = DEFAULT_ROLLING_RESISTANCE,
    val exertsGravity: Boolean = true
) {

    public companion object {
        public const val DEFAULT_FRICTION: Double = 0.0001
        public const val DEFAULT_ROLLING_RESISTANCE: Double = 0.0001
    }
}
