package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
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
) : BaseGenerator(ParticleEffect.FIREWORK_TAIL) {

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
    }

    private var nextFireworkTailGpuSlot: Int = 0

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
                id = Random.nextInt().toString(16),
                effect = ParticleEffect.FIREWORK_TAIL,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = delay,
                lifetime = timeToDestination,
                color = startColor,
                startColor = startColor,
                endColor = endColor,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = radius,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = xPosition,
                y = originY,
                z = 0.0,
                originX = xPosition.roundToInt(),
                originY = originY.roundToInt(),
                originZ = 0,
                destinationX = xPosition.roundToInt(),
                destinationY = randomDestinationY.roundToInt(),
                destinationZ = 0,
                xVelocity = 0.0,
                yVelocity = -launchVelocity,
                zVelocity = 0.0,
                maxXVelocity = DEFAULT_MAX_VELOCITY,
                maxYVelocity = launchVelocity * FIREWORK_TAIL_VELOCITY_HEADROOM,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = 0.0,
                mass = FIREWORK_TAIL_MASS,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false,
                gpuReservedSlot = nextFireworkTailGpuSlot
            )
            nextFireworkTailGpuSlot = (nextFireworkTailGpuSlot + 1) % MAX_ACTIVE_FIREWORK_TAIL_PARTICLES
        }

        return particles
    }
}
