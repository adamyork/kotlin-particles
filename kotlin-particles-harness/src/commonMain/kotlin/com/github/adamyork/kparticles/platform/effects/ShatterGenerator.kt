package com.github.adamyork.kparticles.platform.effects

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import com.github.adamyork.particles.engine.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class ShatterGenerator(
    private val physicsSettingsService: ParticlePhysicsSettingsService
) : AbstractGenerator(ParticleEffects.SHATTER) {

    private companion object {
        private const val SHATTER_TARGET_RADIUS_RATIO: Double = 0.28
        private const val SHATTER_TARGET_HARDNESS: Int = 3
        private const val SHATTER_TARGET_POSITION_RATIO: Double = 0.25
        private const val SHATTER_GUN_POSITION_RATIO: Double = 0.85
        private const val SHATTER_PROJECTILE_COUNT: Int = 20
        private const val SHATTER_PROJECTILE_RADIUS: Double = 14.0
        private const val SHATTER_PROJECTILE_DELAY_STEP: Double = 6.0
        private const val SHATTER_PROJECTILE_MIN_SPEED: Double = 30.0
        private const val SHATTER_PROJECTILE_SPEED_RANGE: Double = 35.0
        private const val SHATTER_PROJECTILE_DRAG_REACH: Double = 2000.0
        private const val SHATTER_TARGET_DENSITY_RATIO: Double = 200.0
        private const val SHATTER_AIM_SWEEP_RATIO: Double = 0.8
        private const val SHATTER_AIM_SWEEP_FREQUENCY: Double = 0.9
        private const val SHATTER_AIM_JITTER_RATIO: Double = 0.15
        private const val SHATTER_LIFETIME: Double = 2048.0
        private const val SHATTER_RESTITUTION: Double = 0.9
        private const val SHATTER_MAX_ACTIVE_PARTICLES: Int = 1024
        private val SHATTER_PROJECTILE_HARDNESS = intArrayOf(1, 0, 2)
        private val SHATTER_HARDNESS_COLORS = listOf(Color.Cyan, Color.Green, Color.Yellow, Color.White)
    }

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(GpuSlotGroup(SHATTER_MAX_ACTIVE_PARTICLES))

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val targetRadius = viewPort.height * SHATTER_TARGET_RADIUS_RATIO
        val centerY = viewPort.y + viewPort.height / 2.0
        val targetX = viewPort.x + viewPort.width * SHATTER_TARGET_POSITION_RATIO
        val gunX = viewPort.x + viewPort.width * SHATTER_GUN_POSITION_RATIO
        val particles = mutableListOf<Particle>()
        particles += createShatterParticle(
            ParticleRectDimensions(width = targetRadius * 2.0, height = targetRadius * 2.0),
            targetX,
            centerY,
            ParticleVector(0.0, 0.0),
            SHATTER_TARGET_HARDNESS,
            0.0,
            physicsSettingsService.mediumDensity * SHATTER_TARGET_DENSITY_RATIO
        )
        val projectileDimensions = ParticleRectDimensions(
            width = SHATTER_PROJECTILE_RADIUS * 2.0, height = SHATTER_PROJECTILE_RADIUS * 2.0
        )
        val projectileFrontalLength =
            (projectileDimensions.boundingWidth() + projectileDimensions.boundingHeight()) / 2.0
        val projectileDensity = max(
            physicsSettingsService.mediumDensity,
            0.5 * physicsSettingsService.mediumDensity * physicsSettingsService.dragCoefficient * projectileFrontalLength *
                SHATTER_PROJECTILE_DRAG_REACH / projectileDimensions.area()
        )
        val aimDistanceX = targetX + targetRadius - gunX
        repeat(SHATTER_PROJECTILE_COUNT) { projectileIndex ->
            val aimOffsetY = targetRadius * (
                SHATTER_AIM_SWEEP_RATIO * sin(projectileIndex * SHATTER_AIM_SWEEP_FREQUENCY) +
                    (random.nextDouble() - 0.5) * 2.0 * SHATTER_AIM_JITTER_RATIO
                )
            val aimLength = sqrt(aimDistanceX * aimDistanceX + aimOffsetY * aimOffsetY)
            val impactSpeed = SHATTER_PROJECTILE_MIN_SPEED + random.nextDouble() * SHATTER_PROJECTILE_SPEED_RANGE
            val launchSpeed = launchSpeedFor(projectileDimensions, projectileDensity, aimLength, impactSpeed)
            particles += createShatterParticle(
                ParticleRectDimensions(width = SHATTER_PROJECTILE_RADIUS * 2.0, height = SHATTER_PROJECTILE_RADIUS * 2.0),
                gunX,
                centerY,
                ParticleVector(aimDistanceX / aimLength * launchSpeed, aimOffsetY / aimLength * launchSpeed),
                SHATTER_PROJECTILE_HARDNESS[projectileIndex % SHATTER_PROJECTILE_HARDNESS.size],
                projectileIndex * SHATTER_PROJECTILE_DELAY_STEP,
                projectileDensity
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = gpuSlotGroups.first()

    override fun adopt(particle: Particle) {
        val particleId = nextParticleId()
        particles.add(
            particle.copy(
                id = particleId,
                transform = particle.transform.copy(
                    position = particle.transform.position.copy(z = shatterPlaneZ(particleId))
                )
            )
        )
    }

    override fun gpuSplitFragmentsUseWallPlane(): Boolean = true

    private fun launchSpeedFor(
        dimensions: ParticleDimensions,
        density: Double,
        travelDistance: Double,
        impactSpeed: Double
    ): Double {
        val frontalLength = (dimensions.boundingWidth() + dimensions.boundingHeight()) / 2.0
        val quadraticDragRate = 0.5 * physicsSettingsService.mediumDensity * physicsSettingsService.dragCoefficient *
            frontalLength / (density * dimensions.area())
        val flightTicks = travelDistance / impactSpeed
        return impactSpeed * exp(quadraticDragRate * travelDistance + physicsSettingsService.drag * flightTicks)
    }

    private fun createShatterParticle(
        dimensions: ParticleDimensions,
        centerX: Double,
        centerY: Double,
        velocity: ParticleVector,
        hardness: Int,
        delay: Double,
        density: Double
    ): Particle {
        val particleId = nextParticleId()
        val holdThrustY = -density * dimensions.area() * Particle.netGravityOf(
            density, physicsSettingsService.gravity, physicsSettingsService.mediumDensity
        )
        return Particle(
            lifecycle = ParticleLifecycle(delay = delay, lifetime = SHATTER_LIFETIME),
            appearance = ParticleAppearance(color = SHATTER_HARDNESS_COLORS[hardness]),
            dimensions = dimensions,
            transform = ParticleTransform(
                position = ParticleVector(centerX, centerY, shatterPlaneZ(particleId)),
                origin = ParticleVector(centerX, centerY)
            ),
            dynamics = ParticleDynamics(velocity = velocity, thrust = ParticleVector(0.0, holdThrustY)),
            material = ParticleMaterial(
                density = density,
                restitution = SHATTER_RESTITUTION,
                hardness = hardness
            ),
            id = particleId
        )
    }

    private fun shatterPlaneZ(particleId: Long): Double {
        return Particle.wallContactPlaneZ(
            particleId,
            physicsSettingsService.collisionPlaneThickness,
            physicsSettingsService.gravityPlaneThickness
        )
    }
}
