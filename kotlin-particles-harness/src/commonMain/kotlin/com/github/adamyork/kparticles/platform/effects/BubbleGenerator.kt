package com.github.adamyork.kparticles.platform.effects

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.ScheduledGenerator
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class BubbleGenerator(
    private val physicsSettingsService: ParticlePhysicsSettingsService
) : ScheduledGenerator<Unit>(
    ParticleEffects.BUBBLE,
    BUBBLE_EMISSION_INTERVAL_TICKS,
    BUBBLE_PARTICLES_PER_INTERVAL,
    BUBBLE_COUNT
) {

    private companion object {
        private const val BUBBLE_COUNT: Int = 12
        private const val BUBBLE_MIN_RADIUS: Double = 16.0
        private const val BUBBLE_MAX_RADIUS: Double = 48.0
        private const val BUBBLE_ALPHA: Double = 0.6
        private const val BUBBLE_MIN_RISE_SPEED: Double = 0.3
        private const val BUBBLE_RISE_SPEED_RANGE: Double = 0.9
        private const val BUBBLE_LIFETIME: Double = 3000.0
        private const val BUBBLE_EMISSION_INTERVAL_TICKS: Double = 25.0
        private const val BUBBLE_PARTICLES_PER_INTERVAL: Int = 1
        private const val BUBBLE_MAX_ACTIVE_PARTICLES: Int = 32
    }

    private val bubbleGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(BUBBLE_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(bubbleGpuSlotGroup)

    override fun createStreamState(request: ParticleEffectRequest) = Unit

    override fun createParticle(
        request: ParticleEffectRequest,
        streamState: Unit,
        particleId: Long,
        emissionIndex: Int,
        emissionOffset: Double
    ): Particle {
        val viewPort = request.viewPort
        val radius = random.nextDouble() * (BUBBLE_MAX_RADIUS - BUBBLE_MIN_RADIUS) + BUBBLE_MIN_RADIUS
        val spawnX = viewPort.x + random.nextDouble() * viewPort.width
        val spawnY = viewPort.y + random.nextDouble() * viewPort.height
        val riseSpeed = random.nextDouble() * BUBBLE_RISE_SPEED_RANGE + BUBBLE_MIN_RISE_SPEED
        return Particle(
            lifecycle = ParticleLifecycle(lifetime = BUBBLE_LIFETIME),
            appearance = ParticleAppearance(color = Color.White, alpha = BUBBLE_ALPHA),
            dimensions = CircleDimensions(radius = radius),
            transform = ParticleTransform(
                position = ParticleVector(
                    spawnX,
                    spawnY,
                    Particle.contactPlaneZ(
                        particleId,
                        physicsSettingsService.collisionPlaneThickness,
                        physicsSettingsService.gravityPlaneThickness
                    )
                ),
                origin = ParticleVector(spawnX, spawnY)
            ),
            dynamics = ParticleDynamics(velocity = ParticleVector(0.0, -riseSpeed)),
            material = ParticleMaterial(
                density = Particle.densityForNetGravity(
                    -riseSpeed * physicsSettingsService.drag,
                    physicsSettingsService.gravity,
                    physicsSettingsService.mediumDensity
                )
            ),
            id = particleId
        )
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = bubbleGpuSlotGroup
}
