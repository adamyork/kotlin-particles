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
class BubbleGenerator : BaseGenerator(ParticleEffect.BUBBLE) {

    private companion object {
        private const val BUBBLE_COUNT: Int = 12
        private const val BUBBLE_MIN_RADIUS: Double = 16.0
        private const val BUBBLE_MAX_RADIUS: Double = 48.0
        private const val BUBBLE_ALPHA: Double = 0.6
        private const val BUBBLE_MIN_RISE_SPEED: Double = 0.3
        private const val BUBBLE_RISE_SPEED_RANGE: Double = 0.9
        private const val BUBBLE_LIFETIME: Double = 3000.0
        private const val BUBBLE_SEQUENTIAL_DELAY_STEP: Double = 25.0
        private const val BUBBLE_DRIFT_MIN_AMPLITUDE: Double = 0.05
        private const val BUBBLE_DRIFT_AMPLITUDE_RANGE: Double = 0.1
        private const val BUBBLE_DRIFT_MIN_FREQUENCY: Double = 0.0015
        private const val BUBBLE_DRIFT_FREQUENCY_RANGE: Double = 0.0015
    }

    private var nextBubbleGpuSlot: Int = 0

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        repeat(BUBBLE_COUNT) { bubbleIndex ->
            val radius = Random.nextDouble() * (BUBBLE_MAX_RADIUS - BUBBLE_MIN_RADIUS) + BUBBLE_MIN_RADIUS
            val diameter = radius * 2.0
            val spawnX = viewPort.x + Random.nextDouble() * viewPort.width
            val spawnY = viewPort.y + Random.nextDouble() * viewPort.height
            val riseSpeed = Random.nextDouble() * BUBBLE_RISE_SPEED_RANGE + BUBBLE_MIN_RISE_SPEED
            val driftAmplitude = Random.nextDouble() * BUBBLE_DRIFT_AMPLITUDE_RANGE + BUBBLE_DRIFT_MIN_AMPLITUDE
            val driftFrequency = Random.nextDouble() * BUBBLE_DRIFT_FREQUENCY_RANGE + BUBBLE_DRIFT_MIN_FREQUENCY
            val driftPhase = Random.nextDouble() * (2.0 * PI)
            val sequentialDelay = bubbleIndex * BUBBLE_SEQUENTIAL_DELAY_STEP
            particles += Particle(
                id = Random.nextInt().toString(16),
                effect = ParticleEffect.BUBBLE,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = sequentialDelay,
                lifetime = BUBBLE_LIFETIME,
                color = Color.White,
                startColor = Color.White,
                endColor = Color.White,
                alpha = BUBBLE_ALPHA,
                endAlpha = BUBBLE_ALPHA,
                initialAlpha = BUBBLE_ALPHA,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = radius,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = spawnX,
                y = spawnY,
                z = 0.0,
                originX = spawnX.roundToInt(),
                originY = spawnY.roundToInt(),
                originZ = 0,
                destinationX = spawnX.roundToInt(),
                destinationY = (spawnY - viewPort.height).roundToInt(),
                destinationZ = 0,
                xVelocity = 0.0,
                yVelocity = -riseSpeed,
                zVelocity = 0.0,
                maxXVelocity = 0.0,
                maxYVelocity = 0.0,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = 0.0,
                mass = 0.0,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false,
                driftAmplitude = driftAmplitude,
                driftFrequency = driftFrequency,
                driftPhase = driftPhase,
                gpuReservedSlot = nextBubbleGpuSlot
            )
            nextBubbleGpuSlot = (nextBubbleGpuSlot + 1) % MAX_ACTIVE_BUBBLE_PARTICLES
        }
        return particles
    }
}
