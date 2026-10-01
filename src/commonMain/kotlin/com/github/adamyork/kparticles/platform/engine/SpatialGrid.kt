package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.Particle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface SpatialGrid {

    fun currentCellSize(): Double

    fun maxCellOccupancy(): Int

    fun beginTick(viewPort: ViewPort)

    fun place(particle: Particle)

    fun forEachNearbyPair(action: (Particle, Particle) -> Unit)
}
