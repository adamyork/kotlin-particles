package com.github.adamyork.kparticles.platform.engine.data

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class CircleDimensions(
    var radius: Double,
    val maxRadius: Double = DEFAULT_MAX_RADIUS,
    val scaleRate: Double = 0.0
) : ParticleDimensions {

    companion object {
        const val DEFAULT_MAX_RADIUS: Double = 5.0
    }

    override fun boundingWidth(): Double = radius * 2.0

    override fun boundingHeight(): Double = radius * 2.0

    override fun collisionRadius(): Double = radius

    override fun boundingRadius(): Double = radius

    override fun area(): Double = PI * radius * radius

    override fun advance(deltaTime: Double) {
        if (scaleRate == 0.0) return
        radius = (radius * (1.0 + scaleRate * deltaTime)).coerceIn(0.0, maxRadius)
    }

    override fun fitArea(area: Double) {
        radius = sqrt(area / PI)
    }

    override fun scaleBy(factor: Double) {
        radius *= factor
    }

    override fun scaledBy(factor: Double): ParticleDimensions = copy(radius = radius * factor)
}
