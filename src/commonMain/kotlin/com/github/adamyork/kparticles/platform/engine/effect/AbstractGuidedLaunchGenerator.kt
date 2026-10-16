package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractGuidedLaunchGenerator(
    effect: ParticleEffect,
    private val guidedLaunchGpuKind: Float,
    private val guidedLaunchGpuSlotGroup: GpuSlotGroup,
    private val guidedLaunchMaxFrames: Int,
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(effect) {

    companion object {
        const val GUIDED_LAUNCH_INITIAL_SPEED: Double = 2.8
        private const val GUIDED_LAUNCH_RADIUS: Double = 16.0
        private const val GUIDED_LAUNCH_NET_GRAVITY_FRACTION: Double = 0.0001
        private const val GUIDED_LAUNCH_SETTLE_FRACTION: Double = 0.95
        private const val GUIDED_LAUNCH_SPEED_MULTIPLIER: Double = 2.0
        private const val GUIDED_LAUNCH_MINIMUM_DRAG: Double = 0.0001
    }

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(guidedLaunchGpuSlotGroup)

    protected abstract fun launchColor(): Color

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val deltaX = request.destinationX - centerX
        val deltaY = request.destinationY - centerY
        val launchAngle = atan2(deltaY, deltaX)
        val drag = max(physicsSettingsService.drag, GUIDED_LAUNCH_MINIMUM_DRAG)
        val arrivalTicks = -ln(1.0 - GUIDED_LAUNCH_SETTLE_FRACTION) / drag / GUIDED_LAUNCH_SPEED_MULTIPLIER
        val launchSpeed = hypot(deltaX, deltaY) * drag / (1.0 - exp(-drag * arrivalTicks))
        val dimensions = CircleDimensions(radius = GUIDED_LAUNCH_RADIUS)
        val density = Particle.densityForNetGravity(
            physicsSettingsService.gravity * GUIDED_LAUNCH_NET_GRAVITY_FRACTION,
            physicsSettingsService.gravity,
            physicsSettingsService.mediumDensity
        )
        return mutableListOf(
            Particle(
                id = nextParticleId(),
                lifecycle = ParticleLifecycle(lifetime = arrivalTicks),
                appearance = ParticleAppearance(color = launchColor()),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(centerX, centerY),
                    origin = ParticleVector(centerX, centerY)
                ),
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(
                        cos(launchAngle) * launchSpeed,
                        sin(launchAngle) * launchSpeed
                    )
                ),
                material = ParticleMaterial(density = density),
                animation = ParticleAnimation(maxFrames = guidedLaunchMaxFrames)
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
        val velocityLength = guidedLaunchVelocityLength(particle)
        return if (velocityLength > 0f) particle.dynamics.velocity.x.toFloat() / velocityLength else 1f
    }

    protected fun guidedLaunchUnitY(particle: Particle): Float {
        val velocityLength = guidedLaunchVelocityLength(particle)
        return if (velocityLength > 0f) particle.dynamics.velocity.y.toFloat() / velocityLength else 0f
    }

    private fun guidedLaunchVelocityLength(particle: Particle): Float {
        return sqrt((particle.dynamics.velocity.x * particle.dynamics.velocity.x + particle.dynamics.velocity.y * particle.dynamics.velocity.y).toFloat())
    }
}
