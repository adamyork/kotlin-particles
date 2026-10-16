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
class SpinnersGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.SPINNERS) {

    private companion object {
        private const val SPINNERS_PARTICLE_COUNT: Int = 4
        private const val SPINNERS_MIN_SIZE: Double = 40.0
        private const val SPINNERS_SIZE_RANGE: Double = 60.0
        private const val SPINNERS_LIFETIME: Double = 7200.0
        private const val SPINNERS_MIN_SPEED: Double = 1.5
        private const val SPINNERS_SPEED_RANGE: Double = 3.0
        private const val SPINNERS_MIN_SPIN: Double = 0.02
        private const val SPINNERS_SPIN_RANGE: Double = 0.06
        private const val SPINNERS_NET_GRAVITY_FRACTION: Double = 0.02
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
        repeat(SPINNERS_PARTICLE_COUNT) {
            val size = SPINNERS_MIN_SIZE + Random.nextDouble() * SPINNERS_SIZE_RANGE
            val angle = Random.nextDouble() * PI * 2
            val speed = SPINNERS_MIN_SPEED + Random.nextDouble() * SPINNERS_SPEED_RANGE
            val spinDirection = if (Random.nextBoolean()) 1.0 else -1.0
            val startX = viewPort.x + size + Random.nextDouble() * max(viewPort.width - size * 2.0, 1.0)
            val startY = viewPort.y + size + Random.nextDouble() * max(viewPort.height - size * 2.0, 1.0)
            particles += Particle(
                lifecycle = ParticleLifecycle(lifetime = SPINNERS_LIFETIME),
                appearance = ParticleAppearance(color = randomColor()),
                dimensions = RectDimensions(width = size, height = size),
                transform = ParticleTransform(
                    position = ParticleVector(startX, startY, Particle.BOUNDED_Z),
                    origin = ParticleVector(startX, startY),
                    angle = Random.nextDouble() * PI * 2
                ),
                material = ParticleMaterial(
                    density = Particle.densityForNetGravity(
                        physicsSettingsService.gravity * SPINNERS_NET_GRAVITY_FRACTION,
                        physicsSettingsService.gravity,
                        physicsSettingsService.mediumDensity
                    ),
                    restitution = SPINNERS_RESTITUTION,
                    friction = SPINNERS_FRICTION,
                    staticFriction = SPINNERS_STATIC_FRICTION,
                    rollingResistance = SPINNERS_ROLLING_RESISTANCE
                ),
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed),
                    angularVelocity = spinDirection * (SPINNERS_MIN_SPIN + Random.nextDouble() * SPINNERS_SPIN_RANGE)
                ),
                id = nextParticleId()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = spinnersGpuSlotGroup
}
