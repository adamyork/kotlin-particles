package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.max

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CommonSpatialGrid(
    private val physicsSettingsService: PhysicsSettingsService
) : SpatialGrid {

    private val grid = UniformCellGrid()
    private val particles = ArrayList<Particle>()
    private var viewPortX = 0.0
    private var viewPortY = 0.0
    private var viewPortWidth = 0.0
    private var viewPortHeight = 0.0
    private var isGridBuilt = false

    override fun beginTick(viewPort: ViewPort) {
        particles.clear()
        viewPortX = viewPort.x.toDouble()
        viewPortY = viewPort.y.toDouble()
        viewPortWidth = viewPort.width.toDouble()
        viewPortHeight = viewPort.height.toDouble()
        isGridBuilt = false
    }

    override fun place(particle: Particle) {
        particles.add(particle)
    }

    override fun placedParticles(): List<Particle> = particles

    override fun forEachCollisionPair(action: (Particle, Particle) -> Unit) {
        buildGridIfNeeded()
        grid.forEachNearbyPair(action)
    }

    override fun forEachParticleWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit) {
        buildGridIfNeeded()
        grid.forEachWithin(centerX, centerY, range, action)
    }

    private fun buildGridIfNeeded() {
        if (isGridBuilt) return
        val cellSize = max(1.0, physicsSettingsService.spatialGridCellSize)
        grid.build(particles, cellSize, viewPortX, viewPortY, viewPortWidth, viewPortHeight)
        isGridBuilt = true
    }
}
