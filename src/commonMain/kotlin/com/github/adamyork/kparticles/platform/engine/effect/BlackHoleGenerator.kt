package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
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
class BlackHoleGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.BLACK_HOLE) {

    private companion object {
        private const val BLACK_HOLE_CORE_RADIUS: Double = 5.0
        private const val BLACK_HOLE_CORE_DELAY: Double = 60.0
        private const val BLACK_HOLE_CORE_LIFETIME: Double = 2048.0
        private const val BLACK_HOLE_CORE_MASS: Double = 100.0
        private const val BLACK_HOLE_CORE_MAX_ACTIVE_PARTICLES: Int = 8
        private const val BLACK_HOLE_CORE_GPU_KIND: Float = 9f
        private const val BLACK_HOLE_EXPLOSION_PARTICLE_COUNT: Int = 256
        private const val BLACK_HOLE_EXPLOSION_RADIUS: Double = 2.0
        private const val BLACK_HOLE_EXPLOSION_MIN_SPEED: Double = 1.5
        private const val BLACK_HOLE_EXPLOSION_SPEED_RANGE: Double = 4.5
        private const val BLACK_HOLE_EXPLOSION_LIFETIME: Double = BLACK_HOLE_CORE_LIFETIME
        private const val BLACK_HOLE_EXPLOSION_MASS: Double = 0.05
        private const val BLACK_HOLE_EXPLOSION_DRAG: Double = 0.02
        private const val BLACK_HOLE_EXPLOSION_ATTRACTION: Double = 1.0
        private const val BLACK_HOLE_EXPLOSION_MAX_ACTIVE_PARTICLES: Int = 1024
        private const val BLACK_HOLE_EXPLOSION_GPU_KIND: Float = 10f
    }

    private val blackHoleCoreGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(BLACK_HOLE_CORE_MAX_ACTIVE_PARTICLES)

    private val blackHoleExplosionGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(BLACK_HOLE_EXPLOSION_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(blackHoleCoreGpuSlotGroup, blackHoleExplosionGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val centerX = viewPort.x + viewPort.width / 2.0
        val centerY = viewPort.y + viewPort.height / 2.0
        val coreAttraction = viewPort.width.toDouble()
        particles += Particle(
            effect = ParticleEffect.BLACK_HOLE,
            shape = ParticleShape.CIRCLE,
            delay = BLACK_HOLE_CORE_DELAY,
            lifetime = BLACK_HOLE_CORE_LIFETIME,
            color = Color.Black,
            width = BLACK_HOLE_CORE_RADIUS * 2.0,
            height = BLACK_HOLE_CORE_RADIUS * 2.0,
            radius = BLACK_HOLE_CORE_RADIUS,
            x = centerX,
            y = centerY,
            originX = centerX.roundToInt(),
            originY = centerY.roundToInt(),
            destinationX = centerX.roundToInt(),
            destinationY = centerY.roundToInt(),
            mass = BLACK_HOLE_CORE_MASS,
            attraction = coreAttraction,
            canCollide = true,
            viewportBound = true,
            yForce = -physicsSettingsService.gravity * min(1.0, BLACK_HOLE_CORE_MASS),
            gpuReservedSlot = blackHoleCoreGpuSlotGroup.claimNextSlot()
        )
        repeat(BLACK_HOLE_EXPLOSION_PARTICLE_COUNT) {
            val angle = Random.nextDouble() * PI * 2
            val speed = BLACK_HOLE_EXPLOSION_MIN_SPEED + Random.nextDouble() * BLACK_HOLE_EXPLOSION_SPEED_RANGE
            val diameter = BLACK_HOLE_EXPLOSION_RADIUS * 2.0
            particles += Particle(
                effect = ParticleEffect.BLACK_HOLE,
                shape = ParticleShape.CIRCLE,
                lifetime = BLACK_HOLE_EXPLOSION_LIFETIME,
                color = randomColor(),
                width = diameter,
                height = diameter,
                radius = BLACK_HOLE_EXPLOSION_RADIUS,
                x = centerX,
                y = centerY,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                destinationX = centerX.roundToInt(),
                destinationY = centerY.roundToInt(),
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                drag = BLACK_HOLE_EXPLOSION_DRAG,
                mass = BLACK_HOLE_EXPLOSION_MASS,
                attraction = BLACK_HOLE_EXPLOSION_ATTRACTION,
                yForce = -physicsSettingsService.gravity * min(1.0, BLACK_HOLE_EXPLOSION_MASS),
                gpuReservedSlot = blackHoleExplosionGpuSlotGroup.claimNextSlot()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup =
        if (isCore(particle)) blackHoleCoreGpuSlotGroup else blackHoleExplosionGpuSlotGroup

    override fun gpuKind(particle: Particle): Float =
        if (isCore(particle)) BLACK_HOLE_CORE_GPU_KIND else BLACK_HOLE_EXPLOSION_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        val firstOffset = if (isCore(particle)) 0 else 2
        targetBuffer[parameterIndex + firstOffset] = particle.mass.toFloat()
        targetBuffer[parameterIndex + firstOffset + 1] = particle.attraction.toFloat()
    }

    private fun isCore(particle: Particle): Boolean = particle.delay > 0.0
}
