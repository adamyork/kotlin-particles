package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import kotlin.math.*
import kotlin.random.Random
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
        private const val FIREWORK_BURST_MIN_SPEED: Double = 3.6
        private const val FIREWORK_BURST_SPEED_RANGE: Double = 1.5
        private const val FIREWORK_BURST_ANGLE_JITTER: Double = 0.2
        private const val FIREWORK_BURST_RADIUS: Double = 3.0
        private const val FIREWORK_BURST_LIFETIME: Double = 120.0
        private const val FIREWORK_BURST_NET_GRAVITY_FRACTION: Double = 0.01
        private const val FIREWORK_BURST_Y_ACCELERATION: Double = 0.03
        private const val FIREWORK_BURST_MAX_ACTIVE_PARTICLES: Int = 512
        private const val FIREWORK_BURST_GPU_KIND: Float = 5f
    }

    private val fireworkBurstGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(FIREWORK_BURST_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(fireworkBurstGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val particles = mutableListOf<Particle>()
        val (startColor, endColor) = createDistinctColorPair()
        repeat(FIREWORK_BURST_PARTICLE_COUNT) { burstIndex ->
            val angle = (burstIndex.toDouble() / FIREWORK_BURST_PARTICLE_COUNT) * PI * 2 +
                (Random.nextDouble() - 0.5) * FIREWORK_BURST_ANGLE_JITTER
            val speed = Random.nextDouble() * FIREWORK_BURST_SPEED_RANGE + FIREWORK_BURST_MIN_SPEED
            val dimensions = CircleDimensions(radius = FIREWORK_BURST_RADIUS)
            val particleId = nextParticleId()
            val density = Particle.densityForNetGravity(
                physicsSettingsService.gravity * FIREWORK_BURST_NET_GRAVITY_FRACTION,
                physicsSettingsService.gravity,
                physicsSettingsService.mediumDensity
            )
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
                    thrust = ParticleVector(0.0, FIREWORK_BURST_Y_ACCELERATION * density * dimensions.area())
                ),
                material = ParticleMaterial(density = density),
                id = particleId
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = fireworkBurstGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = FIREWORK_BURST_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        val endColorRed = (particle.appearance.endColor.red * 255f).roundToInt().coerceIn(0, 255)
        val endColorGreen = (particle.appearance.endColor.green * 255f).roundToInt().coerceIn(0, 255)
        val endColorBlue = (particle.appearance.endColor.blue * 255f).roundToInt().coerceIn(0, 255)
        targetBuffer[parameterIndex] = ((endColorRed * 65536) + (endColorGreen * 256) + endColorBlue).toFloat()
    }
}
