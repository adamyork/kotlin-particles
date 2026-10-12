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
        private const val FIREWORK_TAIL_MASS: Double = 0.02
        private const val FIREWORK_TAIL_DESTINATION_BAND_START_RATIO: Double = 0.15
        private const val FIREWORK_TAIL_DESTINATION_BAND_SIZE_RATIO: Double = 0.2
        private const val FIREWORK_TAIL_STAGGER_TIMING_RATIO: Double = 0.5
        private const val FIREWORK_TAIL_DELAY_JITTER_RANGE: Double = 20.0
        private const val FIREWORK_TAIL_MIN_RADIUS: Double = 8.0
        private const val FIREWORK_TAIL_RADIUS_RANGE: Double = 8.0
        private const val FIREWORK_TAIL_VELOCITY_HEADROOM: Double = 1.2
        private const val FIREWORK_TAIL_MAX_ACTIVE_PARTICLES: Int = 1024
        private const val FIREWORK_TAIL_GPU_KIND: Float = 4f
    }

    private val fireworkTailGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(FIREWORK_TAIL_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(fireworkTailGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val deceleration = physicsSettingsService.gravity * FIREWORK_TAIL_MASS
        val delayOrder = MutableList(FIREWORK_TAIL_PARTICLE_COUNT) { it }
        shuffle(delayOrder)
        val viewPortWidth = viewPort.width.toDouble()
        val viewPortHeight = viewPort.height.toDouble()
        val originY = viewPort.y + viewPortHeight
        val destinationBandStart = viewPortHeight * FIREWORK_TAIL_DESTINATION_BAND_START_RATIO
        val destinationBandSize = viewPortHeight * FIREWORK_TAIL_DESTINATION_BAND_SIZE_RATIO
        val averageDistance = originY - (viewPort.y + destinationBandStart + destinationBandSize / 2.0)
        val averageLaunchVelocity = sqrt(2 * deceleration * averageDistance)
        val averageTimeToApex = averageLaunchVelocity / deceleration
        val staggerFrames = averageTimeToApex * FIREWORK_TAIL_STAGGER_TIMING_RATIO
        repeat(FIREWORK_TAIL_PARTICLE_COUNT) { particleIndex ->
            val (startColor, endColor) = createDistinctColorPair()
            val xPosition = viewPort.x + (particleIndex.toDouble() / (FIREWORK_TAIL_PARTICLE_COUNT - 1)) * viewPortWidth
            val randomDestinationY = viewPort.y + destinationBandStart + Random.nextDouble() * destinationBandSize
            val distanceToDestination = originY - randomDestinationY
            val launchVelocity = sqrt(2 * deceleration * distanceToDestination)
            val timeToDestination = launchVelocity / deceleration
            val radius = FIREWORK_TAIL_MIN_RADIUS + Random.nextDouble() * FIREWORK_TAIL_RADIUS_RANGE
            val diameter = radius * 2.0
            val delay = max(
                0.0,
                round(delayOrder[particleIndex] * staggerFrames + (Random.nextDouble() - 0.5) * FIREWORK_TAIL_DELAY_JITTER_RANGE)
            )
            particles += Particle(
                effect = ParticleEffect.FIREWORK_TAIL,
                shape = ParticleShape.CIRCLE,
                delay = delay,
                lifetime = timeToDestination,
                color = startColor,
                endColor = endColor,
                width = diameter,
                height = diameter,
                radius = radius,
                x = xPosition,
                y = originY,
                originX = xPosition.roundToInt(),
                originY = originY.roundToInt(),
                destinationX = xPosition.roundToInt(),
                destinationY = randomDestinationY.roundToInt(),
                yVelocity = -launchVelocity,
                maxYVelocity = launchVelocity * FIREWORK_TAIL_VELOCITY_HEADROOM,
                mass = FIREWORK_TAIL_MASS,
                gpuReservedSlot = fireworkTailGpuSlotGroup.claimNextSlot()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = fireworkTailGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = FIREWORK_TAIL_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex] = particle.maxYVelocity.toFloat()
        targetBuffer[parameterIndex + 1] = particle.delay.toFloat()
    }
}
