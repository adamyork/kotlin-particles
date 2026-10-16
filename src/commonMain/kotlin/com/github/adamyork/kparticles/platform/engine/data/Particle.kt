package com.github.adamyork.kparticles.platform.engine.data

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sign

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class Particle(
    val id: Long,
    val lifecycle: ParticleLifecycle,
    val appearance: ParticleAppearance,
    val dimensions: ParticleDimensions,
    val transform: ParticleTransform,
    val material: ParticleMaterial,
    val dynamics: ParticleDynamics = ParticleDynamics(),
    val animation: ParticleAnimation? = null
) {
    companion object {
        private const val VISIBILITY_BUFFER = 50
        const val MINIMUM_MASS: Double = 0.000000001
        const val BOUNDED_Z: Double = 1.0
        const val MAXIMUM_ANGULAR_VELOCITY: Double = 0.5
        private const val MAXIMUM_GRAVITY_FRACTION: Double = 0.999

        fun isolatedPlaneZ(id: Long, planeThickness: Double): Double = -(id + 1).toDouble() * planeThickness * 2.0

        fun contactPlaneZ(id: Long, contactThickness: Double, separationThickness: Double): Double =
            -contactPlaneOffset(id, contactThickness, separationThickness)

        fun wallContactPlaneZ(id: Long, contactThickness: Double, separationThickness: Double): Double =
            contactPlaneOffset(id, contactThickness, separationThickness)

        private fun contactPlaneOffset(id: Long, contactThickness: Double, separationThickness: Double): Double {
            val planeSpacing = separationThickness * 2.0
            val planeCount = max(1, (contactThickness / planeSpacing).toInt() - 1)
            return ((id % planeCount) + 1).toDouble() * planeSpacing
        }

        fun densityFor(mass: Double, dimensions: ParticleDimensions): Double {
            val area = dimensions.area()
            return if (area > 0.0) mass / area else 0.0
        }

        fun netGravityOf(density: Double, gravity: Double, mediumDensity: Double): Double =
            gravity * (1.0 - mediumDensity / max(density, MINIMUM_MASS))

        fun densityForNetGravity(netGravity: Double, gravity: Double, mediumDensity: Double): Double {
            if (gravity <= 0.0) return mediumDensity
            val gravityFraction = min(netGravity / gravity, MAXIMUM_GRAVITY_FRACTION)
            return mediumDensity / (1.0 - gravityFraction)
        }
    }

    fun cullingCheck(viewPort: ViewPort): Boolean {
        val localCord = viewPort.globalToLocal(transform.position.x.toInt(), transform.position.y.toInt())
        return localCord.y < viewPort.height &&
                localCord.y > -VISIBILITY_BUFFER &&
                localCord.x > -VISIBILITY_BUFFER &&
                localCord.x < viewPort.width + VISIBILITY_BUFFER
    }

    fun mass(): Double = material.density * dimensions.area()

    fun inertialMass(): Double = max(mass(), MINIMUM_MASS)

    fun collisionRadius(): Double = dimensions.collisionRadius()

    fun boundingRadius(): Double = dimensions.boundingRadius()

    fun axisAlignedHalfWidth(): Double = when (val currentDimensions = dimensions) {
        is RectDimensions -> (abs(cos(transform.angle)) * currentDimensions.width +
                abs(sin(transform.angle)) * currentDimensions.height) / 2.0
        is CircleDimensions -> currentDimensions.radius
    }

    fun axisAlignedHalfHeight(): Double = when (val currentDimensions = dimensions) {
        is RectDimensions -> (abs(sin(transform.angle)) * currentDimensions.width +
                abs(cos(transform.angle)) * currentDimensions.height) / 2.0
        is CircleDimensions -> currentDimensions.radius
    }

    fun angularVelocityAfterRolling(normalImpulse: Double, contactArm: Double): Double {
        val decay = material.rollingResistance * normalImpulse * contactArm / momentOfInertia()
        return if (abs(dynamics.angularVelocity) <= decay) 0.0 else dynamics.angularVelocity - sign(dynamics.angularVelocity) * decay
    }

    fun momentOfInertia(): Double {
        val shapeFactor = when (val currentDimensions = dimensions) {
            is RectDimensions -> (currentDimensions.width * currentDimensions.width +
                    currentDimensions.height * currentDimensions.height) / 12.0
            is CircleDimensions -> currentDimensions.radius * currentDimensions.radius / 2.0
        }
        return max(inertialMass() * shapeFactor, MINIMUM_MASS)
    }
}

