package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.DiagnosticCounter
import com.github.adamyork.particles.engine.data.Particle
import kotlin.math.max

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal class SortedCellGrid(
    private val engineDiagnostics: EngineDiagnostics
) {

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
    private var sortedPositionX = DoubleArray(0)
    private var sortedPositionY = DoubleArray(0)
    private var sortedBoundingRadius = DoubleArray(0)
    private var smallParticleCount = 0
    private val largeParticles = ArrayList<Particle>()

    public fun build(
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
            sortedPositionX = DoubleArray(smallParticleCount)
            sortedPositionY = DoubleArray(smallParticleCount)
            sortedBoundingRadius = DoubleArray(smallParticleCount)
        }
        for (particleIndex in particles.indices) {
            val cellIndex = particleCellIndices[particleIndex]
            if (cellIndex >= 0) {
                val particle = particles[particleIndex]
                val slot = cellCursors[cellIndex]++
                sortedParticles[slot] = particle
                sortedPositionX[slot] = particle.transform.position.x
                sortedPositionY[slot] = particle.transform.position.y
                sortedBoundingRadius[slot] = particle.boundingRadius()
            }
        }
    }

    public fun forEachNearbyPair(action: (Particle, Particle) -> Unit) {
        var testedPairCount = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cellIndex = row * columns + column
                val cellStart = cellStarts[cellIndex]
                val cellEnd = cellStarts[cellIndex + 1]
                for (firstIndex in cellStart until cellEnd) {
                    for (secondIndex in firstIndex + 1 until cellEnd) {
                        testedPairCount++
                        if (smallBoundsOverlap(firstIndex, secondIndex)) {
                            action(sortedParticles[firstIndex]!!, sortedParticles[secondIndex]!!)
                        }
                    }
                }
                for (offsetIndex in FORWARD_COLUMN_OFFSETS.indices) {
                    val neighborColumn = column + FORWARD_COLUMN_OFFSETS[offsetIndex]
                    val neighborRow = row + FORWARD_ROW_OFFSETS[offsetIndex]
                    if (neighborColumn !in 0 until columns || neighborRow !in 0 until rows) continue
                    val neighborCellIndex = neighborRow * columns + neighborColumn
                    for (firstIndex in cellStart until cellEnd) {
                        for (secondIndex in cellStarts[neighborCellIndex] until cellStarts[neighborCellIndex + 1]) {
                            testedPairCount++
                            if (smallBoundsOverlap(firstIndex, secondIndex)) {
                                action(sortedParticles[firstIndex]!!, sortedParticles[secondIndex]!!)
                            }
                        }
                    }
                }
            }
        }
        for (largeIndex in largeParticles.indices) {
            val largeParticle = largeParticles[largeIndex]
            val largePositionX = largeParticle.transform.position.x
            val largePositionY = largeParticle.transform.position.y
            val largeRadius = largeParticle.boundingRadius()
            val reach = largeRadius + cellSize * 0.5
            val firstColumn = firstCellWithin(largePositionX, reach, originX)
            val lastColumn = lastCellWithin(largePositionX, reach, originX, columns)
            val firstRow = firstCellWithin(largePositionY, reach, originY)
            val lastRow = lastCellWithin(largePositionY, reach, originY, rows)
            for (row in firstRow..lastRow) {
                for (column in firstColumn..lastColumn) {
                    val cellIndex = row * columns + column
                    for (smallIndex in cellStarts[cellIndex] until cellStarts[cellIndex + 1]) {
                        testedPairCount++
                        val deltaX = sortedPositionX[smallIndex] - largePositionX
                        val deltaY = sortedPositionY[smallIndex] - largePositionY
                        val combinedRadius = sortedBoundingRadius[smallIndex] + largeRadius
                        if (deltaX * deltaX + deltaY * deltaY < combinedRadius * combinedRadius) {
                            action(largeParticle, sortedParticles[smallIndex]!!)
                        }
                    }
                }
            }
            for (otherLargeIndex in largeIndex + 1 until largeParticles.size) {
                testedPairCount++
                val otherLargeParticle = largeParticles[otherLargeIndex]
                val deltaX = otherLargeParticle.transform.position.x - largePositionX
                val deltaY = otherLargeParticle.transform.position.y - largePositionY
                val combinedRadius = otherLargeParticle.boundingRadius() + largeRadius
                if (deltaX * deltaX + deltaY * deltaY < combinedRadius * combinedRadius) {
                    action(largeParticle, otherLargeParticle)
                }
            }
        }
        engineDiagnostics.count(DiagnosticCounter.LARGE_PARTICLES, largeParticles.size)
        engineDiagnostics.count(DiagnosticCounter.SMALL_PARTICLES, smallParticleCount)
        engineDiagnostics.count(DiagnosticCounter.GRID_TESTED_PAIRS, testedPairCount)
    }

    private fun firstCellWithin(center: Double, reach: Double, origin: Double): Int =
        max(0, ((center - reach - origin) / cellSize).toInt())

    private fun lastCellWithin(center: Double, reach: Double, origin: Double, cellCount: Int): Int =
        minOf(cellCount - 1, ((center + reach - origin) / cellSize).toInt())

    private fun smallBoundsOverlap(firstIndex: Int, secondIndex: Int): Boolean {
        val deltaX = sortedPositionX[secondIndex] - sortedPositionX[firstIndex]
        val deltaY = sortedPositionY[secondIndex] - sortedPositionY[firstIndex]
        val combinedRadius = sortedBoundingRadius[firstIndex] + sortedBoundingRadius[secondIndex]
        return deltaX * deltaX + deltaY * deltaY < combinedRadius * combinedRadius
    }

    public fun forEachWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit) {
        val firstColumn = firstCellWithin(centerX, range, originX)
        val lastColumn = lastCellWithin(centerX, range, originX, columns)
        val firstRow = firstCellWithin(centerY, range, originY)
        val lastRow = lastCellWithin(centerY, range, originY, rows)
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
