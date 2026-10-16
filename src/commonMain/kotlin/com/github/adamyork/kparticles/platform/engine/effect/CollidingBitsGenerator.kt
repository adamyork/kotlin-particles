package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CollidingBitsGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.COLLIDING_BITS) {

    private companion object {
        private const val COLLIDING_BITS_PARTICLE_COUNT: Int = 100
        private const val COLLIDING_BITS_MIN_SIZE: Double = 8.0
        private const val COLLIDING_BITS_SIZE_RANGE: Double = 24.0
        private const val COLLIDING_BITS_MIN_LIFETIME: Double = 128.0
        private const val COLLIDING_BITS_LIFETIME_RANGE: Double = 384.0
        private const val COLLIDING_BITS_MIN_SPEED: Double = 2.0
        private const val COLLIDING_BITS_SPEED_RANGE: Double = 6.0
        private const val COLLIDING_BITS_NET_GRAVITY_FRACTION: Double = 0.12
        private const val COLLIDING_BITS_RESTITUTION: Double = 0.6
        private const val COLLIDING_BITS_MAX_ACTIVE_PARTICLES: Int = 512
        private const val COLLIDING_BITS_GPU_KIND: Float = 7f
    }

    private val collidingBitsGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(COLLIDING_BITS_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(collidingBitsGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        repeat(COLLIDING_BITS_PARTICLE_COUNT) {
            val isCircle = Random.nextBoolean()
            val angle = Random.nextDouble() * PI * 2
            val speed = COLLIDING_BITS_MIN_SPEED + Random.nextDouble() * COLLIDING_BITS_SPEED_RANGE
            val lifetime = COLLIDING_BITS_MIN_LIFETIME + Random.nextDouble() * COLLIDING_BITS_LIFETIME_RANGE
            val startX = viewPort.x + Random.nextDouble() * viewPort.width
            val startY = viewPort.y + Random.nextDouble() * viewPort.height
            val dimensions: ParticleDimensions = if (isCircle) {
                CircleDimensions(radius = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE)
            } else {
                RectDimensions(
                    width = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE,
                    height = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE
                )
            }
            particles += Particle(
                lifecycle = ParticleLifecycle(lifetime = lifetime),
                appearance = ParticleAppearance(color = Color.White),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(startX, startY, Particle.BOUNDED_Z),
                    origin = ParticleVector(startX, startY)
                ),
                dynamics = ParticleDynamics(velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed)),
                material = ParticleMaterial(
                    density = Particle.densityForNetGravity(
                        physicsSettingsService.gravity * COLLIDING_BITS_NET_GRAVITY_FRACTION,
                        physicsSettingsService.gravity,
                        physicsSettingsService.mediumDensity
                    ),
                    restitution = COLLIDING_BITS_RESTITUTION
                ),
                id = nextParticleId()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = collidingBitsGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = COLLIDING_BITS_GPU_KIND
}
