package com.github.adamyork.kparticles.platform.engine.data

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class RectDimensions(
    var width: Double,
    var height: Double,
    val maxWidth: Double = DEFAULT_MAX_WIDTH,
    val maxHeight: Double = DEFAULT_MAX_HEIGHT,
    val scaleRate: Double = 0.0
) : ParticleDimensions {

    companion object {
        const val DEFAULT_MAX_WIDTH: Double = 10.0
        const val DEFAULT_MAX_HEIGHT: Double = 10.0
    }

    override fun boundingWidth(): Double = width

    override fun boundingHeight(): Double = height

    override fun collisionRadius(): Double = width / 2.0

    override fun boundingRadius(): Double = hypot(width, height) / 2.0

    override fun area(): Double = width * height

    override fun advance(deltaTime: Double) {
        if (scaleRate == 0.0) return
        val scale = 1.0 + scaleRate * deltaTime
        width = (width * scale).coerceIn(0.0, maxWidth)
        height = (height * scale).coerceIn(0.0, maxHeight)
    }

    override fun fitArea(area: Double) {
        val sideLength = sqrt(area)
        width = sideLength
        height = sideLength
    }

    override fun scaleBy(factor: Double) {
        width *= factor
        height *= factor
    }

    override fun scaledBy(factor: Double): ParticleDimensions = copy(width = width * factor, height = height * factor)
}
