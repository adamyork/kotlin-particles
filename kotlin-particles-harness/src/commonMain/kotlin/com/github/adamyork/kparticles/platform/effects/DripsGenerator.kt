package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.PhysicsSettingsService
import kotlin.math.*
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.engine.AbstractGenerator

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class DripsGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffects.DRIPS) {

    private companion object {
        private const val DRIPS_GROUP_COUNT: Int = 10
        private const val DRIPS_GROUP_STAGGER_TICKS: Double = 240.0
        private const val DRIPS_START_BAND_RATIO: Double = 0.25
        private const val DRIPS_MAIN_MIN_RADIUS: Double = 6.0
        private const val DRIPS_MAIN_RADIUS_RANGE: Double = 4.0
        private const val DRIPS_MAIN_MIN_LENGTH: Double = 100.0
        private const val DRIPS_MAIN_LENGTH_RANGE: Double = 60.0
        private const val DRIPS_LONG_CHANCE: Double = 0.3
        private const val DRIPS_LONG_MIN_HEIGHT_RATIO: Double = 0.6
        private const val DRIPS_LONG_HEIGHT_RATIO_RANGE: Double = 0.4
        private const val DRIPS_MIN_ADHESION_RATIO: Double = 0.05
        private const val DRIPS_MAX_ADHESION_RATIO: Double = 0.9
        private const val DRIPS_DENSITY_RATIO: Double = 1.1
        private const val DRIPS_AVERAGE_SPEED: Double = 3.5
        private const val DRIPS_HOLD_TICKS: Double = 3600.0
        private const val DRIPS_MAX_ACTIVE_PARTICLES: Int = 256
    }

    private val dripsGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(DRIPS_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(dripsGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val gravity = physicsSettingsService.gravity
        val mediumDensity = physicsSettingsService.mediumDensity
        val dripDensity = mediumDensity * DRIPS_DENSITY_RATIO
        repeat(DRIPS_GROUP_COUNT) {
            val color = randomColor()
            val groupDelay = random.nextDouble() * DRIPS_GROUP_STAGGER_TICKS
            val mainRadius = DRIPS_MAIN_MIN_RADIUS + random.nextDouble() * DRIPS_MAIN_RADIUS_RANGE
            val sourceCenterX = viewPort.x + random.nextDouble() * viewPort.width
            val sourceY = viewPort.y + random.nextDouble() * viewPort.height * DRIPS_START_BAND_RATIO
            val remainingHeight = viewPort.y + viewPort.height - sourceY
            val mainLength = if (random.nextDouble() < DRIPS_LONG_CHANCE) {
                max(
                    DRIPS_MAIN_MIN_LENGTH,
                    min(
                        remainingHeight,
                        viewPort.height * (DRIPS_LONG_MIN_HEIGHT_RATIO + random.nextDouble() * DRIPS_LONG_HEIGHT_RATIO_RANGE)
                    )
                )
            } else {
                DRIPS_MAIN_MIN_LENGTH + random.nextDouble() * DRIPS_MAIN_LENGTH_RANGE
            }
            val trailFriction = physicsSettingsService.friction + ParticleMaterial.DEFAULT_FRICTION
            val adhesionRatio = (1.0 / (1.0 + mainLength * trailFriction / (2.0 * mainRadius)))
                .coerceIn(DRIPS_MIN_ADHESION_RATIO, DRIPS_MAX_ADHESION_RATIO)
            val travelTicks = mainLength / DRIPS_AVERAGE_SPEED
            val particleId = nextParticleId()
            val area = PI * mainRadius * mainRadius
            val excessDensity = dripDensity - mediumDensity
            val adhesion = area * gravity * excessDensity * adhesionRatio
            particles += Particle(
                lifecycle = ParticleLifecycle(delay = groupDelay, lifetime = travelTicks + DRIPS_HOLD_TICKS),
                appearance = ParticleAppearance(color = color),
                dimensions = CircleDimensions(radius = mainRadius),
                transform = ParticleTransform(
                    position = ParticleVector(
                        sourceCenterX,
                        sourceY,
                        Particle.isolatedPlaneZ(particleId, physicsSettingsService.collisionPlaneThickness)
                    ),
                    origin = ParticleVector(sourceCenterX, sourceY)
                ),
                material = ParticleMaterial(density = dripDensity, adhesion = adhesion),
                id = particleId
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = dripsGpuSlotGroup
}
