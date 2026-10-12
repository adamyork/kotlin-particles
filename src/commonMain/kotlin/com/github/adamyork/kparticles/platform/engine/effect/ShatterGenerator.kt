package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.*
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class ShatterGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.SHATTER) {

    private companion object {
        private const val SHATTER_RADIUS_RATIO: Double = 0.2
        private const val SHATTER_LEFT_POSITION_RATIO: Double = 0.25
        private const val SHATTER_RIGHT_POSITION_RATIO: Double = 0.75
        private const val SHATTER_SPEED: Double = 3.0
        private const val SHATTER_HARD_MASS: Double = 2.0
        private const val SHATTER_SOFT_MASS: Double = 1.0
        private const val SHATTER_LIFETIME: Double = 2048.0
        private const val SHATTER_RESTITUTION: Double = 0.9
        private const val SHATTER_HARD_HARDNESS: Int = 2
        private const val SHATTER_SOFT_HARDNESS: Int = 1
        private const val SHATTER_GPU_KIND: Float = 14f
        private const val SHATTER_MAX_ACTIVE_PARTICLES: Int = 256
    }

    private val shatterGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(SHATTER_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(shatterGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val radius = viewPort.height * SHATTER_RADIUS_RATIO
        val centerY = viewPort.y + viewPort.height / 2.0
        val leftX = viewPort.x + viewPort.width * SHATTER_LEFT_POSITION_RATIO
        val rightX = viewPort.x + viewPort.width * SHATTER_RIGHT_POSITION_RATIO
        return mutableListOf(
            createShatterParticle(
                ParticleShape.RECT, radius, leftX, centerY, SHATTER_SPEED, SHATTER_HARD_MASS, SHATTER_HARD_HARDNESS, Color.White
            ),
            createShatterParticle(
                ParticleShape.CIRCLE, radius, rightX, centerY, -SHATTER_SPEED, SHATTER_SOFT_MASS, SHATTER_SOFT_HARDNESS, Color.Cyan
            )
        )
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = shatterGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = SHATTER_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex] = particle.hardness.toFloat()
        targetBuffer[parameterIndex + 1] = particle.mass.toFloat()
        targetBuffer[parameterIndex + 2] = particle.restitution.toFloat()
    }

    override fun gpuSpawnOffset(particle: Particle, size: Float): Float = 0f

    private fun createShatterParticle(
        shape: ParticleShape,
        radius: Double,
        centerX: Double,
        centerY: Double,
        xVelocity: Double,
        mass: Double,
        hardness: Int,
        color: Color
    ): Particle {
        val diameter = radius * 2.0
        return Particle(
            effect = ParticleEffect.SHATTER,
            shape = shape,
            lifetime = SHATTER_LIFETIME,
            color = color,
            width = diameter,
            height = diameter,
            radius = radius,
            x = centerX,
            y = centerY,
            originX = centerX.roundToInt(),
            originY = centerY.roundToInt(),
            xVelocity = xVelocity,
            maxXVelocity = physicsSettingsService.shatterMaxVelocity,
            maxYVelocity = physicsSettingsService.shatterMaxVelocity,
            mass = mass,
            restitution = SHATTER_RESTITUTION,
            canCollide = true,
            viewportBound = true,
            yForce = -physicsSettingsService.gravity * min(1.0, mass),
            hardness = hardness,
            gpuReservedSlot = shatterGpuSlotGroup.claimNextSlot()
        )
    }
}
