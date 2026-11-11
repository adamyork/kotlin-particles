package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal class CollisionContact {

    companion object {
        const val MAXIMUM_POINTS: Int = 2
    }

    var normalX: Double = 0.0

    var normalY: Double = 0.0

    var depth: Double = 0.0

    var pointCount: Int = 0

    val pointX: DoubleArray = DoubleArray(MAXIMUM_POINTS)

    val pointY: DoubleArray = DoubleArray(MAXIMUM_POINTS)
}
