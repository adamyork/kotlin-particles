package com.github.adamyork.kparticles.platform.effects

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import com.github.adamyork.particles.engine.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class CollidingBitsGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffects.COLLIDING_BITS) {

    private companion object {
        private const val COLLIDING_BITS_PARTICLE_COUNT: Int = 100
        private const val COLLIDING_BITS_MIN_SIZE: Double = 8.0
        private const val COLLIDING_BITS_SIZE_RANGE: Double = 24.0
        private const val COLLIDING_BITS_MIN_LIFETIME: Double = 128.0
        private const val COLLIDING_BITS_LIFETIME_RANGE: Double = 384.0
        private const val COLLIDING_BITS_MIN_SPEED: Double = 2.0
        private const val COLLIDING_BITS_SPEED_RANGE: Double = 6.0
        private const val COLLIDING_BITS_FALL_GRAVITY_FRACTION: Double = 0.08
        private const val COLLIDING_BITS_DRAG_REACH: Double = 4000.0
        private const val COLLIDING_BITS_PLANE_SPAN_RATIO: Double = 0.999
        private const val COLLIDING_BITS_RESTITUTION: Double = 0.92
        private const val COLLIDING_BITS_MAX_ACTIVE_PARTICLES: Int = 512
    }

    private val collidingBitsGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(COLLIDING_BITS_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(collidingBitsGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val mediumDensity = physicsSettingsService.mediumDensity
        val gravity = physicsSettingsService.gravity
        val targetDragRate = 1.0 / COLLIDING_BITS_DRAG_REACH
        val planeSpacing = physicsSettingsService.collisionPlaneThickness * COLLIDING_BITS_PLANE_SPAN_RATIO /
            (COLLIDING_BITS_PARTICLE_COUNT - 1)
        repeat(COLLIDING_BITS_PARTICLE_COUNT) { bitIndex ->
            val isCircle = random.nextBoolean()
            val angle = random.nextDouble() * PI * 2
            val speed = COLLIDING_BITS_MIN_SPEED + random.nextDouble() * COLLIDING_BITS_SPEED_RANGE
            val lifetime = COLLIDING_BITS_MIN_LIFETIME + random.nextDouble() * COLLIDING_BITS_LIFETIME_RANGE
            val startX = viewPort.x + random.nextDouble() * viewPort.width
            val startY = viewPort.y + random.nextDouble() * viewPort.height
            val dimensions: ParticleDimensions = if (isCircle) {
                CircleDimensions(radius = COLLIDING_BITS_MIN_SIZE + random.nextDouble() * COLLIDING_BITS_SIZE_RANGE)
            } else {
                ParticleRectDimensions(
                    width = COLLIDING_BITS_MIN_SIZE + random.nextDouble() * COLLIDING_BITS_SIZE_RANGE,
                    height = COLLIDING_BITS_MIN_SIZE + random.nextDouble() * COLLIDING_BITS_SIZE_RANGE
                )
            }
            val frontalLength = (dimensions.boundingWidth() + dimensions.boundingHeight()) / 2.0
            val density = max(
                mediumDensity,
                0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength / (dimensions.area() * targetDragRate)
            )
            val holdThrustY = density * dimensions.area() *
                (gravity * COLLIDING_BITS_FALL_GRAVITY_FRACTION - Particle.netGravityOf(density, gravity, mediumDensity))
            particles += Particle(
                lifecycle = ParticleLifecycle(lifetime = lifetime),
                appearance = ParticleAppearance(color = Color.White),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(startX, startY, Particle.BOUNDED_Z + bitIndex * planeSpacing),
                    origin = ParticleVector(startX, startY)
                ),
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed),
                    thrust = ParticleVector(0.0, holdThrustY)
                ),
                material = ParticleMaterial(density = density, restitution = COLLIDING_BITS_RESTITUTION),
                id = nextParticleId()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = collidingBitsGpuSlotGroup
}
