package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.data.Particle
import kotlin.math.max

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class UniformCellGrid {

    private companion object {
        private val FORWARD_COLUMN_OFFSETS = intArrayOf(1, -1, 0, 1)
        private val FORWARD_ROW_OFFSETS = intArrayOf(0, 1, 1, 1)
    }

    private var cellSize = 1.0
    private var columns = 1
    private var rows = 1
    private var originX = 0.0
    private var originY = 0.0
    private var cellStarts = IntArray(2)
    private var cellCursors = IntArray(1)
    private var particleCellIndices = IntArray(0)
    private var sortedParticles = arrayOfNulls<Particle>(0)
    private var smallParticleCount = 0
    private val largeParticles = ArrayList<Particle>()

    fun build(
        particles: List<Particle>,
        cellSize: Double,
        originX: Double,
        originY: Double,
        width: Double,
        height: Double
    ) {
        this.cellSize = cellSize
        this.originX = originX
        this.originY = originY
        columns = max(1, (width / cellSize).toInt() + 1)
        rows = max(1, (height / cellSize).toInt() + 1)
        val cellCount = columns * rows
        if (cellStarts.size < cellCount + 1) {
            cellStarts = IntArray(cellCount + 1)
            cellCursors = IntArray(cellCount)
        } else {
            cellStarts.fill(0, 0, cellCount + 1)
        }
        if (particleCellIndices.size < particles.size) {
            particleCellIndices = IntArray(particles.size)
        }
        largeParticles.clear()
        for (particleIndex in particles.indices) {
            val particle = particles[particleIndex]
            var cellIndex = cellIndexFor(particle.transform.position.x, particle.transform.position.y)
            if (cellIndex >= 0 && particle.boundingRadius() * 2.0 > cellSize) {
                largeParticles.add(particle)
                cellIndex = -1
            }
            particleCellIndices[particleIndex] = cellIndex
            if (cellIndex >= 0) cellStarts[cellIndex + 1]++
        }
        for (cellIndex in 0 until cellCount) {
            cellStarts[cellIndex + 1] += cellStarts[cellIndex]
            cellCursors[cellIndex] = cellStarts[cellIndex]
        }
        smallParticleCount = cellStarts[cellCount]
        if (sortedParticles.size < smallParticleCount) {
            sortedParticles = arrayOfNulls(smallParticleCount)
        }
        for (particleIndex in particles.indices) {
            val cellIndex = particleCellIndices[particleIndex]
            if (cellIndex >= 0) {
                sortedParticles[cellCursors[cellIndex]++] = particles[particleIndex]
            }
        }
    }

    fun forEachNearbyPair(action: (Particle, Particle) -> Unit) {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cellIndex = row * columns + column
                val cellStart = cellStarts[cellIndex]
                val cellEnd = cellStarts[cellIndex + 1]
                for (firstIndex in cellStart until cellEnd) {
                    for (secondIndex in firstIndex + 1 until cellEnd) {
                        action(sortedParticles[firstIndex]!!, sortedParticles[secondIndex]!!)
                    }
                }
                for (offsetIndex in FORWARD_COLUMN_OFFSETS.indices) {
                    val neighborColumn = column + FORWARD_COLUMN_OFFSETS[offsetIndex]
                    val neighborRow = row + FORWARD_ROW_OFFSETS[offsetIndex]
                    if (neighborColumn !in 0 until columns || neighborRow !in 0 until rows) continue
                    val neighborCellIndex = neighborRow * columns + neighborColumn
                    for (firstIndex in cellStart until cellEnd) {
                        for (secondIndex in cellStarts[neighborCellIndex] until cellStarts[neighborCellIndex + 1]) {
                            action(sortedParticles[firstIndex]!!, sortedParticles[secondIndex]!!)
                        }
                    }
                }
            }
        }
        for (largeIndex in largeParticles.indices) {
            val largeParticle = largeParticles[largeIndex]
            for (smallIndex in 0 until smallParticleCount) {
                action(largeParticle, sortedParticles[smallIndex]!!)
            }
            for (otherLargeIndex in largeIndex + 1 until largeParticles.size) {
                action(largeParticle, largeParticles[otherLargeIndex])
            }
        }
    }

    fun forEachWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit) {
        val firstColumn = max(0, ((centerX - range - originX) / cellSize).toInt())
        val lastColumn = minOf(columns - 1, ((centerX + range - originX) / cellSize).toInt())
        val firstRow = max(0, ((centerY - range - originY) / cellSize).toInt())
        val lastRow = minOf(rows - 1, ((centerY + range - originY) / cellSize).toInt())
        val rangeSquared = range * range
        for (largeParticle in largeParticles) {
            val deltaX = largeParticle.transform.position.x - centerX
            val deltaY = largeParticle.transform.position.y - centerY
            if (deltaX * deltaX + deltaY * deltaY <= rangeSquared) action(largeParticle)
        }
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                val cellIndex = row * columns + column
                for (particleIndex in cellStarts[cellIndex] until cellStarts[cellIndex + 1]) {
                    val particle = sortedParticles[particleIndex]!!
                    val deltaX = particle.transform.position.x - centerX
                    val deltaY = particle.transform.position.y - centerY
                    if (deltaX * deltaX + deltaY * deltaY <= rangeSquared) action(particle)
                }
            }
        }
    }

    private fun cellIndexFor(x: Double, y: Double): Int {
        val column = ((x - originX) / cellSize).toInt()
        val row = ((y - originY) / cellSize).toInt()
        if (x < originX || y < originY || column !in 0 until columns || row !in 0 until rows) return -1
        return row * columns + column
    }
}
