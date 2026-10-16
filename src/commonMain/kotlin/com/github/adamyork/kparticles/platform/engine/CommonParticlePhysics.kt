package com.github.adamyork.kparticles.platform.engine

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
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
    val physicsSettingsService: PhysicsSettingsService
) : ParticlePhysics {

    private val gravitySources = ArrayList<Particle>()

    private var worldTime = 0.0

    private val statusProvider: CommonRuntimeService
        get() = statusProviderFactory()

    override fun applyParticlePhysics(
        generators: List<ParticleEffectGenerator>,
        viewPort: ViewPort,
        completedParticleResults: ArrayList<CompletedParticleResult>
    ) {
        val deltaTime = statusProvider.getDeltaTimeCoefficient()
        val gravity = physicsSettingsService.gravity
        val maxParticleSpeed = physicsSettingsService.maxParticleSpeed
        val mediumDensity = physicsSettingsService.mediumDensity
        val spriteTicksPerFrame = (statusProvider.assetService.appProperties.engine.tickTargetPerSec.toDouble() /
            statusProvider.assetService.appProperties.engine.spriteAnimationFramePerSec.toDouble()).coerceAtLeast(1.0)
        worldTime += deltaTime
        for (generator in generators) {
            integrateParticles(
                generator.particles, deltaTime, gravity, mediumDensity, maxParticleSpeed, spriteTicksPerFrame, completedParticleResults
            )
        }
    }

    private fun integrateParticles(
        particles: ArrayList<Particle>,
        deltaTime: Double,
        gravity: Double,
        mediumDensity: Double,
        maxParticleSpeed: Double,
        spriteTicksPerFrame: Double,
        completedParticleResults: ArrayList<CompletedParticleResult>
    ) {
        for (particleIndex in particles.lastIndex downTo 0) {
            val particle = particles[particleIndex]
            if (particle.lifecycle.age >= particle.lifecycle.lifetime + particle.lifecycle.delay) {
                completedParticleResults.add(
                    CompletedParticleResult(
                        particle.transform.position.x.toInt(),
                        particle.transform.position.y.toInt(),
                        particle.appearance.color
                    )
                )
                particles.removeAt(particleIndex)
                continue
            }

            particle.lifecycle.age += deltaTime
            if (particle.lifecycle.age < particle.lifecycle.delay) continue

            val activeAge = particle.lifecycle.age - particle.lifecycle.delay
            val progress = (activeAge / particle.lifecycle.lifetime).coerceIn(0.0, 1.0)


            if (particle.material.adhesion > 0.0) {
                integrateAdhesiveParticle(particle, gravity, mediumDensity, maxParticleSpeed, deltaTime)
            } else {
                val inertialMass = particle.inertialMass()
                val buoyancyAcceleration = mediumDensity * particle.dimensions.area() * gravity / inertialMass
                particle.dynamics.velocity.y += (gravity - buoyancyAcceleration) * deltaTime
                particle.dynamics.velocity.x += particle.dynamics.thrust.x / inertialMass * deltaTime
                particle.dynamics.velocity.y += particle.dynamics.thrust.y / inertialMass * deltaTime
                particle.dynamics.velocity.z += particle.dynamics.thrust.z / inertialMass * deltaTime
                val dragFraction = min(1.0, max(0.0, physicsSettingsService.drag) * deltaTime)
                if (dragFraction > 0.0) {
                    val flowVelocityX = flowVelocityXAt(particle.transform.position.y)
                    particle.dynamics.velocity.x += (flowVelocityX - particle.dynamics.velocity.x) * dragFraction
                    particle.dynamics.velocity.y *= 1.0 - dragFraction
                    particle.dynamics.velocity.z *= 1.0 - dragFraction
                }
                limitSpeed(particle, maxParticleSpeed)
                particle.dynamics.velocity.z = particle.dynamics.velocity.z.coerceIn(-maxParticleSpeed, maxParticleSpeed)
                particle.transform.position.x += particle.dynamics.velocity.x * deltaTime
                particle.transform.position.y += particle.dynamics.velocity.y * deltaTime
                particle.transform.position.z += particle.dynamics.velocity.z * deltaTime
            }

            if (particle.dynamics.angularVelocity != 0.0) {
                particle.transform.angle = (particle.transform.angle + particle.dynamics.angularVelocity * deltaTime) % (2.0 * PI)
            }
            particle.dimensions.advance(deltaTime)

            particle.appearance.color = interpolateColor(particle.appearance.startColor, particle.appearance.endColor, progress)
            val startAlpha = particle.appearance.initialAlpha
            val endAlpha = particle.appearance.endAlpha
            particle.appearance.alphaMultiplier = progress
            particle.appearance.alpha = startAlpha + (endAlpha - startAlpha) * particle.appearance.alphaMultiplier
            val animation = particle.animation
            if (animation != null) {
                animation.frame = ((particle.lifecycle.age / spriteTicksPerFrame).toInt()) % animation.maxFrames
            }

        }
    }

    override fun applyGravitation(collision: ParticleCollision) {
        val gravitationalConstant = physicsSettingsService.gravitationalConstant
        if (gravitationalConstant <= 0.0) return
        selectGravitySources(collision.placedParticles(), physicsSettingsService.gravitySourceCount)
        val deltaTime = statusProvider.getDeltaTimeCoefficient()
        val softeningSquared = physicsSettingsService.gravitationalSoftening * physicsSettingsService.gravitationalSoftening
        val minimumAcceleration = max(physicsSettingsService.gravityMinimumAcceleration, Double.MIN_VALUE)
        val planeThickness = physicsSettingsService.gravityPlaneThickness
        val lowestSourceMass = if (gravitySources.isEmpty()) 0.0 else gravitySources[gravitySources.size - 1].mass()
        for (sourceIndex in gravitySources.indices) {
            val source = gravitySources[sourceIndex]
            if (source.lifecycle.isExpired()) continue
            val range = sqrt(gravitationalConstant * source.mass() / minimumAcceleration)
            collision.forEachParticleWithin(source.transform.position.x, source.transform.position.y, range) { other ->
                if (other !== source && !other.lifecycle.isExpired() && (other.mass() < lowestSourceMass || !isGravitySource(other)) &&
                    abs(other.transform.position.z - source.transform.position.z) < planeThickness
                ) {
                    applyPull(source, other, gravitationalConstant, softeningSquared, deltaTime)
                }
            }
            for (otherIndex in sourceIndex + 1 until gravitySources.size) {
                val otherSource = gravitySources[otherIndex]
                if (otherSource.lifecycle.isExpired() || abs(otherSource.transform.position.z - source.transform.position.z) >= planeThickness) continue
                applyPull(source, otherSource, gravitationalConstant, softeningSquared, deltaTime)
            }
        }
    }

    override fun applyParticleAccretion(firstParticle: Particle, secondParticle: Particle): Boolean {
        if (firstParticle.lifecycle.isExpired() || secondParticle.lifecycle.isExpired()) return false
        if (abs(firstParticle.transform.position.z - secondParticle.transform.position.z) >= physicsSettingsService.gravityPlaneThickness) return false
        val consumingParticle = if (firstParticle.mass() >= secondParticle.mass()) firstParticle else secondParticle
        val consumedParticle = if (consumingParticle === firstParticle) secondParticle else firstParticle
        val consumingMass = consumingParticle.mass()
        val consumedMass = consumedParticle.mass()
        if (consumingMass < consumedParticle.inertialMass() * physicsSettingsService.accretionMassRatio) return false
        val gravity = physicsSettingsService.gravity
        val mediumDensity = physicsSettingsService.mediumDensity
        val previousNetWeight = consumingMass * Particle.netGravityOf(consumingParticle.material.density, gravity, mediumDensity)
        val holdFraction = if (previousNetWeight != 0.0) -consumingParticle.dynamics.thrust.y / previousNetWeight else 0.0
        val combinedMass = consumingMass + consumedMass
        consumingParticle.dynamics.velocity.x =
            (consumingParticle.dynamics.velocity.x * consumingMass + consumedParticle.dynamics.velocity.x * consumedMass) / combinedMass
        consumingParticle.dynamics.velocity.y =
            (consumingParticle.dynamics.velocity.y * consumingMass + consumedParticle.dynamics.velocity.y * consumedMass) / combinedMass
        consumingParticle.dimensions.fitArea(consumingParticle.dimensions.area() + consumedParticle.dimensions.area())
        consumingParticle.material.density = Particle.densityFor(combinedMass, consumingParticle.dimensions)
        val thrustScale = combinedMass / consumingMass
        consumingParticle.dynamics.thrust.x *= thrustScale
        consumingParticle.dynamics.thrust.z *= thrustScale
        consumingParticle.dynamics.thrust.y =
            -holdFraction * combinedMass * Particle.netGravityOf(consumingParticle.material.density, gravity, mediumDensity)
        consumedParticle.lifecycle.age = consumedParticle.lifecycle.lifetime + consumedParticle.lifecycle.delay
        return true
    }

    private fun selectGravitySources(candidates: List<Particle>, sourceCount: Int) {
        gravitySources.clear()
        if (sourceCount <= 0) return
        var lowestSourceMass = 0.0
        for (candidate in candidates) {
            val candidateMass = candidate.mass()
            if (candidateMass < Particle.MINIMUM_MASS) continue
            if (gravitySources.size == sourceCount && candidateMass <= lowestSourceMass) continue
            var insertIndex = gravitySources.size
            while (insertIndex > 0 && gravitySources[insertIndex - 1].mass() < candidateMass) insertIndex--
            gravitySources.add(insertIndex, candidate)
            if (gravitySources.size > sourceCount) gravitySources.removeAt(gravitySources.size - 1)
            lowestSourceMass = gravitySources[gravitySources.size - 1].mass()
        }
    }

    private fun isGravitySource(particle: Particle): Boolean {
        for (source in gravitySources) {
            if (source === particle) return true
        }
        return false
    }

    private fun applyPull(
        pullingParticle: Particle,
        pulledParticle: Particle,
        gravitationalConstant: Double,
        softeningSquared: Double,
        deltaTime: Double
    ) {
        val deltaX = pullingParticle.transform.position.x - pulledParticle.transform.position.x
        val deltaY = pullingParticle.transform.position.y - pulledParticle.transform.position.y
        val softenedDistanceSquared = deltaX * deltaX + deltaY * deltaY + softeningSquared
        val inverseDistanceCubed = 1.0 / (softenedDistanceSquared * sqrt(softenedDistanceSquared))
        val pulledAcceleration = gravitationalConstant * pullingParticle.mass() * inverseDistanceCubed * deltaTime
        pulledParticle.dynamics.velocity.x += deltaX * pulledAcceleration
        pulledParticle.dynamics.velocity.y += deltaY * pulledAcceleration
        limitSpeed(pulledParticle, physicsSettingsService.maxParticleSpeed)
        val pullingAcceleration = gravitationalConstant * pulledParticle.mass() * inverseDistanceCubed * deltaTime
        pullingParticle.dynamics.velocity.x -= deltaX * pullingAcceleration
        pullingParticle.dynamics.velocity.y -= deltaY * pullingAcceleration
        limitSpeed(pullingParticle, physicsSettingsService.maxParticleSpeed)
    }

    private fun flowVelocityXAt(positionY: Double): Double {
        val amplitude = physicsSettingsService.flowAmplitude
        val wavelength = physicsSettingsService.flowWavelength
        if (amplitude == 0.0 || wavelength <= 0.0) return 0.0
        return amplitude * cos(2.0 * PI * positionY / wavelength + worldTime * physicsSettingsService.flowFrequency)
    }

    private fun integrateAdhesiveParticle(
        particle: Particle,
        gravity: Double,
        mediumDensity: Double,
        maxParticleSpeed: Double,
        deltaTime: Double
    ) {
        val inertialMass = particle.inertialMass()
        val buoyancyAcceleration = mediumDensity * particle.dimensions.area() * gravity / inertialMass
        val netForce = inertialMass * (gravity - buoyancyAcceleration) - particle.material.adhesion
        particle.dynamics.velocity.x = 0.0
        if (particle.dynamics.velocity.y <= 0.0 && netForce <= 0.0) {
            particle.dynamics.velocity.y = 0.0
            return
        }
        particle.dynamics.velocity.y = max(0.0, particle.dynamics.velocity.y + netForce / inertialMass * deltaTime)
        limitSpeed(particle, maxParticleSpeed)
        val distance = particle.dynamics.velocity.y * deltaTime
        particle.transform.position.y += distance
        val wettedMass = if (gravity > 0.0) particle.material.adhesion / gravity else 0.0
        val massLoss = (physicsSettingsService.friction + particle.material.friction) * wettedMass * distance / (particle.collisionRadius() * 2.0)
        val remainingMass = max(inertialMass - massLoss, Particle.MINIMUM_MASS)
        particle.dynamics.velocity.y *= remainingMass / inertialMass
        particle.material.density = Particle.densityFor(remainingMass, particle.dimensions)
    }

    private fun limitSpeed(particle: Particle, maxSpeed: Double) {
        if (maxSpeed <= 0.0) return
        val speedSquared = particle.dynamics.velocity.x * particle.dynamics.velocity.x +
                particle.dynamics.velocity.y * particle.dynamics.velocity.y
        if (speedSquared <= maxSpeed * maxSpeed) return
        val scale = maxSpeed / sqrt(speedSquared)
        particle.dynamics.velocity.x *= scale
        particle.dynamics.velocity.y *= scale
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
