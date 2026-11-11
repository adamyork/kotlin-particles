package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import kotlin.math.*
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.engine.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class SpinnersGenerator(
    private val physicsSettingsService: ParticlePhysicsSettingsService
) : AbstractGenerator(ParticleEffects.SPINNERS) {

    private companion object {
        private const val SPINNERS_PARTICLE_COUNT: Int = 4
        private const val SPINNERS_MIN_SIZE: Double = 40.0
        private const val SPINNERS_SIZE_RANGE: Double = 60.0
        private const val SPINNERS_LIFETIME: Double = 7200.0
        private const val SPINNERS_MIN_SPEED: Double = 6.0
        private const val SPINNERS_SPEED_RANGE: Double = 8.0
        private const val SPINNERS_MIN_SPIN: Double = 0.02
        private const val SPINNERS_SPIN_RANGE: Double = 0.06
        private const val SPINNERS_FAST_SPIN: Double = 0.45
        private const val SPINNERS_FALL_GRAVITY_FRACTION: Double = 0.25
        private const val SPINNERS_DRAG_REACH: Double = 4000.0
        private const val SPINNERS_PLANE_SPAN_RATIO: Double = 0.999
        private const val SPINNERS_RESTITUTION: Double = 0.8
        private const val SPINNERS_FRICTION: Double = 0.5
        private const val SPINNERS_STATIC_FRICTION: Double = 0.7
        private const val SPINNERS_ROLLING_RESISTANCE: Double = 0.03
        private const val SPINNERS_MAX_ACTIVE_PARTICLES: Int = 16
    }

    private val spinnersGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(SPINNERS_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(spinnersGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val mediumDensity = physicsSettingsService.mediumDensity
        val gravity = physicsSettingsService.gravity
        val targetDragRate = 1.0 / SPINNERS_DRAG_REACH
        val planeSpacing = physicsSettingsService.collisionPlaneThickness * SPINNERS_PLANE_SPAN_RATIO /
            max(SPINNERS_PARTICLE_COUNT - 1, 1)
        repeat(SPINNERS_PARTICLE_COUNT) { spinnerIndex ->
            val size = SPINNERS_MIN_SIZE + random.nextDouble() * SPINNERS_SIZE_RANGE
            val angle = random.nextDouble() * PI * 2
            val speed = SPINNERS_MIN_SPEED + random.nextDouble() * SPINNERS_SPEED_RANGE
            val spinDirection = if (random.nextBoolean()) 1.0 else -1.0
            val startX = viewPort.x + size + random.nextDouble() * max(viewPort.width - size * 2.0, 1.0)
            val startY = viewPort.y + size + random.nextDouble() * max(viewPort.height - size * 2.0, 1.0)
            val dimensions = ParticleRectDimensions(width = size, height = size)
            val density = max(
                mediumDensity,
                0.5 * mediumDensity * physicsSettingsService.dragCoefficient * size / (dimensions.area() * targetDragRate)
            )
            val holdThrustY = density * dimensions.area() *
                (gravity * SPINNERS_FALL_GRAVITY_FRACTION - Particle.netGravityOf(density, gravity, mediumDensity))
            particles += Particle(
                lifecycle = ParticleLifecycle(lifetime = SPINNERS_LIFETIME),
                appearance = ParticleAppearance(color = randomColor()),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(startX, startY, Particle.BOUNDED_Z + spinnerIndex * planeSpacing),
                    origin = ParticleVector(startX, startY),
                    angle = random.nextDouble() * PI * 2
                ),
                material = ParticleMaterial(
                    density = density,
                    restitution = SPINNERS_RESTITUTION,
                    friction = SPINNERS_FRICTION,
                    staticFriction = SPINNERS_STATIC_FRICTION,
                    rollingResistance = SPINNERS_ROLLING_RESISTANCE
                ),
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed),
                    angularVelocity = spinDirection * if (spinnerIndex == 0) {
                        SPINNERS_FAST_SPIN
                    } else {
                        SPINNERS_MIN_SPIN + random.nextDouble() * SPINNERS_SPIN_RANGE
                    },
                    thrust = ParticleVector(0.0, holdThrustY)
                ),
                id = nextParticleId()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = spinnersGpuSlotGroup
}
