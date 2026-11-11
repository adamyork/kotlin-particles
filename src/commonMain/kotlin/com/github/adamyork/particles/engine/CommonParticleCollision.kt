package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonParticleCollision(
    private val particlePhysics: ParticlePhysics,
    private val spatialGrid: SpatialGrid,
    private val physicsSettingsService: ParticlePhysicsSettingsService
) : ParticleCollision {

    private companion object {
        private const val WALL_CORNER_COUNT: Int = 4
        private const val WALL_CONTACT_TOLERANCE: Double = 0.5
        private const val POSITION_CORRECTION_ITERATIONS: Int = 2
        private const val CORNER_COUNT: Int = 4
        private const val CONTAINMENT_TOLERANCE: Double = 0.5
        private const val MANIFOLD_TANGENT_EPSILON: Double = 0.001
        private const val MAXIMUM_CANDIDATES: Int = 8
        private const val SOLVER_ITERATIONS: Int = 6
    }

    private val touchingFirstParticles = ArrayList<Particle>()

    private val touchingSecondParticles = ArrayList<Particle>()

    private val contact = CollisionContact()

    private val wallContact = CollisionContact()

    private val wallCornerOffsetX = DoubleArray(WALL_CORNER_COUNT)

    private val wallCornerOffsetY = DoubleArray(WALL_CORNER_COUNT)

    private val candidateX = DoubleArray(MAXIMUM_CANDIDATES)
    private val candidateY = DoubleArray(MAXIMUM_CANDIDATES)
    private var candidateCount = 0
    private var supportX = 0.0
    private var supportY = 0.0

    private val normalMasses = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val tangentMasses = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val normalImpulses = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val tangentImpulses = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val restitutionTargets = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val firstArmX = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val firstArmY = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val secondArmX = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private val secondArmY = DoubleArray(CollisionContact.MAXIMUM_POINTS)
    private var firstInverseMass = 0.0
    private var firstInverseInertia = 0.0
    private var secondInverseMass = 0.0
    private var secondInverseInertia = 0.0

    override fun applyParticleCollision(generators: List<ParticleEffectGenerator>, viewPort: ParticleViewPort, deltaTime: Double) {
        val minActiveVelocitySquared =
            physicsSettingsService.minActiveVelocity * physicsSettingsService.minActiveVelocity
        val fragments = ArrayList<Pair<Particle, Particle>>()
        val planeThickness = physicsSettingsService.collisionPlaneThickness
        placeParticles(generators, viewPort)
        particlePhysics.applyGravitation(this, deltaTime)
        touchingFirstParticles.clear()
        touchingSecondParticles.clear()
        spatialGrid.forEachCollisionPair { firstParticle, secondParticle ->
            if (abs(firstParticle.transform.position.z - secondParticle.transform.position.z) >= planeThickness) {
                return@forEachCollisionPair
            }
            if (!detectContact(firstParticle, secondParticle, contact)) return@forEachCollisionPair
            touchingFirstParticles.add(firstParticle)
            touchingSecondParticles.add(secondParticle)
            if (particlePhysics.applyParticleAccretion(firstParticle, secondParticle)) return@forEachCollisionPair
            if (isResting(firstParticle, minActiveVelocitySquared) && isResting(
                    secondParticle,
                    minActiveVelocitySquared
                )
            ) {
                return@forEachCollisionPair
            }
            val isHardnessMismatch = firstParticle.material.hardness != secondParticle.material.hardness
            val harderParticle =
                if (firstParticle.material.hardness > secondParticle.material.hardness) firstParticle else secondParticle
            val weakerParticle = if (harderParticle === firstParticle) secondParticle else firstParticle
            val impactSpecificEnergy =
                if (isHardnessMismatch) impactSpecificEnergy(firstParticle, secondParticle, harderParticle, weakerParticle) else 0.0
            applyParticleCollisionPhysics(firstParticle, secondParticle, contact)
            if (isHardnessMismatch) {
                splitParticle(
                    weakerParticle,
                    fragmentCountFor(impactSpecificEnergy, weakerParticle),
                    impactSpecificEnergy,
                    contact.normalX,
                    contact.normalY,
                    fragments
                )
            }
        }
        repeat(POSITION_CORRECTION_ITERATIONS) {
            for (touchingIndex in touchingFirstParticles.indices) {
                correctPositions(touchingFirstParticles[touchingIndex], touchingSecondParticles[touchingIndex], planeThickness)
            }
        }
        for ((parentParticle, fragmentParticle) in fragments) {
            generators.firstOrNull { generator -> generator.particles.any { it === parentParticle } }
                ?.adopt(fragmentParticle)
        }
        for (generator in generators) {
            applyBoundaryCollision(generator.particles, viewPort)
        }
    }

    private fun correctPositions(firstParticle: Particle, secondParticle: Particle, planeThickness: Double) {
        if (firstParticle.lifecycle.isExpired() || secondParticle.lifecycle.isExpired()) return
        if (abs(firstParticle.transform.position.z - secondParticle.transform.position.z) >= planeThickness) return
        if (!detectContact(firstParticle, secondParticle, contact)) return
        val correction = max(contact.depth - physicsSettingsService.positionCorrectionSlop, 0.0) *
                physicsSettingsService.positionCorrectionFactor
        if (correction <= 0.0) return
        val firstParticleMass = firstParticle.inertialMass()
        val secondParticleMass = secondParticle.inertialMass()
        val totalMass = firstParticleMass + secondParticleMass
        firstParticle.transform.position.x -= contact.normalX * correction * (secondParticleMass / totalMass)
        firstParticle.transform.position.y -= contact.normalY * correction * (secondParticleMass / totalMass)
        secondParticle.transform.position.x += contact.normalX * correction * (firstParticleMass / totalMass)
        secondParticle.transform.position.y += contact.normalY * correction * (firstParticleMass / totalMass)
    }

    override fun getAllPlacedParticles(): List<Particle> = spatialGrid.getAllPlacedParticles()

    override fun forEachParticleWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit) {
        spatialGrid.forEachParticleWithin(centerX, centerY, range, action)
    }

    private fun placeParticles(generators: List<ParticleEffectGenerator>, viewPort: ParticleViewPort) {
        spatialGrid.beginTick(viewPort)
        for (generator in generators) {
            for (particle in generator.particles) {
                if (particle.lifecycle.isVisible() && !particle.lifecycle.isExpired()) spatialGrid.place(particle)
            }
        }
    }

    private fun applyParticleCollisionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        contact: CollisionContact
    ) {
        if (firstParticle.dimensions is CircleDimensions && secondParticle.dimensions is CircleDimensions) {
            applyCircleCollisionPhysics(firstParticle, secondParticle, contact.normalX, contact.normalY)
        } else {
            applyBoxCollisionPhysics(firstParticle, secondParticle, contact)
        }
    }

    private fun applyBoxCollisionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        contact: CollisionContact
    ) {
        val restitution = min(firstParticle.material.restitution, secondParticle.material.restitution)
        val friction = physicsSettingsService.friction +
                sqrt(firstParticle.material.friction * secondParticle.material.friction)
        val staticFriction = max(
            friction,
            physicsSettingsService.friction +
                    sqrt(firstParticle.material.staticFriction * secondParticle.material.staticFriction)
        )
        solveContact(
            firstParticle,
            secondParticle,
            contact,
            restitution,
            friction,
            staticFriction,
            physicsSettingsService.minActiveVelocity
        )
    }

    private fun applyCircleCollisionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        normalX: Double,
        normalY: Double
    ) {
        val firstParticleMass = firstParticle.inertialMass()
        val secondParticleMass = secondParticle.inertialMass()
        val totalMass = firstParticleMass + secondParticleMass
        val relativeVelocityX = firstParticle.dynamics.velocity.x - secondParticle.dynamics.velocity.x
        val relativeVelocityY = firstParticle.dynamics.velocity.y - secondParticle.dynamics.velocity.y
        val relativeNormalSpeed = normalX * relativeVelocityX + normalY * relativeVelocityY
        if (relativeNormalSpeed <= 0.0) return
        val impulse = relativeNormalSpeed / totalMass
        val restitution = min(firstParticle.material.restitution, secondParticle.material.restitution)
        firstParticle.dynamics.velocity.x -= impulse * secondParticleMass * normalX * (1 + restitution)
        firstParticle.dynamics.velocity.y -= impulse * secondParticleMass * normalY * (1 + restitution)
        secondParticle.dynamics.velocity.x += impulse * firstParticleMass * normalX * (1 + restitution)
        secondParticle.dynamics.velocity.y += impulse * firstParticleMass * normalY * (1 + restitution)
        val friction = physicsSettingsService.friction +
                sqrt(firstParticle.material.friction * secondParticle.material.friction)
        val staticFriction = max(
            friction,
            physicsSettingsService.friction +
                    sqrt(firstParticle.material.staticFriction * secondParticle.material.staticFriction)
        )
        if (friction <= 0.0) return
        val tangentX = -normalY
        val isSpinning = firstParticle.dynamics.angularVelocity != 0.0 || secondParticle.dynamics.angularVelocity != 0.0
        val firstSpinArm = if (isSpinning) firstParticle.collisionRadius() else 0.0
        val secondSpinArm = if (isSpinning) secondParticle.collisionRadius() else 0.0
        val firstInertia = firstParticle.momentOfInertia()
        val secondInertia = secondParticle.momentOfInertia()
        val slipSpeed = tangentX * relativeVelocityX + normalX * relativeVelocityY +
                firstParticle.dynamics.angularVelocity * firstSpinArm + secondParticle.dynamics.angularVelocity * secondSpinArm
        val effectiveInverseMass = 1.0 / firstParticleMass + 1.0 / secondParticleMass +
                firstSpinArm * firstSpinArm / firstInertia + secondSpinArm * secondSpinArm / secondInertia
        val reducedMass = firstParticleMass * secondParticleMass / totalMass
        val normalImpulseMagnitude = abs(reducedMass * relativeNormalSpeed * (1 + restitution))
        val requiredImpulse = slipSpeed / effectiveInverseMass
        val frictionImpulse = if (abs(requiredImpulse) <= staticFriction * normalImpulseMagnitude) {
            requiredImpulse
        } else {
            sign(requiredImpulse) * friction * normalImpulseMagnitude
        }
        firstParticle.dynamics.velocity.x -= frictionImpulse / firstParticleMass * tangentX
        firstParticle.dynamics.velocity.y -= frictionImpulse / firstParticleMass * normalX
        secondParticle.dynamics.velocity.x += frictionImpulse / secondParticleMass * tangentX
        secondParticle.dynamics.velocity.y += frictionImpulse / secondParticleMass * normalX
        firstParticle.dynamics.angularVelocity =
            (firstParticle.dynamics.angularVelocity - frictionImpulse * firstSpinArm / firstInertia)
                .coerceIn(-Particle.MAXIMUM_ANGULAR_VELOCITY, Particle.MAXIMUM_ANGULAR_VELOCITY)
        secondParticle.dynamics.angularVelocity =
            (secondParticle.dynamics.angularVelocity - frictionImpulse * secondSpinArm / secondInertia)
                .coerceIn(-Particle.MAXIMUM_ANGULAR_VELOCITY, Particle.MAXIMUM_ANGULAR_VELOCITY)
        firstParticle.dynamics.angularVelocity =
            firstParticle.angularVelocityAfterRolling(normalImpulseMagnitude, firstParticle.collisionRadius())
        secondParticle.dynamics.angularVelocity =
            secondParticle.angularVelocityAfterRolling(normalImpulseMagnitude, secondParticle.collisionRadius())
    }

    private fun applyBoundaryCollision(particles: List<Particle>, viewPort: ParticleViewPort) {
        val boundaryMinimumZ = physicsSettingsService.boundaryZ
        val boundaryMaximumZ = boundaryMinimumZ + physicsSettingsService.boundaryDepth
        val wallRestitution = physicsSettingsService.wallRestitution
        val environmentFriction = physicsSettingsService.friction
        val restitutionThreshold = physicsSettingsService.minActiveVelocity
        for (particle in particles) {
            if (!particle.lifecycle.isVisible() || particle.lifecycle.isExpired()) continue
            val position = particle.transform.position
            if (position.z <= boundaryMinimumZ || position.z > boundaryMaximumZ) continue
            val halfWidth = particle.axisAlignedHalfWidth()
            val halfHeight = particle.axisAlignedHalfHeight()
            val bounce = particle.material.restitution.coerceIn(0.0, 1.0) * wallRestitution
            val friction = environmentFriction + particle.material.friction
            val staticFriction = max(friction, environmentFriction + particle.material.staticFriction)
            val minimumX = viewPort.x + halfWidth
            val maximumX = viewPort.x + viewPort.width - halfWidth
            val minimumY = viewPort.y + halfHeight
            val maximumY = viewPort.y + viewPort.height - halfHeight
            val hitLeftWall = position.x < minimumX
            val hitRightWall = position.x > maximumX
            if (hitLeftWall || hitRightWall) {
                position.x = if (hitLeftWall) minimumX else maximumX
                applyWallContact(
                    particle,
                    if (hitLeftWall) -1.0 else 1.0,
                    0.0,
                    bounce,
                    friction,
                    staticFriction,
                    restitutionThreshold
                )
            }
            val hitTopWall = position.y < minimumY
            val hitBottomWall = position.y > maximumY
            if (hitTopWall || hitBottomWall) {
                position.y = if (hitTopWall) minimumY else maximumY
                applyWallContact(
                    particle,
                    0.0,
                    if (hitTopWall) -1.0 else 1.0,
                    bounce,
                    friction,
                    staticFriction,
                    restitutionThreshold
                )
            }
        }
    }

    private fun applyWallContact(
        particle: Particle,
        wallNormalX: Double,
        wallNormalY: Double,
        bounce: Double,
        friction: Double,
        staticFriction: Double,
        restitutionThreshold: Double
    ) {
        fillWallContact(particle, wallNormalX, wallNormalY)
        solveContact(particle, null, wallContact, bounce, friction, staticFriction, restitutionThreshold)
    }

    private fun fillWallContact(particle: Particle, wallNormalX: Double, wallNormalY: Double) {
        val position = particle.transform.position
        wallContact.normalX = wallNormalX
        wallContact.normalY = wallNormalY
        wallContact.depth = 0.0
        val dimensions = particle.dimensions
        if (dimensions !is ParticleRectDimensions) {
            val radius = particle.collisionRadius()
            wallContact.pointCount = 1
            wallContact.pointX[0] = position.x + wallNormalX * radius
            wallContact.pointY[0] = position.y + wallNormalY * radius
            return
        }
        val cosine = cos(particle.transform.angle)
        val sine = sin(particle.transform.angle)
        val halfWidth = dimensions.width / 2.0
        val halfHeight = dimensions.height / 2.0
        var maximumSupport = -Double.MAX_VALUE
        for (cornerIndex in 0 until WALL_CORNER_COUNT) {
            val signX = if ((cornerIndex and 1) == 0) -1.0 else 1.0
            val signY = if ((cornerIndex and 2) == 0) -1.0 else 1.0
            wallCornerOffsetX[cornerIndex] = signX * halfWidth * cosine - signY * halfHeight * sine
            wallCornerOffsetY[cornerIndex] = signX * halfWidth * sine + signY * halfHeight * cosine
            val support = wallCornerOffsetX[cornerIndex] * wallNormalX + wallCornerOffsetY[cornerIndex] * wallNormalY
            maximumSupport = max(maximumSupport, support)
        }
        wallContact.pointCount = 0
        for (cornerIndex in 0 until WALL_CORNER_COUNT) {
            val support = wallCornerOffsetX[cornerIndex] * wallNormalX + wallCornerOffsetY[cornerIndex] * wallNormalY
            if (support >= maximumSupport - WALL_CONTACT_TOLERANCE && wallContact.pointCount < CollisionContact.MAXIMUM_POINTS) {
                wallContact.pointX[wallContact.pointCount] = position.x + wallCornerOffsetX[cornerIndex]
                wallContact.pointY[wallContact.pointCount] = position.y + wallCornerOffsetY[cornerIndex]
                wallContact.pointCount++
            }
        }
    }

    private fun impactSpecificEnergy(
        firstParticle: Particle,
        secondParticle: Particle,
        harderParticle: Particle,
        weakerParticle: Particle
    ): Double {
        val closingSpeed = contact.normalX * (firstParticle.dynamics.velocity.x - secondParticle.dynamics.velocity.x) +
                contact.normalY * (firstParticle.dynamics.velocity.y - secondParticle.dynamics.velocity.y)
        if (closingSpeed <= 0.0) return 0.0
        val harderMass = harderParticle.inertialMass()
        val massShare = harderMass / (harderMass + weakerParticle.inertialMass())
        val hardnessGap = harderParticle.material.hardness - weakerParticle.material.hardness
        return 0.5 * massShare * closingSpeed * closingSpeed * hardnessGap
    }

    private fun toughnessOf(particle: Particle): Double =
        physicsSettingsService.shatterToughness * (1.0 + particle.material.hardness)

    private fun fragmentCountFor(impactSpecificEnergy: Double, weakerParticle: Particle): Int {
        return (impactSpecificEnergy / toughnessOf(weakerParticle)).toInt()
            .coerceAtMost(physicsSettingsService.shatterMaximumFragmentCount)
    }

    private fun splitParticle(
        particle: Particle,
        requestedFragmentCount: Int,
        impactSpecificEnergy: Double,
        normalX: Double,
        normalY: Double,
        fragments: ArrayList<Pair<Particle, Particle>>
    ) {
        val currentRadius = particle.collisionRadius()
        val fragmentCount =
            min(requestedFragmentCount, (currentRadius / physicsSettingsService.shatterMinimumFragmentRadius).toInt())
        if (fragmentCount < physicsSettingsService.shatterMinimumFragmentCount) return
        val fragmentDiameter = currentRadius / fragmentCount * 2.0
        val tangentX = -normalY
        val sourceX = particle.transform.position.x
        val sourceY = particle.transform.position.y
        val fractureEnergy = fragmentCount * toughnessOf(particle)
        val spreadSpeed = sqrt(
            6.0 * max(0.0, impactSpecificEnergy - fractureEnergy) / (fragmentCount * fragmentCount - 1.0)
        )
        val currentSpeedSquared = particle.dynamics.velocity.x * particle.dynamics.velocity.x +
                particle.dynamics.velocity.y * particle.dynamics.velocity.y
        val velocityRetention =
            if (currentSpeedSquared > 0.0) sqrt(max(0.0, 1.0 - 2.0 * fractureEnergy / currentSpeedSquared)) else 1.0
        val sourceXVelocity = particle.dynamics.velocity.x * velocityRetention
        val sourceYVelocity = particle.dynamics.velocity.y * velocityRetention
        val centerOffset = (fragmentCount - 1) / 2.0
        val fragmentMassRatio = 1.0 / (fragmentCount * fragmentCount)
        val fragmentDensity = particle.material.density
        val fragmentMass = particle.mass() * fragmentMassRatio
        val gravity = physicsSettingsService.gravity
        val mediumDensity = physicsSettingsService.mediumDensity
        val parentNetWeight = particle.mass() * Particle.netGravityOf(particle.material.density, gravity, mediumDensity)
        val holdFraction = if (parentNetWeight != 0.0) -particle.dynamics.thrust.y / parentNetWeight else 0.0
        for (fragmentIndex in 1 until fragmentCount) {
            val offset = fragmentIndex - centerOffset
            val fragmentDimensions = particle.dimensions.scaledBy(1.0 / fragmentCount)
            val fragmentParticle = particle.copy(
                id = particle.id,
                dimensions = fragmentDimensions,
                appearance = particle.appearance.copy(),
                animation = particle.animation?.copy(),
                transform = ParticleTransform(
                    position = ParticleVector(
                        sourceX + tangentX * fragmentDiameter * offset,
                        sourceY + normalX * fragmentDiameter * offset,
                        particle.transform.position.z
                    ),
                    origin = particle.transform.origin.copy(),
                    angle = particle.transform.angle
                ),
                lifecycle = particle.lifecycle.copy(),
                material = particle.material.copy(density = fragmentDensity),
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(
                        sourceXVelocity + tangentX * spreadSpeed * 2.0 * offset,
                        sourceYVelocity + normalX * spreadSpeed * 2.0 * offset,
                        particle.dynamics.velocity.z
                    ),
                    thrust = ParticleVector(
                        particle.dynamics.thrust.x * fragmentMassRatio,
                        -holdFraction * fragmentMass * Particle.netGravityOf(fragmentDensity, gravity, mediumDensity),
                        particle.dynamics.thrust.z * fragmentMassRatio
                    ),
                    angularVelocity = particle.dynamics.angularVelocity
                )
            )
            fragments.add(Pair(particle, fragmentParticle))
        }
        particle.dimensions.scaleBy(1.0 / fragmentCount)
        particle.transform.position.x = sourceX - tangentX * fragmentDiameter * centerOffset
        particle.transform.position.y = sourceY - normalX * fragmentDiameter * centerOffset
        particle.dynamics.velocity.x =
            sourceXVelocity - tangentX * spreadSpeed * 2.0 * centerOffset
        particle.dynamics.velocity.y =
            sourceYVelocity - normalX * spreadSpeed * 2.0 * centerOffset
        particle.dynamics.thrust.x *= fragmentMassRatio
        particle.dynamics.thrust.y =
            -holdFraction * fragmentMass * Particle.netGravityOf(particle.material.density, gravity, mediumDensity)
        particle.dynamics.thrust.z *= fragmentMassRatio
    }

    private fun isResting(particle: Particle, minActiveVelocitySquared: Double): Boolean {
        val angularSurfaceSpeed = particle.dynamics.angularVelocity * particle.collisionRadius()
        val speedSquared = particle.dynamics.velocity.x * particle.dynamics.velocity.x +
                particle.dynamics.velocity.y * particle.dynamics.velocity.y +
                angularSurfaceSpeed * angularSurfaceSpeed
        return speedSquared < minActiveVelocitySquared
    }

    private fun detectContact(firstParticle: Particle, secondParticle: Particle, contact: CollisionContact): Boolean {
        val firstDimensions = firstParticle.dimensions
        val secondDimensions = secondParticle.dimensions
        if (firstDimensions is CircleDimensions && secondDimensions is CircleDimensions) {
            return detectCircleCircle(firstParticle, secondParticle, contact)
        }
        if (firstDimensions is CircleDimensions && secondDimensions is ParticleRectDimensions) {
            return detectCircleBox(firstParticle, secondParticle, secondDimensions, false, contact)
        }
        if (firstDimensions is ParticleRectDimensions && secondDimensions is CircleDimensions) {
            return detectCircleBox(secondParticle, firstParticle, firstDimensions, true, contact)
        }
        return detectBoxBox(firstParticle, secondParticle, contact)
    }

    private fun positionDeltaX(fromParticle: Particle, toParticle: Particle): Double =
        toParticle.transform.position.x - fromParticle.transform.position.x

    private fun positionDeltaY(fromParticle: Particle, toParticle: Particle): Double =
        toParticle.transform.position.y - fromParticle.transform.position.y

    private fun detectCircleCircle(firstParticle: Particle, secondParticle: Particle, contact: CollisionContact): Boolean {
        val deltaX = positionDeltaX(firstParticle, secondParticle)
        val deltaY = positionDeltaY(firstParticle, secondParticle)
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
        boxDimensions: ParticleRectDimensions,
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
        val firstDimensions = firstParticle.dimensions as ParticleRectDimensions
        val secondDimensions = secondParticle.dimensions as ParticleRectDimensions
        val firstHalfWidth = firstDimensions.width / 2.0
        val firstHalfHeight = firstDimensions.height / 2.0
        val secondHalfWidth = secondDimensions.width / 2.0
        val secondHalfHeight = secondDimensions.height / 2.0
        val firstCosine = cos(firstParticle.transform.angle)
        val firstSine = sin(firstParticle.transform.angle)
        val secondCosine = cos(secondParticle.transform.angle)
        val secondSine = sin(secondParticle.transform.angle)
        val deltaX = positionDeltaX(firstParticle, secondParticle)
        val deltaY = positionDeltaY(firstParticle, secondParticle)
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
        cornerDimensions: ParticleRectDimensions,
        containerParticle: Particle,
        containerDimensions: ParticleRectDimensions
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
        firstDimensions: ParticleRectDimensions,
        secondParticle: Particle,
        secondDimensions: ParticleRectDimensions,
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

    private fun writeSupportPoint(particle: Particle, dimensions: ParticleRectDimensions, directionX: Double, directionY: Double) {
        val cosine = cos(particle.transform.angle)
        val sine = sin(particle.transform.angle)
        val localDirectionX = directionX * cosine + directionY * sine
        val localDirectionY = -directionX * sine + directionY * cosine
        val localX = if (localDirectionX >= 0.0) dimensions.width / 2.0 else -dimensions.width / 2.0
        val localY = if (localDirectionY >= 0.0) dimensions.height / 2.0 else -dimensions.height / 2.0
        supportX = localX * cosine - localY * sine
        supportY = localX * sine + localY * cosine
    }

    private fun solveContact(
        firstParticle: Particle,
        secondParticle: Particle?,
        contact: CollisionContact,
        restitution: Double,
        friction: Double,
        staticFriction: Double,
        restitutionThreshold: Double
    ) {
        firstInverseMass = 1.0 / firstParticle.inertialMass()
        firstInverseInertia = 1.0 / firstParticle.momentOfInertia()
        secondInverseMass = if (secondParticle == null) 0.0 else 1.0 / secondParticle.inertialMass()
        secondInverseInertia = if (secondParticle == null) 0.0 else 1.0 / secondParticle.momentOfInertia()
        val normalX = contact.normalX
        val normalY = contact.normalY
        val tangentX = -normalY
        for (pointIndex in 0 until contact.pointCount) {
            firstArmX[pointIndex] = contact.pointX[pointIndex] - firstParticle.transform.position.x
            firstArmY[pointIndex] = contact.pointY[pointIndex] - firstParticle.transform.position.y
            secondArmX[pointIndex] = if (secondParticle == null) 0.0 else contact.pointX[pointIndex] - secondParticle.transform.position.x
            secondArmY[pointIndex] = if (secondParticle == null) 0.0 else contact.pointY[pointIndex] - secondParticle.transform.position.y
            val firstNormalCross = firstArmX[pointIndex] * normalY - firstArmY[pointIndex] * normalX
            val secondNormalCross = secondArmX[pointIndex] * normalY - secondArmY[pointIndex] * normalX
            val firstTangentCross = firstArmX[pointIndex] * normalX - firstArmY[pointIndex] * tangentX
            val secondTangentCross = secondArmX[pointIndex] * normalX - secondArmY[pointIndex] * tangentX
            normalMasses[pointIndex] = 1.0 / (firstInverseMass + secondInverseMass +
                    firstNormalCross * firstNormalCross * firstInverseInertia +
                    secondNormalCross * secondNormalCross * secondInverseInertia)
            tangentMasses[pointIndex] = 1.0 / (firstInverseMass + secondInverseMass +
                    firstTangentCross * firstTangentCross * firstInverseInertia +
                    secondTangentCross * secondTangentCross * secondInverseInertia)
            normalImpulses[pointIndex] = 0.0
            tangentImpulses[pointIndex] = 0.0
            val approachSpeed = relativeSpeedAlong(firstParticle, secondParticle, pointIndex, normalX, normalY)
            restitutionTargets[pointIndex] = if (approachSpeed < -restitutionThreshold) -restitution * approachSpeed else 0.0
        }
        for (iteration in 0 until SOLVER_ITERATIONS) {
            for (pointIndex in 0 until contact.pointCount) {
                val normalSpeed = relativeSpeedAlong(firstParticle, secondParticle, pointIndex, normalX, normalY)
                val accumulatedNormal = normalImpulses[pointIndex]
                val updatedNormal = maxOf(accumulatedNormal + normalMasses[pointIndex] * (restitutionTargets[pointIndex] - normalSpeed), 0.0)
                val appliedNormal = updatedNormal - accumulatedNormal
                normalImpulses[pointIndex] = updatedNormal
                applyImpulse(firstParticle, secondParticle, pointIndex, normalX * appliedNormal, normalY * appliedNormal)
                val tangentSpeed = relativeSpeedAlong(firstParticle, secondParticle, pointIndex, tangentX, normalX)
                val accumulatedTangent = tangentImpulses[pointIndex]
                var updatedTangent = accumulatedTangent - tangentMasses[pointIndex] * tangentSpeed
                if (abs(updatedTangent) > staticFriction * updatedNormal) {
                    updatedTangent = sign(updatedTangent) * friction * updatedNormal
                }
                val appliedTangent = updatedTangent - accumulatedTangent
                tangentImpulses[pointIndex] = updatedTangent
                applyImpulse(firstParticle, secondParticle, pointIndex, tangentX * appliedTangent, normalX * appliedTangent)
            }
        }
        var totalNormalImpulse = 0.0
        for (pointIndex in 0 until contact.pointCount) {
            totalNormalImpulse += normalImpulses[pointIndex]
        }
        firstParticle.dynamics.angularVelocity = firstParticle.dynamics.angularVelocity
            .coerceIn(-Particle.MAXIMUM_ANGULAR_VELOCITY, Particle.MAXIMUM_ANGULAR_VELOCITY)
        firstParticle.dynamics.angularVelocity =
            firstParticle.angularVelocityAfterRolling(totalNormalImpulse, firstParticle.collisionRadius())
        if (secondParticle != null) {
            secondParticle.dynamics.angularVelocity = secondParticle.dynamics.angularVelocity
                .coerceIn(-Particle.MAXIMUM_ANGULAR_VELOCITY, Particle.MAXIMUM_ANGULAR_VELOCITY)
            secondParticle.dynamics.angularVelocity =
                secondParticle.angularVelocityAfterRolling(totalNormalImpulse, secondParticle.collisionRadius())
        }
    }

    private fun relativeSpeedAlong(
        firstParticle: Particle,
        secondParticle: Particle?,
        pointIndex: Int,
        directionX: Double,
        directionY: Double
    ): Double {
        val firstDynamics = firstParticle.dynamics
        val firstPointVelocityX = firstDynamics.velocity.x - firstDynamics.angularVelocity * firstArmY[pointIndex]
        val firstPointVelocityY = firstDynamics.velocity.y + firstDynamics.angularVelocity * firstArmX[pointIndex]
        var secondPointVelocityX = 0.0
        var secondPointVelocityY = 0.0
        if (secondParticle != null) {
            val secondDynamics = secondParticle.dynamics
            secondPointVelocityX = secondDynamics.velocity.x - secondDynamics.angularVelocity * secondArmY[pointIndex]
            secondPointVelocityY = secondDynamics.velocity.y + secondDynamics.angularVelocity * secondArmX[pointIndex]
        }
        return (secondPointVelocityX - firstPointVelocityX) * directionX + (secondPointVelocityY - firstPointVelocityY) * directionY
    }

    private fun applyImpulse(
        firstParticle: Particle,
        secondParticle: Particle?,
        pointIndex: Int,
        impulseX: Double,
        impulseY: Double
    ) {
        val firstDynamics = firstParticle.dynamics
        firstDynamics.velocity.x -= impulseX * firstInverseMass
        firstDynamics.velocity.y -= impulseY * firstInverseMass
        firstDynamics.angularVelocity -=
            firstInverseInertia * (firstArmX[pointIndex] * impulseY - firstArmY[pointIndex] * impulseX)
        if (secondParticle == null) return
        val secondDynamics = secondParticle.dynamics
        secondDynamics.velocity.x += impulseX * secondInverseMass
        secondDynamics.velocity.y += impulseY * secondInverseMass
        secondDynamics.angularVelocity +=
            secondInverseInertia * (secondArmX[pointIndex] * impulseY - secondArmY[pointIndex] * impulseX)
    }
}
