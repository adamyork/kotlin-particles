package com.github.adamyork.particles.engine

import androidx.compose.ui.graphics.Color
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.Particle
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import com.github.adamyork.particles.service.RuntimeService
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonParticlePhysics(
    private val statusProviderFactory: () -> RuntimeService,
    public val physicsSettingsService: ParticlePhysicsSettingsService,
    private val particleEngineDiagnostics: ParticleEngineDiagnostics,
    private val particleEffectRegistry: ParticleEffectRegistry
) : ParticlePhysics {

    private val gravitySources = ArrayList<Particle>()

    private companion object {
        private const val REST_KEY_EFFECT_SHIFT: Int = 48
        private const val GRAVITY_SOURCE_MINIMUM_MASS_RATIO: Double = 0.03
        private const val GRAVITY_SOURCE_MINIMUM_MASS: Double = 0.015
        private const val REST_COUNT_BITS: Int = 20
        private const val REST_COUNT_MASK: Long = (1L shl REST_COUNT_BITS) - 1L
        private const val ENVIRONMENT_VALUE_COUNT: Int = 10
    }

    private var worldTime = 0.0

    private val restFrameCounts = HashMap<Long, Long>()

    private var stepCounter = 0L

    private val environment = DoubleArray(ENVIRONMENT_VALUE_COUNT)

    private val lastEnvironment = DoubleArray(ENVIRONMENT_VALUE_COUNT)

    private var asleepParticleCount = 0

    private val statusProvider: RuntimeService
        get() = statusProviderFactory()

    override fun applyParticlePhysics(
        generators: List<ParticleEffectGenerator>,
        viewPort: ParticleViewPort,
        deltaTime: Double
    ) {
        val gravity = physicsSettingsService.gravity
        val mediumDensity = physicsSettingsService.mediumDensity
        val spriteTicksPerFrame = statusProvider.getSpriteTicksPerFrame()
        worldTime += deltaTime
        stepCounter++
        var environmentIndex = 0
        environment[environmentIndex++] = gravity
        environment[environmentIndex++] = mediumDensity
        environment[environmentIndex++] = physicsSettingsService.drag
        environment[environmentIndex++] = physicsSettingsService.dragCoefficient
        environment[environmentIndex++] = physicsSettingsService.flowAmplitude
        environment[environmentIndex++] = physicsSettingsService.windVelocity
        environment[environmentIndex++] = viewPort.x.toDouble()
        environment[environmentIndex++] = viewPort.y.toDouble()
        environment[environmentIndex++] = viewPort.width.toDouble()
        environment[environmentIndex] = viewPort.height.toDouble()
        if (!environment.contentEquals(lastEnvironment)) {
            restFrameCounts.clear()
            environment.copyInto(lastEnvironment)
        }
        asleepParticleCount = 0
        for (generator in generators) {
            if (generator.particles.isEmpty()) continue
            val integrateStartMicroseconds = particleEngineDiagnostics.timestampMicroseconds()
            integrateParticles(
                generator.particles,
                deltaTime,
                gravity,
                mediumDensity,
                spriteTicksPerFrame,
                particleEffectRegistry.indexOf(generator.effect)
            )
            particleEngineDiagnostics.recordEffectCostSince(generator.effect, integrateStartMicroseconds)
        }
        particleEngineDiagnostics.recordSleepingParticles(asleepParticleCount)
    }

    private fun integrateParticles(
        particles: ArrayList<Particle>,
        deltaTime: Double,
        gravity: Double,
        mediumDensity: Double,
        spriteTicksPerFrame: Double,
        effectIndex: Int
    ) {
        for (particleIndex in particles.lastIndex downTo 0) {
            val particle = particles[particleIndex]
            val restKey = (effectIndex.toLong() shl REST_KEY_EFFECT_SHIFT) or particle.id
            if (particle.lifecycle.age >= particle.lifecycle.lifetime + particle.lifecycle.delay) {
                restFrameCounts.remove(restKey)
                particles.removeAt(particleIndex)
                continue
            }
            particle.lifecycle.age += deltaTime
            if (particle.lifecycle.age < particle.lifecycle.delay) continue
            if (particle.lifecycle.age < particle.lifecycle.pinnedUntil) continue
            val isAsleep = updateRestCount(particle, restKey)
            val activeAge = particle.lifecycle.age - particle.lifecycle.delay
            val progress = (activeAge / particle.lifecycle.lifetime).coerceIn(0.0, 1.0)
            if (isAsleep) asleepParticleCount++
            if (!isAsleep) {
                if (particle.material.adhesion > 0.0) {
                    integrateAdhesiveParticle(particle, gravity, mediumDensity, deltaTime)
                } else {
                    val inertialMass = particle.inertialMass()
                    val buoyancyAcceleration = mediumDensity * particle.dimensions.area() * gravity / inertialMass
                    particle.dynamics.velocity.y += (gravity - buoyancyAcceleration) * deltaTime
                    particle.dynamics.velocity.x += particle.dynamics.thrust.x / inertialMass * deltaTime
                    particle.dynamics.velocity.y += particle.dynamics.thrust.y / inertialMass * deltaTime
                    particle.dynamics.velocity.z += particle.dynamics.thrust.z / inertialMass * deltaTime
                    val flowVelocityX = flowVelocityXAt(particle.transform.position.y)
                    val dragFraction = min(1.0, max(0.0, physicsSettingsService.drag) * deltaTime)
                    if (dragFraction > 0.0) {
                        particle.dynamics.velocity.x += (flowVelocityX - particle.dynamics.velocity.x) * dragFraction
                        particle.dynamics.velocity.y *= 1.0 - dragFraction
                        particle.dynamics.velocity.z *= 1.0 - dragFraction
                    }
                    val relativeVelocityX = particle.dynamics.velocity.x - flowVelocityX
                    val relativeSpeed = sqrt(
                        relativeVelocityX * relativeVelocityX +
                            particle.dynamics.velocity.y * particle.dynamics.velocity.y +
                            particle.dynamics.velocity.z * particle.dynamics.velocity.z
                    )
                    val dragFactor = quadraticDragFactor(particle, inertialMass, relativeSpeed, deltaTime)
                    particle.dynamics.velocity.x = flowVelocityX + relativeVelocityX * dragFactor
                    particle.dynamics.velocity.y *= dragFactor
                    particle.dynamics.velocity.z *= dragFactor
                    particle.transform.position.x += particle.dynamics.velocity.x * deltaTime
                    particle.transform.position.y += particle.dynamics.velocity.y * deltaTime
                    particle.transform.position.z += particle.dynamics.velocity.z * deltaTime
                }
                if (particle.dynamics.angularVelocity != 0.0) {
                    particle.transform.angle = (particle.transform.angle + particle.dynamics.angularVelocity * deltaTime) % (2.0 * PI)
                }
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

    private fun updateRestCount(particle: Particle, restKey: Long): Boolean {
        val thrust = particle.dynamics.thrust
        val velocity = particle.dynamics.velocity
        val angularSurfaceSpeed = particle.dynamics.angularVelocity * particle.collisionRadius()
        val speedSquared = velocity.x * velocity.x + velocity.y * velocity.y + angularSurfaceSpeed * angularSurfaceSpeed
        val minimumActiveVelocity = physicsSettingsService.minActiveVelocity
        val isUnpowered = thrust.x == 0.0 && thrust.y == 0.0 && thrust.z == 0.0 && particle.material.adhesion <= 0.0
        if (!isUnpowered || speedSquared >= minimumActiveVelocity * minimumActiveVelocity) {
            return false
        }
        val storedEntry = restFrameCounts[restKey]
        val previousCount = if (storedEntry != null && (storedEntry shr REST_COUNT_BITS) == stepCounter - 1L) {
            (storedEntry and REST_COUNT_MASK).toInt()
        } else {
            0
        }
        val restCount = previousCount + 1
        restFrameCounts[restKey] = (stepCounter shl REST_COUNT_BITS) or restCount.toLong().coerceAtMost(REST_COUNT_MASK)
        return restCount > physicsSettingsService.restStepsBeforeSleep
    }

    override fun applyGravitation(collision: ParticleCollision, deltaTime: Double) {
        val gravitationalConstant = physicsSettingsService.gravitationalConstant
        if (gravitationalConstant <= 0.0) return
        selectGravitySources(collision.getAllPlacedParticles(), physicsSettingsService.gravitySourceCount)
        val softeningSquared = physicsSettingsService.gravitationalSoftening * physicsSettingsService.gravitationalSoftening
        val minimumAcceleration = max(physicsSettingsService.gravityMinimumAcceleration, Double.MIN_VALUE)
        val planeThickness = physicsSettingsService.gravityPlaneThickness
        val lowestSourceMass = if (gravitySources.isEmpty()) 0.0 else gravitySources[gravitySources.size - 1].gravitationalMass()
        for (sourceIndex in gravitySources.indices) {
            val source = gravitySources[sourceIndex]
            if (source.lifecycle.isExpired()) continue
            val range = sqrt(gravitationalConstant * source.gravitationalMass() / minimumAcceleration)
            collision.forEachParticleWithin(source.transform.position.x, source.transform.position.y, range) { other ->
                if (other !== source && !other.lifecycle.isExpired() && (other.gravitationalMass() < lowestSourceMass || !isGravitySource(other)) &&
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
            val candidateMass = candidate.gravitationalMass()
            if (candidateMass < Particle.MINIMUM_MASS || candidateMass < GRAVITY_SOURCE_MINIMUM_MASS) continue
            if (gravitySources.size == sourceCount && candidateMass <= lowestSourceMass) continue
            var insertIndex = gravitySources.size
            while (insertIndex > 0 && gravitySources[insertIndex - 1].gravitationalMass() < candidateMass) insertIndex--
            gravitySources.add(insertIndex, candidate)
            if (gravitySources.size > sourceCount) gravitySources.removeAt(gravitySources.size - 1)
            lowestSourceMass = gravitySources[gravitySources.size - 1].gravitationalMass()
        }
        if (gravitySources.isEmpty()) return
        val minimumSourceMass = gravitySources[0].gravitationalMass() * GRAVITY_SOURCE_MINIMUM_MASS_RATIO
        while (gravitySources.size > 1 && gravitySources[gravitySources.size - 1].gravitationalMass() < minimumSourceMass) {
            gravitySources.removeAt(gravitySources.size - 1)
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
        val pulledAcceleration = gravitationalConstant * pullingParticle.gravitationalMass() * inverseDistanceCubed * deltaTime
        pulledParticle.dynamics.velocity.x += deltaX * pulledAcceleration
        pulledParticle.dynamics.velocity.y += deltaY * pulledAcceleration
        val pullingAcceleration = gravitationalConstant * pulledParticle.gravitationalMass() * inverseDistanceCubed * deltaTime
        pullingParticle.dynamics.velocity.x -= deltaX * pullingAcceleration
        pullingParticle.dynamics.velocity.y -= deltaY * pullingAcceleration
    }

    private fun flowVelocityXAt(positionY: Double): Double {
        val amplitude = physicsSettingsService.flowAmplitude
        val wavelength = physicsSettingsService.flowWavelength
        val windVelocity = physicsSettingsService.windVelocity
        if (amplitude == 0.0 || wavelength <= 0.0) return windVelocity
        return windVelocity + amplitude * cos(2.0 * PI * positionY / wavelength + worldTime * physicsSettingsService.flowFrequency)
    }

    private fun integrateAdhesiveParticle(
        particle: Particle,
        gravity: Double,
        mediumDensity: Double,
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
        particle.dynamics.velocity.y *=
            quadraticDragFactor(particle, inertialMass, particle.dynamics.velocity.y, deltaTime)
        val distance = particle.dynamics.velocity.y * deltaTime
        particle.transform.position.y += distance
        val wettedMass = if (gravity > 0.0) particle.material.adhesion / gravity else 0.0
        val massLoss = (physicsSettingsService.friction + particle.material.friction) * wettedMass * distance / (particle.collisionRadius() * 2.0)
        val remainingMass = max(inertialMass - massLoss, Particle.MINIMUM_MASS)
        particle.dynamics.velocity.y *= remainingMass / inertialMass
        particle.material.density = Particle.densityFor(remainingMass, particle.dimensions)
    }

    private fun quadraticDragFactor(
        particle: Particle,
        inertialMass: Double,
        relativeSpeed: Double,
        deltaTime: Double
    ): Double {
        val dragCoefficient = physicsSettingsService.dragCoefficient
        if (dragCoefficient <= 0.0) return 1.0
        val dragPerSpeed = 0.5 * physicsSettingsService.mediumDensity * dragCoefficient *
                particle.frontalLength() / inertialMass
        return 1.0 / (1.0 + dragPerSpeed * relativeSpeed * deltaTime)
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
