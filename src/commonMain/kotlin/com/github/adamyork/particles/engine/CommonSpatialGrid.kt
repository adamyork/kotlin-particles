package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.Particle
import com.github.adamyork.particles.service.PhysicsSettingsService
import kotlin.math.max

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonSpatialGrid(
    private val physicsSettingsService: PhysicsSettingsService,
    engineDiagnostics: EngineDiagnostics
) : SpatialGrid {

    private val grid = SortedCellGrid(engineDiagnostics)
    private val particles = ArrayList<Particle>()
    private var viewPortX = 0.0
    private var viewPortY = 0.0
    private var viewPortWidth = 0.0
    private var viewPortHeight = 0.0
    private var isGridBuilt = false

    override fun beginTick(viewPort: ParticleViewPort) {
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

    override fun getAllPlacedParticles(): List<Particle> = particles

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
