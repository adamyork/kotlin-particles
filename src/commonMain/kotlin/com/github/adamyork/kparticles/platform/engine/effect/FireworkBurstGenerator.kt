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
class FireworkBurstGenerator : AbstractGenerator(ParticleEffect.FIREWORK_BURST) {

    private companion object {
        private const val FIREWORK_BURST_PARTICLE_COUNT: Int = 100
        private const val FIREWORK_BURST_MIN_SPEED: Double = 2.0
        private const val FIREWORK_BURST_SPEED_RANGE: Double = 6.0
        private const val FIREWORK_BURST_RADIUS: Double = 3.0
        private const val FIREWORK_BURST_LIFETIME: Double = 120.0
        private const val FIREWORK_BURST_MASS: Double = 0.02
        private const val FIREWORK_BURST_Y_ACCELERATION: Double = 0.05
        private const val FIREWORK_BURST_DRAG: Double = 0.02
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
        repeat(FIREWORK_BURST_PARTICLE_COUNT) {
            val angle = Random.nextDouble() * PI * 2
            val speed = Random.nextDouble() * FIREWORK_BURST_SPEED_RANGE + FIREWORK_BURST_MIN_SPEED
            val diameter = FIREWORK_BURST_RADIUS * 2.0
            particles += Particle(
                effect = ParticleEffect.FIREWORK_BURST,
                shape = ParticleShape.CIRCLE,
                lifetime = FIREWORK_BURST_LIFETIME,
                color = startColor,
                endColor = endColor,
                width = diameter,
                height = diameter,
                radius = FIREWORK_BURST_RADIUS,
                x = centerX,
                y = centerY,
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                yAcceleration = FIREWORK_BURST_Y_ACCELERATION,
                drag = FIREWORK_BURST_DRAG,
                mass = FIREWORK_BURST_MASS,
                gpuReservedSlot = fireworkBurstGpuSlotGroup.claimNextSlot()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = fireworkBurstGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = FIREWORK_BURST_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        val endColorRed = (particle.endColor.red * 255f).roundToInt().coerceIn(0, 255)
        val endColorGreen = (particle.endColor.green * 255f).roundToInt().coerceIn(0, 255)
        val endColorBlue = (particle.endColor.blue * 255f).roundToInt().coerceIn(0, 255)
        targetBuffer[parameterIndex] = ((endColorRed * 65536) + (endColorGreen * 256) + endColorBlue).toFloat()
    }
}
