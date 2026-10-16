package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class BubbleGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.BUBBLE) {

    private companion object {
        private const val BUBBLE_COUNT: Int = 12
        private const val BUBBLE_MIN_RADIUS: Double = 16.0
        private const val BUBBLE_MAX_RADIUS: Double = 48.0
        private const val BUBBLE_ALPHA: Double = 0.6
        private const val BUBBLE_MIN_RISE_SPEED: Double = 0.3
        private const val BUBBLE_RISE_SPEED_RANGE: Double = 0.9
        private const val BUBBLE_LIFETIME: Double = 3000.0
        private const val BUBBLE_SEQUENTIAL_DELAY_STEP: Double = 25.0
        private const val BUBBLE_MAX_ACTIVE_PARTICLES: Int = 32
        private const val BUBBLE_GPU_KIND: Float = 13f
    }

    private val bubbleGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(BUBBLE_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(bubbleGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        repeat(BUBBLE_COUNT) { bubbleIndex ->
            val radius = Random.nextDouble() * (BUBBLE_MAX_RADIUS - BUBBLE_MIN_RADIUS) + BUBBLE_MIN_RADIUS
            val spawnX = viewPort.x + Random.nextDouble() * viewPort.width
            val spawnY = viewPort.y + Random.nextDouble() * viewPort.height
            val riseSpeed = Random.nextDouble() * BUBBLE_RISE_SPEED_RANGE + BUBBLE_MIN_RISE_SPEED
            val particleId = nextParticleId()
            particles += Particle(
                lifecycle = ParticleLifecycle(
                    delay = bubbleIndex * BUBBLE_SEQUENTIAL_DELAY_STEP,
                    lifetime = BUBBLE_LIFETIME
                ),
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
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = bubbleGpuSlotGroup

    override fun shouldWriteGpuParticle(particle: Particle): Boolean = particle.lifecycle.isVisible()

    override fun gpuKind(particle: Particle): Float = BUBBLE_GPU_KIND
}
