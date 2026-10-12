package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.data.*
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractGuidedLaunchGenerator(
    effect: ParticleEffect,
    private val guidedLaunchGpuKind: Float,
    private val guidedLaunchGpuSlotGroup: GpuSlotGroup,
    private val guidedLaunchMaxFrames: Int
) : AbstractGenerator(effect) {

    companion object {
        const val GUIDED_LAUNCH_INITIAL_SPEED: Double = 2.8
        const val GUIDED_LAUNCH_MAX_SPEED: Double = 5.25
        const val GUIDED_LAUNCH_THRUST: Double = 0.16
        const val GUIDED_LAUNCH_VELOCITY_HEADROOM: Double = 0.2
        private const val GUIDED_LAUNCH_RADIUS: Double = 16.0
        private const val GUIDED_LAUNCH_LIFETIME: Double = 512.0
        private const val GUIDED_LAUNCH_MASS: Double = 0.15
        private const val GUIDED_LAUNCH_DRAG: Double = 0.001
    }

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(guidedLaunchGpuSlotGroup)

    protected abstract fun launchColor(): Color

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val launchAngle = atan2(request.destinationY - centerY, request.destinationX - centerX)
        val diameter = GUIDED_LAUNCH_RADIUS * 2.0
        return mutableListOf(
            Particle(
                effect = effect,
                shape = ParticleShape.CIRCLE,
                lifetime = GUIDED_LAUNCH_LIFETIME,
                color = launchColor(),
                width = diameter,
                height = diameter,
                radius = GUIDED_LAUNCH_RADIUS,
                x = centerX,
                y = centerY,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                destinationX = request.destinationX.roundToInt(),
                destinationY = request.destinationY.roundToInt(),
                xVelocity = cos(launchAngle) * GUIDED_LAUNCH_INITIAL_SPEED,
                yVelocity = sin(launchAngle) * GUIDED_LAUNCH_INITIAL_SPEED,
                maxXVelocity = abs(cos(launchAngle) * GUIDED_LAUNCH_MAX_SPEED) + GUIDED_LAUNCH_VELOCITY_HEADROOM,
                maxYVelocity = abs(sin(launchAngle) * GUIDED_LAUNCH_MAX_SPEED) + GUIDED_LAUNCH_VELOCITY_HEADROOM,
                xAcceleration = cos(launchAngle) * GUIDED_LAUNCH_THRUST,
                yAcceleration = sin(launchAngle) * GUIDED_LAUNCH_THRUST,
                drag = GUIDED_LAUNCH_DRAG,
                mass = GUIDED_LAUNCH_MASS,
                maxFrames = guidedLaunchMaxFrames
            )
        )
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = guidedLaunchGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = guidedLaunchGpuKind

    override fun gpuVelocityX(particle: Particle): Float =
        guidedLaunchUnitX(particle) * GUIDED_LAUNCH_INITIAL_SPEED.toFloat()

    override fun gpuVelocityY(particle: Particle): Float =
        guidedLaunchUnitY(particle) * GUIDED_LAUNCH_INITIAL_SPEED.toFloat()

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex] = guidedLaunchUnitX(particle)
        targetBuffer[parameterIndex + 1] = guidedLaunchUnitY(particle)
    }

    protected fun guidedLaunchUnitX(particle: Particle): Float {
        val guidedLaunchLength = guidedLaunchLength(particle)
        val guidedLaunchDeltaX = (particle.destinationX - particle.originX).toFloat()
        return if (guidedLaunchLength > 0f) guidedLaunchDeltaX / guidedLaunchLength else 1f
    }

    protected fun guidedLaunchUnitY(particle: Particle): Float {
        val guidedLaunchLength = guidedLaunchLength(particle)
        val guidedLaunchDeltaY = (particle.destinationY - particle.originY).toFloat()
        return if (guidedLaunchLength > 0f) guidedLaunchDeltaY / guidedLaunchLength else 0f
    }

    private fun guidedLaunchLength(particle: Particle): Float {
        val guidedLaunchDeltaX = (particle.destinationX - particle.originX).toFloat()
        val guidedLaunchDeltaY = (particle.destinationY - particle.originY).toFloat()
        return sqrt((guidedLaunchDeltaX * guidedLaunchDeltaX) + (guidedLaunchDeltaY * guidedLaunchDeltaY))
    }
}
