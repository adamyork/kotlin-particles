package com.github.adamyork.kparticles.platform.effects

import androidx.compose.ui.graphics.Color
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import kotlin.math.*
import com.github.adamyork.particles.engine.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractGuidedLaunchGenerator(
    effect: ParticleEffect,
    private val guidedLaunchGpuSlotGroup: GpuSlotGroup,
    private val guidedLaunchMaxFrames: Int,
    private val physicsSettingsService: ParticlePhysicsSettingsService
) : AbstractGenerator(effect) {

    companion object {
        private const val GUIDED_LAUNCH_RADIUS: Double = 16.0
        private const val GUIDED_LAUNCH_NET_GRAVITY_FRACTION: Double = 0.0001
        private const val GUIDED_LAUNCH_SETTLE_FRACTION: Double = 0.95
        private const val GUIDED_LAUNCH_DRAG_LENGTHS: Double = 1.0
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
        val launchDistance = hypot(deltaX, deltaY)
        val dimensions = CircleDimensions(radius = GUIDED_LAUNCH_RADIUS)
        val mediumDensity = physicsSettingsService.mediumDensity
        val gravity = physicsSettingsService.gravity
        val frontalLength = (dimensions.boundingWidth() + dimensions.boundingHeight()) / 2.0
        val neutralDensity = Particle.densityForNetGravity(gravity * GUIDED_LAUNCH_NET_GRAVITY_FRACTION, gravity, mediumDensity)
        val targetDragRate = GUIDED_LAUNCH_DRAG_LENGTHS / max(launchDistance, 1.0)
        val dragDensity = 0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength /
            (dimensions.area() * targetDragRate)
        val density = max(neutralDensity, dragDensity)
        val quadraticDragRate = 0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength /
            (density * dimensions.area())
        val launchSpeed = drag * expm1(quadraticDragRate * launchDistance) /
            (max(quadraticDragRate, Double.MIN_VALUE) * (1.0 - exp(-drag * arrivalTicks)))
        val particleMass = density * dimensions.area()
        val holdThrust = -particleMass * Particle.netGravityOf(density, gravity, mediumDensity)
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
                    ),
                    thrust = ParticleVector(0.0, holdThrust)
                ),
                material = ParticleMaterial(density = density),
                animation = ParticleAnimation(maxFrames = guidedLaunchMaxFrames)
            )
        )
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = guidedLaunchGpuSlotGroup
}
