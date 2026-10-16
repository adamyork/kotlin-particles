package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class CollisionContact {

    var normalX: Double = 0.0

    var normalY: Double = 0.0

    var depth: Double = 0.0

    var pointCount: Int = 0

    val pointX: DoubleArray = DoubleArray(MAXIMUM_POINTS)

    val pointY: DoubleArray = DoubleArray(MAXIMUM_POINTS)

    companion object {
        const val MAXIMUM_POINTS: Int = 2
    }
}
