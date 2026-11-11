package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.github.adamyork.particles.engine.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class GobblerGenerator(
    private val physicsSettingsService: ParticlePhysicsSettingsService
) : AbstractGenerator(ParticleEffects.GOBBLER) {

    private companion object {
        private val GOBBLER_TIER_RADII = doubleArrayOf(8.0, 18.0, 40.0)
        private val GOBBLER_TIER_COUNTS = intArrayOf(9, 5, 2)
        private const val GOBBLER_TIER_RADIUS_JITTER: Double = 0.04
        private const val GOBBLER_LIFETIME: Double = 1024.0
        private const val GOBBLER_MIN_SPEED: Double = 0.8
        private const val GOBBLER_SPEED_RANGE: Double = 2.4
        private const val GOBBLER_RESTITUTION: Double = 1.0
        private const val GOBBLER_MAX_ACTIVE_PARTICLES: Int = 64
    }

    private val gobblerGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(GOBBLER_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(gobblerGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val tierRadii = GOBBLER_TIER_RADII.indices.flatMap { tierIndex ->
            List(GOBBLER_TIER_COUNTS[tierIndex]) { GOBBLER_TIER_RADII[tierIndex] }
        }
        for (tierRadius in tierRadii) {
            val angle = random.nextDouble() * PI * 2
            val speed = GOBBLER_MIN_SPEED + random.nextDouble() * GOBBLER_SPEED_RANGE
            val startX = viewPort.x + random.nextDouble() * viewPort.width
            val startY = viewPort.y + random.nextDouble() * viewPort.height
            val radius = tierRadius * (1.0 + (random.nextDouble() * 2.0 - 1.0) * GOBBLER_TIER_RADIUS_JITTER)
            val dimensions = CircleDimensions(radius = radius)
            particles += Particle(
                lifecycle = ParticleLifecycle(lifetime = GOBBLER_LIFETIME),
                appearance = ParticleAppearance(color = randomColor()),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(startX, startY, Particle.BOUNDED_Z),
                    origin = ParticleVector(startX, startY)
                ),
                dynamics = ParticleDynamics(velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed)),
                material = ParticleMaterial(
                    density = physicsSettingsService.mediumDensity,
                    restitution = GOBBLER_RESTITUTION
                ),
                id = nextParticleId()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = gobblerGpuSlotGroup
}
