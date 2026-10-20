package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import kotlin.math.*
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class FireworkBurstGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.FIREWORK_BURST) {

    private companion object {
        private const val FIREWORK_BURST_PARTICLE_COUNT: Int = 100
        private const val FIREWORK_BURST_REACH: Double = 280.0
        private const val FIREWORK_BURST_MIN_REACH_FRACTION: Double = 0.7
        private const val FIREWORK_BURST_REACH_FRACTION_RANGE: Double = 0.3
        private const val FIREWORK_BURST_DRAG_LENGTHS: Double = 1.2
        private const val FIREWORK_BURST_ANGLE_JITTER: Double = 0.2
        private const val FIREWORK_BURST_RADIUS: Double = 3.0
        private const val FIREWORK_BURST_LIFETIME: Double = 120.0
        private const val FIREWORK_BURST_FALL_GRAVITY_FRACTION: Double = 0.15
        private const val FIREWORK_BURST_MAX_ACTIVE_PARTICLES: Int = 512
    }

    private val fireworkBurstGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(FIREWORK_BURST_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(fireworkBurstGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
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
            val dimensions = CircleDimensions(radius = FIREWORK_BURST_RADIUS)
            val particleId = nextParticleId()
            particles += Particle(
                lifecycle = ParticleLifecycle(lifetime = FIREWORK_BURST_LIFETIME),
                appearance = ParticleAppearance(color = startColor, endColor = endColor),
                dimensions = dimensions,
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

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = fireworkBurstGpuSlotGroup
}
