package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.PhysicsSettingsService
import kotlin.math.*
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.engine.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class FireworkGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffects.FIREWORK) {

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
        private const val FIREWORK_BURST_PARTICLE_COUNT: Int = 12
        private const val FIREWORK_BURST_REACH: Double = 280.0
        private const val FIREWORK_BURST_MIN_REACH_FRACTION: Double = 0.7
        private const val FIREWORK_BURST_REACH_FRACTION_RANGE: Double = 0.3
        private const val FIREWORK_BURST_DRAG_LENGTHS: Double = 1.2
        private const val FIREWORK_BURST_ANGLE_JITTER: Double = 0.2
        private const val FIREWORK_BURST_RADIUS: Double = 3.0
        private const val FIREWORK_BURST_LIFETIME: Double = 120.0
        private const val FIREWORK_BURST_FALL_GRAVITY_FRACTION: Double = 0.15
        private const val FIREWORK_MAX_ACTIVE_PARTICLES: Int = 8192
    }

    private val fireworkGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(FIREWORK_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(fireworkGpuSlotGroup)

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
            particles += createBurst(xPosition, randomDestinationY, delay + FIREWORK_TAIL_FLIGHT_TICKS)
        }
        return particles
    }

    private fun createBurst(centerX: Double, centerY: Double, burstDelay: Double): List<Particle> {
        val particles = mutableListOf<Particle>()
        val (startColor, endColor) = createDistinctColorPair()
        val mediumDensity = physicsSettingsService.mediumDensity
        val gravity = physicsSettingsService.gravity
        val linearDrag = max(physicsSettingsService.drag, Double.MIN_VALUE)
        val referenceDimensions = CircleDimensions(radius = FIREWORK_BURST_RADIUS)
        val frontalLength = (referenceDimensions.boundingWidth() + referenceDimensions.boundingHeight()) / 2.0
        val targetDragRate = FIREWORK_BURST_DRAG_LENGTHS / FIREWORK_BURST_REACH
        val density = max(
            mediumDensity,
            0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength / (referenceDimensions.area() * targetDragRate)
        )
        val quadraticDragRate = 0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength /
            (density * referenceDimensions.area())
        val reachDivisor = max(quadraticDragRate, Double.MIN_VALUE) * (1.0 - exp(-linearDrag * FIREWORK_BURST_LIFETIME))
        val holdThrustY = density * referenceDimensions.area() *
            (gravity * FIREWORK_BURST_FALL_GRAVITY_FRACTION - Particle.netGravityOf(density, gravity, mediumDensity))
        repeat(FIREWORK_BURST_PARTICLE_COUNT) { burstIndex ->
            val angle = (burstIndex.toDouble() / FIREWORK_BURST_PARTICLE_COUNT) * PI * 2 +
                (random.nextDouble() - 0.5) * FIREWORK_BURST_ANGLE_JITTER
            val reachFraction = FIREWORK_BURST_MIN_REACH_FRACTION + random.nextDouble() * FIREWORK_BURST_REACH_FRACTION_RANGE
            val speed = linearDrag * expm1(quadraticDragRate * FIREWORK_BURST_REACH * reachFraction) / reachDivisor
            val particleId = nextParticleId()
            particles += Particle(
                lifecycle = ParticleLifecycle(delay = burstDelay, lifetime = FIREWORK_BURST_LIFETIME),
                appearance = ParticleAppearance(color = startColor, endColor = endColor),
                dimensions = CircleDimensions(radius = FIREWORK_BURST_RADIUS),
                transform = ParticleTransform(
                    position = ParticleVector(
                        centerX, centerY, Particle.isolatedPlaneZ(particleId, physicsSettingsService.collisionPlaneThickness)
                    )
                ),
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed),
                    thrust = ParticleVector(0.0, holdThrustY)
                ),
                material = ParticleMaterial(density = density),
                id = particleId
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = fireworkGpuSlotGroup
}
