package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractCollision(
    private val particlePhysics: ParticlePhysics,
    private val spatialGrid: SpatialGrid,
    private val physicsSettingsService: PhysicsSettingsService
) : Collision {

    override fun applyParticleCollision(particles: ArrayList<Particle>) {
        val minActiveVelocitySquared = physicsSettingsService.minActiveVelocity * physicsSettingsService.minActiveVelocity
        val fragments = ArrayList<Particle>()
        spatialGrid.forEachNearbyPair { firstParticle, secondParticle ->
            if (firstParticle.attraction <= 0.0 && secondParticle.attraction <= 0.0 &&
                isResting(firstParticle, minActiveVelocitySquared) && isResting(secondParticle, minActiveVelocitySquared)
            ) {
                return@forEachNearbyPair
            }
            val deltaX = secondParticle.x - firstParticle.x
            val deltaY = secondParticle.y - firstParticle.y
            val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
            if (distance <= 0.0) return@forEachNearbyPair
            val normalX = deltaX / distance
            val normalY = deltaY / distance
            particlePhysics.applyParticleAttractionPhysics(firstParticle, secondParticle, distance, normalX, normalY)
            if (!firstParticle.canCollide || !secondParticle.canCollide) return@forEachNearbyPair
            val firstParticleRadius =
                if (firstParticle.radius > 0) firstParticle.radius else firstParticle.width / 2
            val secondParticleRadius =
                if (secondParticle.radius > 0) secondParticle.radius else secondParticle.width / 2
            val minimumDistance = firstParticleRadius + secondParticleRadius
            if (distance < minimumDistance) {
                val overlapDistance = minimumDistance - distance
                val isHardnessMismatch = firstParticle.hardness != secondParticle.hardness
                val harderParticle = if (firstParticle.hardness > secondParticle.hardness) firstParticle else secondParticle
                val impactMomentum =
                    if (isHardnessMismatch) harderParticle.mass * hypot(harderParticle.xVelocity, harderParticle.yVelocity) else 0.0
                particlePhysics.applyParticleCollisionPhysics(
                    firstParticle = firstParticle,
                    secondParticle = secondParticle,
                    distance = distance,
                    overlapDistance = overlapDistance,
                    normalX = normalX,
                    normalY = normalY
                )
                if (isHardnessMismatch) {
                    val weakerParticle = if (harderParticle === firstParticle) secondParticle else firstParticle
                    splitParticle(weakerParticle, fragmentCountFor(impactMomentum), normalX, normalY, fragments)
                }
            }
        }
        particles.addAll(fragments)
    }

    private fun fragmentCountFor(impactMomentum: Double): Int {
        return (impactMomentum / physicsSettingsService.shatterMomentumPerFragment).toInt()
            .coerceIn(physicsSettingsService.shatterMinimumFragmentCount, physicsSettingsService.shatterMaximumFragmentCount)
    }

    private fun splitParticle(
        particle: Particle,
        requestedFragmentCount: Int,
        normalX: Double,
        normalY: Double,
        fragments: ArrayList<Particle>
    ) {
        val currentRadius = if (particle.radius > 0.0) particle.radius else particle.width / 2.0
        val fragmentCount = min(requestedFragmentCount, (currentRadius / physicsSettingsService.shatterMinimumFragmentRadius).toInt())
        if (fragmentCount < physicsSettingsService.shatterMinimumFragmentCount) return
        val fragmentDiameter = currentRadius / fragmentCount * 2.0
        val tangentX = -normalY
        val tangentY = normalX
        val sourceX = particle.x
        val sourceY = particle.y
        val sourceXVelocity = particle.xVelocity
        val sourceYVelocity = particle.yVelocity
        val centerOffset = (fragmentCount - 1) / 2.0
        val fragmentMass = particle.mass / fragmentCount
        val fragmentYForce = -physicsSettingsService.gravity * min(1.0, fragmentMass)
        for (fragmentIndex in 1 until fragmentCount) {
            val offset = fragmentIndex - centerOffset
            fragments.add(
                particle.copy(
                    id = Random.nextInt().toString(16),
                    radius = particle.radius / fragmentCount,
                    width = particle.width / fragmentCount,
                    height = particle.height / fragmentCount,
                    x = sourceX + tangentX * fragmentDiameter * offset,
                    y = sourceY + tangentY * fragmentDiameter * offset,
                    xVelocity = sourceXVelocity + tangentX * physicsSettingsService.shatterSpreadSpeed * 2.0 * offset,
                    yVelocity = sourceYVelocity + tangentY * physicsSettingsService.shatterSpreadSpeed * 2.0 * offset,
                    mass = fragmentMass,
                    yForce = fragmentYForce
                )
            )
        }
        particle.radius /= fragmentCount
        particle.width /= fragmentCount
        particle.height /= fragmentCount
        particle.x = sourceX - tangentX * fragmentDiameter * centerOffset
        particle.y = sourceY - tangentY * fragmentDiameter * centerOffset
        particle.xVelocity = sourceXVelocity - tangentX * physicsSettingsService.shatterSpreadSpeed * 2.0 * centerOffset
        particle.yVelocity = sourceYVelocity - tangentY * physicsSettingsService.shatterSpreadSpeed * 2.0 * centerOffset
        particle.mass = fragmentMass
        particle.yForce = fragmentYForce
    }

    private fun isResting(particle: Particle, minActiveVelocitySquared: Double): Boolean {
        val speedSquared = particle.xVelocity * particle.xVelocity + particle.yVelocity * particle.yVelocity
        return speedSquared < minActiveVelocitySquared
    }
}
