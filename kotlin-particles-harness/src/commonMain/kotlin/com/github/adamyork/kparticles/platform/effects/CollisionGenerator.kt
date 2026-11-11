package com.github.adamyork.kparticles.platform.effects

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.kparticles.platform.assets.AssetService
import kotlin.math.*
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import com.github.adamyork.particles.engine.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class CollisionGenerator(
    private val physicsSettingsService: ParticlePhysicsSettingsService,
    private val assetService: AssetService
) : AbstractGenerator(ParticleEffects.COLLISION) {

    private companion object {
        private const val COLLISION_PARTICLE_COUNT: Int = 256
        private const val COLLISION_EXPLOSION_ANGLE_SPAN: Double = PI
        private const val COLLISION_MIN_RADIUS: Double = 2.0
        private const val COLLISION_RADIUS_RANGE: Double = 2.5
        private const val COLLISION_REACH: Double = 170.0
        private const val COLLISION_MIN_REACH_FRACTION: Double = 0.1
        private const val COLLISION_REACH_FRACTION_RANGE: Double = 0.9
        private const val COLLISION_REACH_TIME: Double = 45.0
        private const val COLLISION_LIFETIME: Double = 240.0
        private const val COLLISION_INITIAL_ALPHA: Double = 0.95
        private const val COLLISION_END_ALPHA: Double = 1.0
        private const val COLLISION_TERMINAL_SPEED: Double = 9.0
        private const val COLLISION_RESTITUTION: Double = 0.8
        private const val COLLISION_MAX_ACTIVE_PARTICLES: Int = 2048
    }

    private val collisionGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(COLLISION_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(collisionGpuSlotGroup)

    private var color: Color = Color.White

    override fun onAssetsReady() {
        color = ConfiguredColors.createColor(assetService.appProperties.particle.player.collision.color)
    }

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val particles = mutableListOf<Particle>()
        val explodeAngle = when (request.direction) {
            ParticleDirection.RIGHT -> 0.0
            ParticleDirection.LEFT -> PI
        }
        val angleStep = COLLISION_EXPLOSION_ANGLE_SPAN / (COLLISION_PARTICLE_COUNT - 1)
        val startAngle = explodeAngle - COLLISION_EXPLOSION_ANGLE_SPAN / 2.0
        repeat(COLLISION_PARTICLE_COUNT) { particleIndex ->
            val radius = random.nextDouble() * COLLISION_RADIUS_RANGE + COLLISION_MIN_RADIUS
            val velocityAngle = startAngle + angleStep * particleIndex
            val reachFraction = COLLISION_MIN_REACH_FRACTION + sqrt(random.nextDouble()) * COLLISION_REACH_FRACTION_RANGE
            val dimensions = CircleDimensions(radius = radius)
            val density = Particle.densityForTerminalSpeed(
                COLLISION_TERMINAL_SPEED,
                physicsSettingsService.gravity,
                physicsSettingsService.mediumDensity,
                physicsSettingsService.dragCoefficient,
                dimensions
            )
            val frontalLength = (dimensions.boundingWidth() + dimensions.boundingHeight()) / 2.0
            val dragRate = 0.5 * physicsSettingsService.mediumDensity * physicsSettingsService.dragCoefficient *
                frontalLength / (density * dimensions.area())
            val speed = expm1(dragRate * COLLISION_REACH * reachFraction) / (dragRate * COLLISION_REACH_TIME)
            val particleId = nextParticleId()
            particles += Particle(
                id = particleId,
                lifecycle = ParticleLifecycle(lifetime = COLLISION_LIFETIME),
                appearance = ParticleAppearance(color = color, alpha = COLLISION_INITIAL_ALPHA, endAlpha = COLLISION_END_ALPHA),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(
                        centerX, centerY, Particle.isolatedPlaneZ(particleId, physicsSettingsService.collisionPlaneThickness)
                    ),
                    origin = ParticleVector(centerX, centerY)
                ),
                dynamics = ParticleDynamics(velocity = ParticleVector(cos(velocityAngle) * speed, sin(velocityAngle) * speed)),
                material = ParticleMaterial(
                    density = density,
                    restitution = COLLISION_RESTITUTION
                )
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = collisionGpuSlotGroup
}
