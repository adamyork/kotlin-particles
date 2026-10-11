package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
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
class CollidingBitsGenerator : BaseGenerator(ParticleEffect.COLLIDING_BITS) {

    private companion object {
        private const val COLLIDING_BITS_PARTICLE_COUNT: Int = 100
        private const val COLLIDING_BITS_MIN_SIZE: Double = 8.0
        private const val COLLIDING_BITS_SIZE_RANGE: Double = 24.0
        private const val COLLIDING_BITS_MIN_LIFETIME: Double = 128.0
        private const val COLLIDING_BITS_LIFETIME_RANGE: Double = 384.0
        private const val COLLIDING_BITS_MIN_SPEED: Double = 2.0
        private const val COLLIDING_BITS_SPEED_RANGE: Double = 6.0
        private const val COLLIDING_BITS_MASS: Double = 0.12
        private const val COLLIDING_BITS_RESTITUTION: Double = 0.6
    }

    private var nextCollidingBitsGpuSlot: Int = 0

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
            val destinationX = viewPort.x + Random.nextDouble() * viewPort.width
            val destinationY = viewPort.y + Random.nextDouble() * viewPort.height
            val radius: Double
            val width: Double
            val height: Double
            if (isCircle) {
                radius = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE
                width = radius * 2.0
                height = width
            } else {
                radius = 0.0
                width = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE
                height = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE
            }
            particles += Particle(
                id = Random.nextInt().toString(16),
                effect = ParticleEffect.COLLIDING_BITS,
                shape = if (isCircle) ParticleShape.CIRCLE else ParticleShape.RECT,
                age = 0.0,
                delay = 0.0,
                lifetime = lifetime,
                color = Color.White,
                startColor = Color.White,
                endColor = Color.White,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = width,
                height = height,
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
                maxXVelocity = DEFAULT_MAX_VELOCITY,
                maxYVelocity = DEFAULT_MAX_VELOCITY,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = 0.0,
                mass = COLLIDING_BITS_MASS,
                restitution = COLLIDING_BITS_RESTITUTION,
                attraction = 0.0,
                canCollide = true,
                visible = true,
                viewportBound = true,
                gpuReservedSlot = nextCollidingBitsGpuSlot
            )
            nextCollidingBitsGpuSlot = (nextCollidingBitsGpuSlot + 1) % MAX_ACTIVE_COLLIDING_BITS_PARTICLES
        }
        return particles
    }
}
