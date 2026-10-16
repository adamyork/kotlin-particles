package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractParticleCollision(
    private val particlePhysics: ParticlePhysics,
    private val spatialGrid: SpatialGrid,
    private val contactDetector: ContactDetector,
    private val contactSolver: ContactSolver,
    private val physicsSettingsService: PhysicsSettingsService
) : ParticleCollision {

    private companion object {
        private const val WALL_CORNER_COUNT: Int = 4
        private const val WALL_CONTACT_TOLERANCE: Double = 0.5
    }

    private val contact = CollisionContact()

    private val wallContact = CollisionContact()

    private val cornerOffsetX = DoubleArray(WALL_CORNER_COUNT)

    private val cornerOffsetY = DoubleArray(WALL_CORNER_COUNT)

    override fun applyParticleCollision(generators: List<ParticleEffectGenerator>, viewPort: ViewPort) {
        val minActiveVelocitySquared =
            physicsSettingsService.minActiveVelocity * physicsSettingsService.minActiveVelocity
        val fragments = ArrayList<Pair<Particle, Particle>>()
        val planeThickness = physicsSettingsService.collisionPlaneThickness
        placeParticles(generators, viewPort)
        particlePhysics.applyGravitation(this)
        spatialGrid.forEachCollisionPair { firstParticle, secondParticle ->
            if (abs(firstParticle.transform.position.z - secondParticle.transform.position.z) >= planeThickness) {
                return@forEachCollisionPair
            }
            if (!contactDetector.detect(firstParticle, secondParticle, contact)) return@forEachCollisionPair
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
            val impactMomentum =
                if (isHardnessMismatch) harderParticle.mass() * hypot(
                    harderParticle.dynamics.velocity.x,
                    harderParticle.dynamics.velocity.y
                ) else 0.0
            applyParticleCollisionPhysics(firstParticle, secondParticle, contact)
            if (isHardnessMismatch) {
                splitParticle(
                    weakerParticle,
                    fragmentCountFor(impactMomentum),
                    contact.normalX,
                    contact.normalY,
                    fragments
                )
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

    override fun placedParticles(): List<Particle> = spatialGrid.placedParticles()

    override fun forEachParticleWithin(centerX: Double, centerY: Double, range: Double, action: (Particle) -> Unit) {
        spatialGrid.forEachParticleWithin(centerX, centerY, range, action)
    }

    private fun placeParticles(generators: List<ParticleEffectGenerator>, viewPort: ViewPort) {
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
            applyCircleCollisionPhysics(firstParticle, secondParticle, contact.depth, contact.normalX, contact.normalY)
        } else {
            applyBoxCollisionPhysics(firstParticle, secondParticle, contact)
        }
    }

    private fun applyBoxCollisionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        contact: CollisionContact
    ) {
        val firstParticleMass = firstParticle.inertialMass()
        val secondParticleMass = secondParticle.inertialMass()
        val totalMass = firstParticleMass + secondParticleMass
        firstParticle.transform.position.x -= contact.normalX * contact.depth * (secondParticleMass / totalMass)
        firstParticle.transform.position.y -= contact.normalY * contact.depth * (secondParticleMass / totalMass)
        secondParticle.transform.position.x += contact.normalX * contact.depth * (firstParticleMass / totalMass)
        secondParticle.transform.position.y += contact.normalY * contact.depth * (firstParticleMass / totalMass)
        val restitution = min(firstParticle.material.restitution, secondParticle.material.restitution)
        val friction = physicsSettingsService.friction +
                sqrt(firstParticle.material.friction * secondParticle.material.friction)
        val staticFriction = max(
            friction,
            physicsSettingsService.friction +
                    sqrt(firstParticle.material.staticFriction * secondParticle.material.staticFriction)
        )
        contactSolver.solve(
            firstParticle,
            secondParticle,
            contact,
            1.0 + 2.0 * restitution,
            friction,
            staticFriction,
            physicsSettingsService.minActiveVelocity
        )
    }

    private fun applyCircleCollisionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        overlapDistance: Double,
        normalX: Double,
        normalY: Double
    ) {
        val firstParticleMass = firstParticle.inertialMass()
        val secondParticleMass = secondParticle.inertialMass()
        val totalMass = firstParticleMass + secondParticleMass
        firstParticle.transform.position.x -= normalX * overlapDistance * (secondParticleMass / totalMass)
        firstParticle.transform.position.y -= normalY * overlapDistance * (secondParticleMass / totalMass)
        secondParticle.transform.position.x += normalX * overlapDistance * (firstParticleMass / totalMass)
        secondParticle.transform.position.y += normalY * overlapDistance * (firstParticleMass / totalMass)
        val relativeVelocityX = firstParticle.dynamics.velocity.x - secondParticle.dynamics.velocity.x
        val relativeVelocityY = firstParticle.dynamics.velocity.y - secondParticle.dynamics.velocity.y
        val relativeNormalSpeed = normalX * relativeVelocityX + normalY * relativeVelocityY
        val impulse = 2 * relativeNormalSpeed / totalMass
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
        val normalImpulseMagnitude = abs(2 * reducedMass * relativeNormalSpeed * (1 + restitution))
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

    private fun applyBoundaryCollision(particles: List<Particle>, viewPort: ViewPort) {
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
        contactSolver.solve(particle, null, wallContact, bounce, friction, staticFriction, restitutionThreshold)
    }

    private fun fillWallContact(particle: Particle, wallNormalX: Double, wallNormalY: Double) {
        val position = particle.transform.position
        wallContact.normalX = wallNormalX
        wallContact.normalY = wallNormalY
        wallContact.depth = 0.0
        val dimensions = particle.dimensions
        if (dimensions !is RectDimensions) {
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
            cornerOffsetX[cornerIndex] = signX * halfWidth * cosine - signY * halfHeight * sine
            cornerOffsetY[cornerIndex] = signX * halfWidth * sine + signY * halfHeight * cosine
            val support = cornerOffsetX[cornerIndex] * wallNormalX + cornerOffsetY[cornerIndex] * wallNormalY
            maximumSupport = max(maximumSupport, support)
        }
        wallContact.pointCount = 0
        for (cornerIndex in 0 until WALL_CORNER_COUNT) {
            val support = cornerOffsetX[cornerIndex] * wallNormalX + cornerOffsetY[cornerIndex] * wallNormalY
            if (support >= maximumSupport - WALL_CONTACT_TOLERANCE && wallContact.pointCount < CollisionContact.MAXIMUM_POINTS) {
                wallContact.pointX[wallContact.pointCount] = position.x + cornerOffsetX[cornerIndex]
                wallContact.pointY[wallContact.pointCount] = position.y + cornerOffsetY[cornerIndex]
                wallContact.pointCount++
            }
        }
    }

    private fun fragmentCountFor(impactMomentum: Double): Int {
        return (impactMomentum / physicsSettingsService.shatterMomentumPerFragment).toInt()
            .coerceIn(
                physicsSettingsService.shatterMinimumFragmentCount,
                physicsSettingsService.shatterMaximumFragmentCount
            )
    }

    private fun splitParticle(
        particle: Particle,
        requestedFragmentCount: Int,
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
        val sourceXVelocity = particle.dynamics.velocity.x
        val sourceYVelocity = particle.dynamics.velocity.y
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
                        sourceXVelocity + tangentX * physicsSettingsService.shatterSpreadSpeed * 2.0 * offset,
                        sourceYVelocity + normalX * physicsSettingsService.shatterSpreadSpeed * 2.0 * offset,
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
            sourceXVelocity - tangentX * physicsSettingsService.shatterSpreadSpeed * 2.0 * centerOffset
        particle.dynamics.velocity.y =
            sourceYVelocity - normalX * physicsSettingsService.shatterSpreadSpeed * 2.0 * centerOffset
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
}
