package com.github.adamyork.particles.engine.data

import com.github.adamyork.particles.common.data.ParticleViewPort
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
public data class Particle(
    val id: Long,
    val lifecycle: ParticleLifecycle,
    val appearance: ParticleAppearance,
    val dimensions: ParticleDimensions,
    val transform: ParticleTransform,
    val material: ParticleMaterial,
    val dynamics: ParticleDynamics = ParticleDynamics(),
    val animation: ParticleAnimation? = null
) {
    public companion object {
        private const val CULLING_EXTENT_SCALE: Double = 1.25
        private const val CULLING_MARGIN: Double = 2.0
        private const val MINIMUM_VISIBLE_ALPHA: Double = 1.0 / 255.0
        public const val MINIMUM_MASS: Double = 0.000000001
        public const val BOUNDED_Z: Double = 1.0
        public const val MAXIMUM_ANGULAR_VELOCITY: Double = 0.5
        private const val MAXIMUM_GRAVITY_FRACTION: Double = 0.999

        public fun isolatedPlaneZ(id: Long, planeThickness: Double): Double = -(id + 1).toDouble() * planeThickness * 2.0

        public fun contactPlaneZ(id: Long, contactThickness: Double, separationThickness: Double): Double =
            -contactPlaneOffset(id, contactThickness, separationThickness)

        public fun wallContactPlaneZ(id: Long, contactThickness: Double, separationThickness: Double): Double =
            contactPlaneOffset(id, contactThickness, separationThickness)

        private fun contactPlaneOffset(id: Long, contactThickness: Double, separationThickness: Double): Double {
            val planeSpacing = separationThickness * 2.0
            val planeCount = max(1, (contactThickness / planeSpacing).toInt() - 1)
            return ((id % planeCount) + 1).toDouble() * planeSpacing
        }

        public fun densityFor(mass: Double, dimensions: ParticleDimensions): Double {
            val area = dimensions.area()
            return if (area > 0.0) mass / area else 0.0
        }

        public fun netGravityOf(density: Double, gravity: Double, mediumDensity: Double): Double =
            gravity * (1.0 - mediumDensity / max(density, MINIMUM_MASS))

        public fun densityForTerminalSpeed(
            terminalSpeed: Double,
            gravity: Double,
            mediumDensity: Double,
            dragCoefficient: Double,
            dimensions: ParticleDimensions
        ): Double {
            val area = dimensions.area()
            if (gravity <= 0.0 || dragCoefficient <= 0.0 || area <= 0.0) return mediumDensity
            val frontalLength = (dimensions.boundingWidth() + dimensions.boundingHeight()) / 2.0
            return mediumDensity *
                (1.0 + terminalSpeed * terminalSpeed * dragCoefficient * frontalLength / (2.0 * gravity * area))
        }

        public fun densityForNetGravity(netGravity: Double, gravity: Double, mediumDensity: Double): Double {
            if (gravity <= 0.0) return mediumDensity
            val gravityFraction = min(netGravity / gravity, MAXIMUM_GRAVITY_FRACTION)
            return mediumDensity / (1.0 - gravityFraction)
        }
    }

    public fun cullingCheck(viewPort: ParticleViewPort): Boolean {
        if (!lifecycle.isVisible() || lifecycle.isExpired()) return false
        if (appearance.alpha < MINIMUM_VISIBLE_ALPHA) return false
        val extent = boundingRadius() * CULLING_EXTENT_SCALE + CULLING_MARGIN
        val localX = transform.position.x - viewPort.x
        val localY = transform.position.y - viewPort.y
        return localX + extent > 0.0 &&
                localX - extent < viewPort.width &&
                localY + extent > 0.0 &&
                localY - extent < viewPort.height
    }

    public fun mass(): Double = material.density * dimensions.area()

    public fun inertialMass(): Double = max(mass(), MINIMUM_MASS)

    public fun gravitationalMass(): Double = if (material.exertsGravity) mass() else 0.0

    public fun frontalLength(): Double = (dimensions.boundingWidth() + dimensions.boundingHeight()) / 2.0

    public fun collisionRadius(): Double = dimensions.collisionRadius()

    public fun boundingRadius(): Double = dimensions.boundingRadius()

    public fun axisAlignedHalfWidth(): Double = when (val currentDimensions = dimensions) {
        is ParticleRectDimensions -> (abs(cos(transform.angle)) * currentDimensions.width +
                abs(sin(transform.angle)) * currentDimensions.height) / 2.0
        is CircleDimensions -> currentDimensions.radius
    }

    public fun axisAlignedHalfHeight(): Double = when (val currentDimensions = dimensions) {
        is ParticleRectDimensions -> (abs(sin(transform.angle)) * currentDimensions.width +
                abs(cos(transform.angle)) * currentDimensions.height) / 2.0
        is CircleDimensions -> currentDimensions.radius
    }

    public fun angularVelocityAfterRolling(normalImpulse: Double, contactArm: Double): Double {
        val decay = material.rollingResistance * normalImpulse * contactArm / momentOfInertia()
        return if (abs(dynamics.angularVelocity) <= decay) 0.0 else dynamics.angularVelocity - sign(dynamics.angularVelocity) * decay
    }

    public fun momentOfInertia(): Double {
        val shapeFactor = when (val currentDimensions = dimensions) {
            is ParticleRectDimensions -> (currentDimensions.width * currentDimensions.width +
                    currentDimensions.height * currentDimensions.height) / 12.0
            is CircleDimensions -> currentDimensions.radius * currentDimensions.radius / 2.0
        }
        return max(inertialMass() * shapeFactor, MINIMUM_MASS)
    }
}

