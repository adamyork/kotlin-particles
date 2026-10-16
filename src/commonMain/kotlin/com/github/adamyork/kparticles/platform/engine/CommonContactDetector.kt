package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.CircleDimensions
import com.github.adamyork.kparticles.platform.engine.data.CollisionContact
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.data.RectDimensions
import me.tatarka.inject.annotations.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CommonContactDetector : ContactDetector {

    private companion object {
        private const val CORNER_COUNT: Int = 4
        private const val CONTAINMENT_TOLERANCE: Double = 0.5
        private const val MANIFOLD_TANGENT_EPSILON: Double = 0.001
        private const val MAXIMUM_CANDIDATES: Int = 8
    }

    private val candidateX = DoubleArray(MAXIMUM_CANDIDATES)
    private val candidateY = DoubleArray(MAXIMUM_CANDIDATES)
    private var candidateCount = 0
    private var supportX = 0.0
    private var supportY = 0.0

    override fun detect(firstParticle: Particle, secondParticle: Particle, contact: CollisionContact): Boolean {
        val firstDimensions = firstParticle.dimensions
        val secondDimensions = secondParticle.dimensions
        if (firstDimensions is CircleDimensions && secondDimensions is CircleDimensions) {
            return detectCircleCircle(firstParticle, secondParticle, contact)
        }
        if (firstDimensions is CircleDimensions && secondDimensions is RectDimensions) {
            return detectCircleBox(firstParticle, secondParticle, secondDimensions, false, contact)
        }
        if (firstDimensions is RectDimensions && secondDimensions is CircleDimensions) {
            return detectCircleBox(secondParticle, firstParticle, firstDimensions, true, contact)
        }
        return detectBoxBox(firstParticle, secondParticle, contact)
    }

    private fun detectCircleCircle(firstParticle: Particle, secondParticle: Particle, contact: CollisionContact): Boolean {
        val deltaX = secondParticle.transform.position.x - firstParticle.transform.position.x
        val deltaY = secondParticle.transform.position.y - firstParticle.transform.position.y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
        if (distance <= 0.0) return false
        val firstRadius = firstParticle.collisionRadius()
        val minimumDistance = firstRadius + secondParticle.collisionRadius()
        if (distance >= minimumDistance) return false
        val overlapDistance = minimumDistance - distance
        contact.normalX = deltaX / distance
        contact.normalY = deltaY / distance
        contact.depth = overlapDistance
        contact.pointCount = 1
        contact.pointX[0] = firstParticle.transform.position.x + contact.normalX * (firstRadius - overlapDistance / 2.0)
        contact.pointY[0] = firstParticle.transform.position.y + contact.normalY * (firstRadius - overlapDistance / 2.0)
        return true
    }

    private fun detectCircleBox(
        circleParticle: Particle,
        boxParticle: Particle,
        boxDimensions: RectDimensions,
        isBoxFirst: Boolean,
        contact: CollisionContact
    ): Boolean {
        val radius = circleParticle.collisionRadius()
        val halfWidth = boxDimensions.width / 2.0
        val halfHeight = boxDimensions.height / 2.0
        val cosine = cos(boxParticle.transform.angle)
        val sine = sin(boxParticle.transform.angle)
        val offsetX = circleParticle.transform.position.x - boxParticle.transform.position.x
        val offsetY = circleParticle.transform.position.y - boxParticle.transform.position.y
        val localX = offsetX * cosine + offsetY * sine
        val localY = -offsetX * sine + offsetY * cosine
        val closestX = localX.coerceIn(-halfWidth, halfWidth)
        val closestY = localY.coerceIn(-halfHeight, halfHeight)
        val deltaX = localX - closestX
        val deltaY = localY - closestY
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        val localNormalX: Double
        val localNormalY: Double
        var localPointX = closestX
        var localPointY = closestY
        val depth: Double
        if (distanceSquared > 0.0) {
            val distance = sqrt(distanceSquared)
            if (distance >= radius) return false
            localNormalX = deltaX / distance
            localNormalY = deltaY / distance
            depth = radius - distance
        } else {
            val penetrationX = halfWidth - abs(localX)
            val penetrationY = halfHeight - abs(localY)
            if (penetrationX < penetrationY) {
                localNormalX = if (localX >= 0.0) 1.0 else -1.0
                localNormalY = 0.0
                localPointX = localNormalX * halfWidth
                depth = radius + penetrationX
            } else {
                localNormalX = 0.0
                localNormalY = if (localY >= 0.0) 1.0 else -1.0
                localPointY = localNormalY * halfHeight
                depth = radius + penetrationY
            }
        }
        val boxToCircleX = localNormalX * cosine - localNormalY * sine
        val boxToCircleY = localNormalX * sine + localNormalY * cosine
        val directionSign = if (isBoxFirst) 1.0 else -1.0
        contact.normalX = boxToCircleX * directionSign
        contact.normalY = boxToCircleY * directionSign
        contact.depth = depth
        contact.pointCount = 1
        contact.pointX[0] = boxParticle.transform.position.x + localPointX * cosine - localPointY * sine
        contact.pointY[0] = boxParticle.transform.position.y + localPointX * sine + localPointY * cosine
        return true
    }

    private fun detectBoxBox(firstParticle: Particle, secondParticle: Particle, contact: CollisionContact): Boolean {
        val firstDimensions = firstParticle.dimensions as RectDimensions
        val secondDimensions = secondParticle.dimensions as RectDimensions
        val firstHalfWidth = firstDimensions.width / 2.0
        val firstHalfHeight = firstDimensions.height / 2.0
        val secondHalfWidth = secondDimensions.width / 2.0
        val secondHalfHeight = secondDimensions.height / 2.0
        val firstCosine = cos(firstParticle.transform.angle)
        val firstSine = sin(firstParticle.transform.angle)
        val secondCosine = cos(secondParticle.transform.angle)
        val secondSine = sin(secondParticle.transform.angle)
        val deltaX = secondParticle.transform.position.x - firstParticle.transform.position.x
        val deltaY = secondParticle.transform.position.y - firstParticle.transform.position.y
        var minimumOverlap = Double.MAX_VALUE
        var normalX = 0.0
        var normalY = 0.0
        for (axisIndex in 0 until CORNER_COUNT) {
            val axisX = when (axisIndex) {
                0 -> firstCosine
                1 -> -firstSine
                2 -> secondCosine
                else -> -secondSine
            }
            val axisY = when (axisIndex) {
                0 -> firstSine
                1 -> firstCosine
                2 -> secondSine
                else -> secondCosine
            }
            val firstProjection = firstHalfWidth * abs(axisX * firstCosine + axisY * firstSine) +
                    firstHalfHeight * abs(-axisX * firstSine + axisY * firstCosine)
            val secondProjection = secondHalfWidth * abs(axisX * secondCosine + axisY * secondSine) +
                    secondHalfHeight * abs(-axisX * secondSine + axisY * secondCosine)
            val separation = deltaX * axisX + deltaY * axisY
            val overlap = firstProjection + secondProjection - abs(separation)
            if (overlap <= 0.0) return false
            if (overlap < minimumOverlap) {
                minimumOverlap = overlap
                normalX = if (separation >= 0.0) axisX else -axisX
                normalY = if (separation >= 0.0) axisY else -axisY
            }
        }
        contact.normalX = normalX
        contact.normalY = normalY
        contact.depth = minimumOverlap
        candidateCount = 0
        collectContainedCorners(firstParticle, firstDimensions, secondParticle, secondDimensions)
        collectContainedCorners(secondParticle, secondDimensions, firstParticle, firstDimensions)
        if (candidateCount == 0) {
            writeSupportMidpoint(firstParticle, firstDimensions, secondParticle, secondDimensions, contact)
        } else {
            writeManifold(contact)
        }
        return true
    }

    private fun collectContainedCorners(
        cornerParticle: Particle,
        cornerDimensions: RectDimensions,
        containerParticle: Particle,
        containerDimensions: RectDimensions
    ) {
        val cornerCosine = cos(cornerParticle.transform.angle)
        val cornerSine = sin(cornerParticle.transform.angle)
        val containerCosine = cos(containerParticle.transform.angle)
        val containerSine = sin(containerParticle.transform.angle)
        val cornerHalfWidth = cornerDimensions.width / 2.0
        val cornerHalfHeight = cornerDimensions.height / 2.0
        val containerHalfWidth = containerDimensions.width / 2.0 + CONTAINMENT_TOLERANCE
        val containerHalfHeight = containerDimensions.height / 2.0 + CONTAINMENT_TOLERANCE
        for (cornerIndex in 0 until CORNER_COUNT) {
            if (candidateCount >= MAXIMUM_CANDIDATES) return
            val signX = if ((cornerIndex and 1) == 0) -1.0 else 1.0
            val signY = if ((cornerIndex and 2) == 0) -1.0 else 1.0
            val cornerOffsetX = signX * cornerHalfWidth * cornerCosine - signY * cornerHalfHeight * cornerSine
            val cornerOffsetY = signX * cornerHalfWidth * cornerSine + signY * cornerHalfHeight * cornerCosine
            val worldX = cornerParticle.transform.position.x + cornerOffsetX
            val worldY = cornerParticle.transform.position.y + cornerOffsetY
            val offsetX = worldX - containerParticle.transform.position.x
            val offsetY = worldY - containerParticle.transform.position.y
            val localX = offsetX * containerCosine + offsetY * containerSine
            val localY = -offsetX * containerSine + offsetY * containerCosine
            if (abs(localX) <= containerHalfWidth && abs(localY) <= containerHalfHeight) {
                candidateX[candidateCount] = worldX
                candidateY[candidateCount] = worldY
                candidateCount++
            }
        }
    }

    private fun writeManifold(contact: CollisionContact) {
        val tangentX = -contact.normalY
        val tangentY = contact.normalX
        var minimumIndex = 0
        var maximumIndex = 0
        var minimumTangent = Double.MAX_VALUE
        var maximumTangent = -Double.MAX_VALUE
        var sumX = 0.0
        var sumY = 0.0
        for (candidateIndex in 0 until candidateCount) {
            val tangentCoordinate = candidateX[candidateIndex] * tangentX + candidateY[candidateIndex] * tangentY
            if (tangentCoordinate < minimumTangent) {
                minimumTangent = tangentCoordinate
                minimumIndex = candidateIndex
            }
            if (tangentCoordinate > maximumTangent) {
                maximumTangent = tangentCoordinate
                maximumIndex = candidateIndex
            }
            sumX += candidateX[candidateIndex]
            sumY += candidateY[candidateIndex]
        }
        if (maximumTangent - minimumTangent < MANIFOLD_TANGENT_EPSILON) {
            contact.pointCount = 1
            contact.pointX[0] = sumX / candidateCount
            contact.pointY[0] = sumY / candidateCount
            return
        }
        contact.pointCount = 2
        contact.pointX[0] = candidateX[minimumIndex]
        contact.pointY[0] = candidateY[minimumIndex]
        contact.pointX[1] = candidateX[maximumIndex]
        contact.pointY[1] = candidateY[maximumIndex]
    }

    private fun writeSupportMidpoint(
        firstParticle: Particle,
        firstDimensions: RectDimensions,
        secondParticle: Particle,
        secondDimensions: RectDimensions,
        contact: CollisionContact
    ) {
        writeSupportPoint(firstParticle, firstDimensions, contact.normalX, contact.normalY)
        val firstPointX = firstParticle.transform.position.x + supportX
        val firstPointY = firstParticle.transform.position.y + supportY
        writeSupportPoint(secondParticle, secondDimensions, -contact.normalX, -contact.normalY)
        contact.pointCount = 1
        contact.pointX[0] = (firstPointX + secondParticle.transform.position.x + supportX) / 2.0
        contact.pointY[0] = (firstPointY + secondParticle.transform.position.y + supportY) / 2.0
    }

    private fun writeSupportPoint(particle: Particle, dimensions: RectDimensions, directionX: Double, directionY: Double) {
        val cosine = cos(particle.transform.angle)
        val sine = sin(particle.transform.angle)
        val localDirectionX = directionX * cosine + directionY * sine
        val localDirectionY = -directionX * sine + directionY * cosine
        val localX = if (localDirectionX >= 0.0) dimensions.width / 2.0 else -dimensions.width / 2.0
        val localY = if (localDirectionY >= 0.0) dimensions.height / 2.0 else -dimensions.height / 2.0
        supportX = localX * cosine - localY * sine
        supportY = localX * sine + localY * cosine
    }
}
