package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.*
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class DripsGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.DRIPS) {

    private companion object {
        private const val DRIPS_GROUP_COUNT: Int = 10
        private const val DRIPS_GROUP_STAGGER_TICKS: Double = 240.0
        private const val DRIPS_START_BAND_RATIO: Double = 0.25
        private const val DRIPS_MAIN_MIN_RADIUS: Double = 6.0
        private const val DRIPS_MAIN_RADIUS_RANGE: Double = 4.0
        private const val DRIPS_MAIN_MIN_LENGTH: Double = 100.0
        private const val DRIPS_MAIN_LENGTH_RANGE: Double = 60.0
        private const val DRIPS_FULL_LENGTH_CHANCE: Double = 0.3
        private const val DRIPS_DENSITY_RATIO: Double = 1.05
        private const val DRIPS_ADHESION_RATIO: Double = 0.6
        private const val DRIPS_AVERAGE_SPEED: Double = 2.5
        private const val DRIPS_HOLD_TICKS: Double = 3600.0
        private const val DRIPS_MAX_ACTIVE_PARTICLES: Int = 256
        private const val DRIPS_GPU_KIND: Float = 15f
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
            val groupDelay = Random.nextDouble() * DRIPS_GROUP_STAGGER_TICKS
            val mainRadius = DRIPS_MAIN_MIN_RADIUS + Random.nextDouble() * DRIPS_MAIN_RADIUS_RANGE
            val sourceCenterX = viewPort.x + Random.nextDouble() * viewPort.width
            val sourceY = viewPort.y + Random.nextDouble() * viewPort.height * DRIPS_START_BAND_RATIO
            val mainLength = if (Random.nextDouble() < DRIPS_FULL_LENGTH_CHANCE) {
                max(viewPort.y + viewPort.height - sourceY, DRIPS_MAIN_MIN_LENGTH)
            } else {
                DRIPS_MAIN_MIN_LENGTH + Random.nextDouble() * DRIPS_MAIN_LENGTH_RANGE
            }
            val travelTicks = mainLength / DRIPS_AVERAGE_SPEED
            val particleId = nextParticleId()
            val area = PI * mainRadius * mainRadius
            val excessDensity = dripDensity - mediumDensity
            val adhesion = area * gravity * excessDensity * DRIPS_ADHESION_RATIO
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

    override fun gpuKind(particle: Particle): Float = DRIPS_GPU_KIND

    override fun gpuSpawnOffset(particle: Particle, size: Float): Float = 0f

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex] = particle.lifecycle.delay.toFloat()
    }
}
