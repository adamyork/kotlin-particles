package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.*
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class FireworkTailGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.FIREWORK_TAIL) {

    private companion object {
        private const val FIREWORK_TAIL_PARTICLE_COUNT: Int = 256
        private const val FIREWORK_TAIL_NET_GRAVITY_FRACTION: Double = 0.02
        private const val FIREWORK_TAIL_DESTINATION_BAND_START_RATIO: Double = 0.15
        private const val FIREWORK_TAIL_DESTINATION_BAND_SIZE_RATIO: Double = 0.2
        private const val FIREWORK_TAIL_STAGGER_TIMING_RATIO: Double = 0.5
        private const val FIREWORK_TAIL_DELAY_JITTER_RANGE: Double = 20.0
        private const val FIREWORK_TAIL_MIN_RADIUS: Double = 8.0
        private const val FIREWORK_TAIL_RADIUS_RANGE: Double = 8.0
        private const val FIREWORK_TAIL_MAX_ACTIVE_PARTICLES: Int = 1024
        private const val FIREWORK_TAIL_GPU_KIND: Float = 4f
        private const val FIREWORK_TAIL_POST_APEX_TICKS: Double = 4.0
        private const val FIREWORK_TAIL_MINIMUM_DRAG: Double = 0.0001
        private const val FIREWORK_TAIL_SOLVER_ITERATIONS: Int = 48
    }

    private val fireworkTailGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(FIREWORK_TAIL_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(fireworkTailGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val deceleration = physicsSettingsService.gravity * FIREWORK_TAIL_NET_GRAVITY_FRACTION
        val density = Particle.densityForNetGravity(
            deceleration, physicsSettingsService.gravity, physicsSettingsService.mediumDensity
        )
        val delayOrder = MutableList(FIREWORK_TAIL_PARTICLE_COUNT) { it }
        shuffle(delayOrder)
        val viewPortWidth = viewPort.width.toDouble()
        val viewPortHeight = viewPort.height.toDouble()
        val originY = viewPort.y + viewPortHeight
        val destinationBandStart = viewPortHeight * FIREWORK_TAIL_DESTINATION_BAND_START_RATIO
        val destinationBandSize = viewPortHeight * FIREWORK_TAIL_DESTINATION_BAND_SIZE_RATIO
        val averageDistance = originY - (viewPort.y + destinationBandStart + destinationBandSize / 2.0)
        val drag = max(physicsSettingsService.drag, FIREWORK_TAIL_MINIMUM_DRAG)
        val maximumLaunchVelocity = physicsSettingsService.maxParticleSpeed
        val averageLaunchVelocity = min(launchVelocityForApex(averageDistance, deceleration, drag), maximumLaunchVelocity)
        val averageTimeToApex = ticksToApex(averageLaunchVelocity, deceleration, drag)
        val staggerFrames = averageTimeToApex * FIREWORK_TAIL_STAGGER_TIMING_RATIO
        repeat(FIREWORK_TAIL_PARTICLE_COUNT) { particleIndex ->
            val (startColor, endColor) = createDistinctColorPair()
            val xPosition = viewPort.x + (particleIndex.toDouble() / (FIREWORK_TAIL_PARTICLE_COUNT - 1)) * viewPortWidth
            val randomDestinationY = viewPort.y + destinationBandStart + Random.nextDouble() * destinationBandSize
            val distanceToDestination = originY - randomDestinationY
            val launchVelocity = min(launchVelocityForApex(distanceToDestination, deceleration, drag), maximumLaunchVelocity)
            val timeToDestination = ticksToApex(launchVelocity, deceleration, drag) + FIREWORK_TAIL_POST_APEX_TICKS
            val radius = FIREWORK_TAIL_MIN_RADIUS + Random.nextDouble() * FIREWORK_TAIL_RADIUS_RANGE
            val delay = max(
                0.0,
                round(delayOrder[particleIndex] * staggerFrames + (Random.nextDouble() - 0.5) * FIREWORK_TAIL_DELAY_JITTER_RANGE)
            )
            val dimensions = CircleDimensions(radius = radius)
            val particleId = nextParticleId()
            particles += Particle(
                lifecycle = ParticleLifecycle(delay = delay, lifetime = timeToDestination),
                appearance = ParticleAppearance(color = startColor, endColor = endColor),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(
                        xPosition, originY, Particle.isolatedPlaneZ(particleId, physicsSettingsService.collisionPlaneThickness)
                    ),
                    origin = ParticleVector(xPosition, originY)
                ),
                dynamics = ParticleDynamics(velocity = ParticleVector(0.0, -launchVelocity)),
                material = ParticleMaterial(density = density),
                id = particleId
            )
        }
        return particles
    }

    private fun ticksToApex(launchVelocity: Double, deceleration: Double, drag: Double): Double {
        return ln(1.0 + drag * launchVelocity / deceleration) / drag
    }

    private fun apexDistance(launchVelocity: Double, deceleration: Double, drag: Double): Double {
        return launchVelocity / drag - (deceleration / (drag * drag)) * ln(1.0 + drag * launchVelocity / deceleration)
    }

    private fun launchVelocityForApex(distance: Double, deceleration: Double, drag: Double): Double {
        var lowerVelocity = 0.0
        var upperVelocity = sqrt(2.0 * deceleration * distance)
        while (apexDistance(upperVelocity, deceleration, drag) < distance) {
            upperVelocity *= 2.0
        }
        repeat(FIREWORK_TAIL_SOLVER_ITERATIONS) {
            val middleVelocity = (lowerVelocity + upperVelocity) / 2.0
            if (apexDistance(middleVelocity, deceleration, drag) < distance) {
                lowerVelocity = middleVelocity
            } else {
                upperVelocity = middleVelocity
            }
        }
        return (lowerVelocity + upperVelocity) / 2.0
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = fireworkTailGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = FIREWORK_TAIL_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex] = physicsSettingsService.maxParticleSpeed.toFloat()
        targetBuffer[parameterIndex + 1] = particle.lifecycle.delay.toFloat()
    }
}
