package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
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
class BubbleGenerator : AbstractGenerator(ParticleEffect.BUBBLE) {

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
        private const val BUBBLE_MAX_ACTIVE_PARTICLES: Int = 32
        private const val BUBBLE_GPU_KIND: Float = 13f
    }

    private val bubbleGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(BUBBLE_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(bubbleGpuSlotGroup)

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
            particles += Particle(
                effect = ParticleEffect.BUBBLE,
                shape = ParticleShape.CIRCLE,
                delay = bubbleIndex * BUBBLE_SEQUENTIAL_DELAY_STEP,
                lifetime = BUBBLE_LIFETIME,
                color = Color.White,
                alpha = BUBBLE_ALPHA,
                width = diameter,
                height = diameter,
                radius = radius,
                x = spawnX,
                y = spawnY,
                originX = spawnX.roundToInt(),
                originY = spawnY.roundToInt(),
                destinationX = spawnX.roundToInt(),
                destinationY = (spawnY - viewPort.height).roundToInt(),
                yVelocity = -riseSpeed,
                maxXVelocity = 0.0,
                maxYVelocity = 0.0,
                driftAmplitude = driftAmplitude,
                driftFrequency = driftFrequency,
                driftPhase = driftPhase,
                gpuReservedSlot = bubbleGpuSlotGroup.claimNextSlot()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = bubbleGpuSlotGroup

    override fun shouldWriteGpuParticle(particle: Particle): Boolean = particle.visible

    override fun gpuKind(particle: Particle): Float = BUBBLE_GPU_KIND
}
