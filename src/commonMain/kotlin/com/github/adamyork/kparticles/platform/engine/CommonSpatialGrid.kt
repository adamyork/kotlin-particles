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

    companion object {
        private val FORWARD_NEIGHBOR_OFFSETS = arrayOf(1 to 0, -1 to 1, 0 to 1, 1 to 1)
    }

    private var cellSize = 1.0
    private var columns = 1
    private var rows = 1
    private var originX = 0.0
    private var originY = 0.0
    private var cells: Array<MutableList<Particle>> = arrayOf(ArrayList())

    private var observedMaxReachThisTick = 0.0
    private var requiredCellSizeFromLastTick = 0.0

    override fun currentCellSize(): Double = cellSize

    override fun maxCellOccupancy(): Int = cells.maxOf { it.size }

    override fun beginTick(viewPort: ViewPort) {
        requiredCellSizeFromLastTick = observedMaxReachThisTick
        observedMaxReachThisTick = 0.0
        ensureDimensions(viewPort)
        originX = viewPort.x.toDouble()
        originY = viewPort.y.toDouble()
        for (cell in cells) {
            cell.clear()
        }
    }

    override fun place(particle: Particle) {
        val reach = max(particle.attraction, particle.radius * 2.0)
        if (reach > observedMaxReachThisTick) {
            observedMaxReachThisTick = reach
        }
        val index = cellIndexFor(particle.x, particle.y)
        if (index >= 0) {
            cells[index].add(particle)
        }
    }

    override fun forEachNearbyPair(action: (Particle, Particle) -> Unit) {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cellParticles = cells[row * columns + column]
                for (i in cellParticles.indices) {
                    for (j in i + 1 until cellParticles.size) {
                        action(cellParticles[i], cellParticles[j])
                    }
                }
                for ((columnOffset, rowOffset) in FORWARD_NEIGHBOR_OFFSETS) {
                    val neighborColumn = column + columnOffset
                    val neighborRow = row + rowOffset
                    if (neighborColumn !in 0 until columns || neighborRow !in 0 until rows) continue
                    val neighborParticles = cells[neighborRow * columns + neighborColumn]
                    for (particle in cellParticles) {
                        for (neighborParticle in neighborParticles) {
                            action(particle, neighborParticle)
                        }
                    }
                }
            }
        }
    }

    private fun ensureDimensions(viewPort: ViewPort) {
        val desiredCellSize = max(max(1.0, physicsSettingsService.spatialGridCellSize), requiredCellSizeFromLastTick)
        val desiredColumns = max(1, (viewPort.width / desiredCellSize).toInt() + 1)
        val desiredRows = max(1, (viewPort.height / desiredCellSize).toInt() + 1)
        if (desiredCellSize == cellSize && desiredColumns == columns && desiredRows == rows) return
        cellSize = desiredCellSize
        columns = desiredColumns
        rows = desiredRows
        cells = Array(columns * rows) { ArrayList() }
    }

    private fun cellIndexFor(x: Double, y: Double): Int {
        val column = ((x - originX) / cellSize).toInt()
        val row = ((y - originY) / cellSize).toInt()
        if (column !in 0 until columns || row !in 0 until rows) return -1
        return row * columns + column
    }
}
