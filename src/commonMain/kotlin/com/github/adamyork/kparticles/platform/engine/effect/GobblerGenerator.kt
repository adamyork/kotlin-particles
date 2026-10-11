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
class GobblerGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : BaseGenerator(ParticleEffect.GOBBLER) {

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
    }

    private var nextGobblerGpuSlot: Int = 0

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
            val color = randomColor()
            particles += Particle(
                id = Random.nextInt().toString(16),
                effect = ParticleEffect.GOBBLER,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = GOBBLER_LIFETIME,
                color = color,
                startColor = color,
                endColor = color,
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
                x = startX,
                y = startY,
                z = 0.0,
                originX = startX.roundToInt(),
                originY = startY.roundToInt(),
                originZ = 0,
                destinationX = destinationX.roundToInt(),
                destinationY = destinationY.roundToInt(),
                destinationZ = 0,
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                zVelocity = 0.0,
                maxXVelocity = GOBBLER_MAX_VELOCITY,
                maxYVelocity = GOBBLER_MAX_VELOCITY,
                maxZVelocity = GOBBLER_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = GOBBLER_DRAG,
                mass = mass,
                restitution = GOBBLER_RESTITUTION,
                attraction = attraction,
                canCollide = true,
                visible = true,
                viewportBound = true,
                xForce = 0.0,
                yForce = -physicsSettingsService.gravity * min(1.0, mass),
                gpuReservedSlot = nextGobblerGpuSlot
            )
            nextGobblerGpuSlot = (nextGobblerGpuSlot + 1) % MAX_ACTIVE_GOBBLER_PARTICLES
        }
        return particles
    }
}
