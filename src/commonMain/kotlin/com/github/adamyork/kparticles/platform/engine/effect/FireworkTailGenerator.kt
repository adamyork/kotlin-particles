package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.*
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
        private const val FIREWORK_TAIL_FLIGHT_TICKS: Double = 90.0
        private const val FIREWORK_TAIL_DRAG_LENGTHS: Double = 1.2
        private const val FIREWORK_TAIL_FALL_GRAVITY_FRACTION: Double = 0.0
        private const val FIREWORK_TAIL_DESTINATION_BAND_START_RATIO: Double = 0.15
        private const val FIREWORK_TAIL_DESTINATION_BAND_SIZE_RATIO: Double = 0.2
        private const val FIREWORK_TAIL_STAGGER_TIMING_RATIO: Double = 0.5
        private const val FIREWORK_TAIL_DELAY_JITTER_RANGE: Double = 20.0
        private const val FIREWORK_TAIL_MIN_RADIUS: Double = 8.0
        private const val FIREWORK_TAIL_RADIUS_RANGE: Double = 8.0
        private const val FIREWORK_TAIL_MAX_ACTIVE_PARTICLES: Int = 1024
    }

    private val fireworkTailGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(FIREWORK_TAIL_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(fireworkTailGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val mediumDensity = physicsSettingsService.mediumDensity
        val gravity = physicsSettingsService.gravity
        val linearDrag = max(physicsSettingsService.drag, Double.MIN_VALUE)
        val delayOrder = MutableList(FIREWORK_TAIL_PARTICLE_COUNT) { it }
        shuffle(delayOrder)
        val viewPortWidth = viewPort.width.toDouble()
        val viewPortHeight = viewPort.height.toDouble()
        val originY = viewPort.y + viewPortHeight
        val destinationBandStart = viewPortHeight * FIREWORK_TAIL_DESTINATION_BAND_START_RATIO
        val destinationBandSize = viewPortHeight * FIREWORK_TAIL_DESTINATION_BAND_SIZE_RATIO
        val averageDistance = originY - (viewPort.y + destinationBandStart + destinationBandSize / 2.0)
        val targetDragRate = FIREWORK_TAIL_DRAG_LENGTHS / averageDistance
        val staggerFrames = FIREWORK_TAIL_FLIGHT_TICKS * FIREWORK_TAIL_STAGGER_TIMING_RATIO
        repeat(FIREWORK_TAIL_PARTICLE_COUNT) { particleIndex ->
            val (startColor, endColor) = createDistinctColorPair()
            val xPosition = viewPort.x + (particleIndex.toDouble() / (FIREWORK_TAIL_PARTICLE_COUNT - 1)) * viewPortWidth
            val randomDestinationY = viewPort.y + destinationBandStart + random.nextDouble() * destinationBandSize
            val distanceToDestination = originY - randomDestinationY
            val radius = FIREWORK_TAIL_MIN_RADIUS + random.nextDouble() * FIREWORK_TAIL_RADIUS_RANGE
            val dimensions = CircleDimensions(radius = radius)
            val frontalLength = (dimensions.boundingWidth() + dimensions.boundingHeight()) / 2.0
            val density = max(
                mediumDensity,
                0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength / (dimensions.area() * targetDragRate)
            )
            val quadraticDragRate = 0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength /
                (density * dimensions.area())
            val reachDivisor = max(quadraticDragRate, Double.MIN_VALUE) * (1.0 - exp(-linearDrag * FIREWORK_TAIL_FLIGHT_TICKS))
            val launchVelocity = linearDrag * expm1(quadraticDragRate * distanceToDestination) / reachDivisor
            val holdThrustY = density * dimensions.area() *
                (gravity * FIREWORK_TAIL_FALL_GRAVITY_FRACTION - Particle.netGravityOf(density, gravity, mediumDensity))
            val delay = max(
                0.0,
                round(delayOrder[particleIndex] * staggerFrames + (random.nextDouble() - 0.5) * FIREWORK_TAIL_DELAY_JITTER_RANGE)
            )
            val particleId = nextParticleId()
            particles += Particle(
                lifecycle = ParticleLifecycle(delay = delay, lifetime = FIREWORK_TAIL_FLIGHT_TICKS),
                appearance = ParticleAppearance(color = startColor, endColor = endColor),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(
                        xPosition, originY, Particle.isolatedPlaneZ(particleId, physicsSettingsService.collisionPlaneThickness)
                    ),
                    origin = ParticleVector(xPosition, originY)
                ),
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(0.0, -launchVelocity),
                    thrust = ParticleVector(0.0, holdThrustY)
                ),
                material = ParticleMaterial(density = density),
                id = particleId
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = fireworkTailGpuSlotGroup
}
