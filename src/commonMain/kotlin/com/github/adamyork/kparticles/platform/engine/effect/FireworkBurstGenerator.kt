package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import kotlin.math.*
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class FireworkBurstGenerator : BaseGenerator(ParticleEffect.FIREWORK_BURST) {

    private companion object {
        private const val FIREWORK_BURST_PARTICLE_COUNT: Int = 100
        private const val FIREWORK_BURST_MIN_SPEED: Double = 2.0
        private const val FIREWORK_BURST_SPEED_RANGE: Double = 6.0
        private const val FIREWORK_BURST_RADIUS: Double = 3.0
        private const val FIREWORK_BURST_LIFETIME: Double = 120.0
        private const val FIREWORK_BURST_MASS: Double = 0.02
        private const val FIREWORK_BURST_Y_ACCELERATION: Double = 0.05
        private const val FIREWORK_BURST_DRAG: Double = 0.02
    }

    private var nextFireworkBurstGpuSlot: Int = 0

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val particles = mutableListOf<Particle>()
        val (startColor, endColor) = createDistinctColorPair()
        repeat(FIREWORK_BURST_PARTICLE_COUNT) {
            val angle = Random.nextDouble() * PI * 2
            val speed = Random.nextDouble() * FIREWORK_BURST_SPEED_RANGE + FIREWORK_BURST_MIN_SPEED
            val diameter = FIREWORK_BURST_RADIUS * 2.0
            particles += Particle(
                id = Random.nextInt().toString(16),
                effect = ParticleEffect.FIREWORK_BURST,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = FIREWORK_BURST_LIFETIME,
                color = startColor,
                startColor = startColor,
                endColor = endColor,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = FIREWORK_BURST_RADIUS,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = 0,
                originY = 0,
                originZ = 0,
                destinationX = 0,
                destinationY = 0,
                destinationZ = 0,
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                zVelocity = 0.0,
                maxXVelocity = DEFAULT_MAX_VELOCITY,
                maxYVelocity = DEFAULT_MAX_VELOCITY,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = FIREWORK_BURST_Y_ACCELERATION,
                zAcceleration = 0.0,
                drag = FIREWORK_BURST_DRAG,
                mass = FIREWORK_BURST_MASS,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false,
                gpuReservedSlot = nextFireworkBurstGpuSlot
            )
            nextFireworkBurstGpuSlot = (nextFireworkBurstGpuSlot + 1) % MAX_ACTIVE_FIREWORK_BURST_PARTICLES
        }
        return particles
    }
}
