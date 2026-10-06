package com.github.adamyork.kparticles.platform.engine

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.data.ParticleShape
import com.github.adamyork.kparticles.platform.service.CommonRuntimeService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CommonParticlePhysics(
    private val statusProviderFactory: () -> CommonRuntimeService,
    val physicsSettingsService: PhysicsSettingsService,
    private val spatialGrid: SpatialGrid
) : ParticlePhysics {

    private val statusProvider: CommonRuntimeService
        get() = statusProviderFactory()

    override fun applyParticlePhysics(
        particles: ArrayList<Particle>,
        viewPort: ViewPort,
        completedParticleResults: ArrayList<CompletedParticleResult>
    ) {
        val deltaTime = statusProvider.getDeltaTimeCoefficient()
        val gravity = physicsSettingsService.gravity

        spatialGrid.beginTick(viewPort)

        for (particleIndex in particles.lastIndex downTo 0) {
            val particle = particles[particleIndex]
            if (particle.age >= particle.lifetime + particle.delay) {
                completedParticleResults.add(
                    CompletedParticleResult(
                        particle.x.toInt(),
                        particle.y.toInt(),
                        particle.color
                    )
                )
                particles.removeAt(particleIndex)
                continue
            }

            particle.age += deltaTime
            particle.visible = particle.age >= particle.delay
            if (particle.age < particle.delay) {
                if (particle.canInteract()) spatialGrid.place(particle)
                continue
            }

            val activeAge = particle.age - particle.delay
            val progress = (activeAge / particle.lifetime).coerceIn(0.0, 1.0)

            particle.xVelocity += particle.xAcceleration * deltaTime
            particle.yVelocity += particle.yAcceleration * deltaTime
            particle.zVelocity += particle.zAcceleration * deltaTime

            val mass = particle.mass
            if (mass > 0.0) {
                val gravityScale = min(1.0, mass)
                particle.yVelocity += gravity * gravityScale * deltaTime
            }

            particle.xVelocity += particle.xForce * deltaTime
            particle.yVelocity += particle.yForce * deltaTime

            val totalDrag = max(0.0, particle.drag)
            if (totalDrag > 0.0) {
                val dragFactor = max(0.0, 1 - totalDrag * deltaTime)
                particle.xVelocity *= dragFactor
                particle.yVelocity *= dragFactor
                particle.zVelocity *= dragFactor
            }

            if (particle.maxXVelocity > 0.0) {
                particle.xVelocity = particle.xVelocity.coerceIn(-particle.maxXVelocity, particle.maxXVelocity)
            }
            if (particle.maxYVelocity > 0.0) {
                particle.yVelocity = particle.yVelocity.coerceIn(-particle.maxYVelocity, particle.maxYVelocity)
            }
            if (particle.maxZVelocity > 0.0) {
                particle.zVelocity = particle.zVelocity.coerceIn(-particle.maxZVelocity, particle.maxZVelocity)
            }

            particle.x += particle.xVelocity * deltaTime
            particle.y += particle.yVelocity * deltaTime
            particle.z += particle.zVelocity * deltaTime

            if (particle.viewportBound) {
                val halfWidth = if (particle.shape == ParticleShape.CIRCLE) particle.radius else particle.width / 2.0
                val halfHeight = if (particle.shape == ParticleShape.CIRCLE) particle.radius else particle.height / 2.0
                val minX = viewPort.x + halfWidth
                val maxX = viewPort.x + viewPort.width - halfWidth
                val minY = viewPort.y + halfHeight
                val maxY = viewPort.y + viewPort.height - halfHeight
                val massDamping = 1.0 / (1.0 + max(0.0, particle.mass))
                val restitutionFactor = particle.restitution.coerceIn(0.0, 1.0) * massDamping
                val (boundedX, boundedXVelocity, boundedDestinationX) = applyBoundaryRestitution(
                    particle.x, particle.xVelocity, particle.destinationX, minX, maxX, restitutionFactor
                )
                particle.x = boundedX
                particle.xVelocity = boundedXVelocity
                particle.destinationX = boundedDestinationX
                val (boundedY, boundedYVelocity, boundedDestinationY) = applyBoundaryRestitution(
                    particle.y, particle.yVelocity, particle.destinationY, minY, maxY, restitutionFactor
                )
                particle.y = boundedY
                particle.yVelocity = boundedYVelocity
                particle.destinationY = boundedDestinationY
            }

            if (particle.growthRate != 0.0) {
                particle.radius = (particle.radius + particle.growthRate * deltaTime).coerceIn(0.0, particle.maxRadius)
                particle.width = (particle.width + particle.growthRate * deltaTime).coerceIn(0.0, particle.maxWidth)
                particle.height = (particle.height + particle.growthRate * deltaTime).coerceIn(0.0, particle.maxHeight)
            }

            particle.color = interpolateColor(particle.startColor, particle.endColor, progress)
            val startAlpha = particle.initialAlpha
            val endAlpha = particle.endAlpha
            particle.alphaMultiplier = progress
            particle.alpha = startAlpha + (endAlpha - startAlpha) * particle.alphaMultiplier

            if (particle.canInteract()) spatialGrid.place(particle)
        }
    }

    override fun applyParticleCollisionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        distance: Double,
        overlapDistance: Double,
        normalX: Double,
        normalY: Double
    ) {
        if (firstParticle.attraction != secondParticle.attraction) {
            val attractionRange = max(firstParticle.attraction, secondParticle.attraction)
            val deltaX = normalX * distance
            val deltaY = normalY * distance
            if (abs(deltaX) <= attractionRange && abs(deltaY) <= attractionRange) return
        }
        val firstParticleMass = firstParticle.mass
        val secondParticleMass = secondParticle.mass
        val totalMass = firstParticleMass + secondParticleMass
        if (totalMass <= 0.0) {
            return
        }
        firstParticle.x -= normalX * overlapDistance * (secondParticleMass / totalMass)
        firstParticle.y -= normalY * overlapDistance * (secondParticleMass / totalMass)
        secondParticle.x += normalX * overlapDistance * (firstParticleMass / totalMass)
        secondParticle.y += normalY * overlapDistance * (firstParticleMass / totalMass)
        val relativeVelocityX = firstParticle.xVelocity - secondParticle.xVelocity
        val relativeVelocityY = firstParticle.yVelocity - secondParticle.yVelocity
        val impulse = 2 * (normalX * relativeVelocityX + normalY * relativeVelocityY) / totalMass
        val restitution = min(firstParticle.restitution, secondParticle.restitution)
        firstParticle.xVelocity -= impulse * secondParticleMass * normalX * (1 + restitution)
        firstParticle.yVelocity -= impulse * secondParticleMass * normalY * (1 + restitution)
        secondParticle.xVelocity += impulse * firstParticleMass * normalX * (1 + restitution)
        secondParticle.yVelocity += impulse * firstParticleMass * normalY * (1 + restitution)
    }

    override fun applyParticleAttractionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        distance: Double,
        normalX: Double,
        normalY: Double
    ) {
        if (firstParticle.age >= firstParticle.lifetime + firstParticle.delay) return
        if (secondParticle.age >= secondParticle.lifetime + secondParticle.delay) return
        if (!firstParticle.visible || !secondParticle.visible) return
        if (firstParticle.attraction <= 0.0 || secondParticle.attraction <= 0.0) return
        if (firstParticle.attraction == secondParticle.attraction) return

        val firstRadius = if (firstParticle.radius > 0.0) firstParticle.radius else firstParticle.width / 2.0
        val secondRadius = if (secondParticle.radius > 0.0) secondParticle.radius else secondParticle.width / 2.0
        if (distance <= firstRadius + secondRadius) {
            val consumingParticle = if (firstParticle.mass >= secondParticle.mass) firstParticle else secondParticle
            val consumedParticle = if (consumingParticle === firstParticle) secondParticle else firstParticle
            consumingParticle.mass += consumedParticle.mass
            consumingParticle.attraction += consumedParticle.attraction
            val mergedRadius =
                sqrt(consumingParticle.radius * consumingParticle.radius + consumedParticle.radius * consumedParticle.radius)
            consumingParticle.radius = mergedRadius
            consumingParticle.width = mergedRadius * 2.0
            consumingParticle.height = mergedRadius * 2.0
            consumingParticle.yForce = -physicsSettingsService.gravity * min(1.0, consumingParticle.mass)
            consumedParticle.age = consumedParticle.lifetime + consumedParticle.delay
            return
        }
        val weakerParticle =
            if (firstParticle.attraction < secondParticle.attraction) firstParticle else secondParticle
        val strongerParticle = if (weakerParticle === firstParticle) secondParticle else firstParticle
        val deltaX = normalX * distance
        val deltaY = normalY * distance
        if (abs(deltaX) > strongerParticle.attraction || abs(deltaY) > strongerParticle.attraction) return
        val directionSign = if (weakerParticle === firstParticle) 1.0 else -1.0
        val directionX = normalX * directionSign
        val directionY = normalY * directionSign
        val pullMagnitude = strongerParticle.attraction * 0.5
        val deltaTime = statusProvider.getDeltaTimeCoefficient()
        weakerParticle.xVelocity += directionX * pullMagnitude * deltaTime
        weakerParticle.yVelocity += directionY * pullMagnitude * deltaTime
        val maxSpeed = weakerParticle.maxXVelocity
        if (maxSpeed > 0.0) {
            val speed = sqrt(
                weakerParticle.xVelocity * weakerParticle.xVelocity +
                    weakerParticle.yVelocity * weakerParticle.yVelocity
            )
            if (speed > maxSpeed) {
                val scale = maxSpeed / speed
                weakerParticle.xVelocity *= scale
                weakerParticle.yVelocity *= scale
            }
        }
    }

    private fun applyBoundaryRestitution(
        position: Double,
        velocity: Double,
        destination: Int,
        min: Double,
        max: Double,
        restitutionFactor: Double
    ): Triple<Double, Double, Int> {
        return when {
            position < min -> Triple(
                min + (min - position),
                -velocity * restitutionFactor,
                (2 * min - destination).roundToInt()
            )

            position > max -> Triple(
                max - (position - max),
                -velocity * restitutionFactor,
                (2 * max - destination).roundToInt()
            )

            else -> Triple(position, velocity, destination)
        }
    }

    private fun interpolateColor(color1: Color, color2: Color, factor: Double): Color {
        val interpolationFactor = factor.coerceIn(0.0, 1.0).toFloat()
        return Color(
            red = color1.red + (color2.red - color1.red) * interpolationFactor,
            green = color1.green + (color2.green - color1.green) * interpolationFactor,
            blue = color1.blue + (color2.blue - color1.blue) * interpolationFactor,
            alpha = color1.alpha + (color2.alpha - color1.alpha) * interpolationFactor
        )
    }
}
