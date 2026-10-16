package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ParticleMaterial(
    var density: Double,
    val restitution: Double = 0.0,
    val hardness: Int = 0,
    val adhesion: Double = 0.0,
    val friction: Double = DEFAULT_FRICTION,
    val staticFriction: Double = DEFAULT_FRICTION,
    val rollingResistance: Double = DEFAULT_ROLLING_RESISTANCE
) {

    companion object {
        const val DEFAULT_FRICTION: Double = 0.0001
        const val DEFAULT_ROLLING_RESISTANCE: Double = 0.0001
    }
}
