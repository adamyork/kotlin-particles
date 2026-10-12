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
class GobblerGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.GOBBLER) {

    private companion object {
        private const val GOBBLER_PARTICLE_COUNT: Int = 16
        private const val GOBBLER_MIN_SIZE: Double = 8.0
        private const val GOBBLER_SIZE_RANGE: Double = 24.0
        private const val GOBBLER_LIFETIME: Double = 1024.0
        private const val GOBBLER_MIN_SPEED: Double = 0.8
        private const val GOBBLER_SPEED_RANGE: Double = 2.4
        private const val GOBBLER_RESTITUTION: Double = 1.0
        private const val GOBBLER_DRAG: Double = 0.002
        private const val GOBBLER_MAX_VELOCITY: Double = 6.0
        private const val GOBBLER_MASS_PER_RADIUS_SQUARED: Double = 1.0 / 20480.0
        private const val GOBBLER_ATTRACTION_PER_MASS: Double = 480.0
        private const val GOBBLER_MAX_ACTIVE_PARTICLES: Int = 64
        private const val GOBBLER_GPU_KIND: Float = 8f
    }

    private val gobblerGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(GOBBLER_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(gobblerGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        repeat(GOBBLER_PARTICLE_COUNT) {
            val angle = Random.nextDouble() * PI * 2
            val speed = GOBBLER_MIN_SPEED + Random.nextDouble() * GOBBLER_SPEED_RANGE
            val startX = viewPort.x + Random.nextDouble() * viewPort.width
            val startY = viewPort.y + Random.nextDouble() * viewPort.height
            val destinationX = viewPort.x + Random.nextDouble() * viewPort.width
            val destinationY = viewPort.y + Random.nextDouble() * viewPort.height
            val radius = GOBBLER_MIN_SIZE + Random.nextDouble() * GOBBLER_SIZE_RANGE
            val diameter = radius * 2.0
            val mass = radius * radius * GOBBLER_MASS_PER_RADIUS_SQUARED
            val attraction = mass * GOBBLER_ATTRACTION_PER_MASS
            particles += Particle(
                effect = ParticleEffect.GOBBLER,
                shape = ParticleShape.CIRCLE,
                lifetime = GOBBLER_LIFETIME,
                color = randomColor(),
                width = diameter,
                height = diameter,
                radius = radius,
                x = startX,
                y = startY,
                originX = startX.roundToInt(),
                originY = startY.roundToInt(),
                destinationX = destinationX.roundToInt(),
                destinationY = destinationY.roundToInt(),
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                maxXVelocity = GOBBLER_MAX_VELOCITY,
                maxYVelocity = GOBBLER_MAX_VELOCITY,
                maxZVelocity = GOBBLER_MAX_VELOCITY,
                drag = GOBBLER_DRAG,
                mass = mass,
                restitution = GOBBLER_RESTITUTION,
                attraction = attraction,
                canCollide = true,
                viewportBound = true,
                yForce = -physicsSettingsService.gravity * min(1.0, mass),
                gpuReservedSlot = gobblerGpuSlotGroup.claimNextSlot()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = gobblerGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = GOBBLER_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex + 2] = particle.mass.toFloat()
        targetBuffer[parameterIndex + 3] = particle.attraction.toFloat()
    }
}
