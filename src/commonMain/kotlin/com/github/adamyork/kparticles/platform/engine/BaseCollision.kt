package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.data.Particle
import kotlin.math.sqrt

abstract class BaseCollision(
    private val physics: Physics
) : Collision {

    override fun applyParticleCollision(particles: ArrayList<Particle>) {
        for (firstParticleIndex in particles.indices) {
            for (secondParticleIndex in firstParticleIndex + 1 until particles.size) {
                val firstParticle = particles[firstParticleIndex]
                val secondParticle = particles[secondParticleIndex]
                if (!firstParticle.canCollide || !secondParticle.canCollide) continue
                val firstParticleRadius =
                    if (firstParticle.radius > 0) firstParticle.radius else firstParticle.width / 2
                val secondParticleRadius =
                    if (secondParticle.radius > 0) secondParticle.radius else secondParticle.width / 2
                val deltaX = secondParticle.x - firstParticle.x
                val deltaY = secondParticle.y - firstParticle.y
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                val minimumDistance = firstParticleRadius + secondParticleRadius
                if (distance < minimumDistance && distance > 0) {
                    val overlapDistance = minimumDistance - distance
                    val normalX = deltaX / distance
                    val normalY = deltaY / distance
                    physics.applyParticleCollisionPhysics(
                        firstParticle = firstParticle,
                        secondParticle = secondParticle,
                        overlapDistance = overlapDistance,
                        normalX = normalX,
                        normalY = normalY
                    )
                }
            }
        }
    }
}
