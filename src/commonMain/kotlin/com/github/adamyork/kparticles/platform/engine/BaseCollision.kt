package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.sqrt

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class BaseCollision(
    private val particlePhysics: ParticlePhysics,
    private val spatialGrid: SpatialGrid,
    private val physicsSettingsService: PhysicsSettingsService
) : Collision {

    override fun applyParticleCollision(particles: ArrayList<Particle>) {
        val minActiveVelocitySquared = physicsSettingsService.minActiveVelocity * physicsSettingsService.minActiveVelocity
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
                particlePhysics.applyParticleCollisionPhysics(
                    firstParticle = firstParticle,
                    secondParticle = secondParticle,
                    distance = distance,
                    overlapDistance = overlapDistance,
                    normalX = normalX,
                    normalY = normalY
                )
            }
        }
    }

    private fun isResting(particle: Particle, minActiveVelocitySquared: Double): Boolean {
        val speedSquared = particle.xVelocity * particle.xVelocity + particle.yVelocity * particle.yVelocity
        return speedSquared < minActiveVelocitySquared
    }
}
