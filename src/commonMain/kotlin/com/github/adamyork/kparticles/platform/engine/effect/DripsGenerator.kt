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

    private class DripSegment(
        val originX: Double,
        val originY: Double,
        val destinationX: Double,
        val destinationY: Double,
        val radius: Double,
        val delay: Double,
        val travelTicks: Double
    )

    private companion object {
        private const val DRIPS_GROUP_COUNT: Int = 10
        private const val DRIPS_MAX_BRANCH_COUNT: Int = 10
        private const val DRIPS_BRANCH_MIN_RADIUS: Double = 1.5
        private const val DRIPS_BRANCH_MIN_LENGTH: Double = 12.0
        private const val DRIPS_GROUP_STAGGER_TICKS: Double = 240.0
        private const val DRIPS_START_BAND_RATIO: Double = 0.25
        private const val DRIPS_SOURCE_RADIUS_RATIO: Double = 1.5
        private const val DRIPS_MAIN_MIN_RADIUS: Double = 6.0
        private const val DRIPS_MAIN_RADIUS_RANGE: Double = 4.0
        private const val DRIPS_MAIN_MIN_LENGTH: Double = 80.0
        private const val DRIPS_MAIN_LENGTH_RANGE: Double = 90.0
        private const val DRIPS_MAIN_MAX_LEAN: Double = 0.12
        private const val DRIPS_BRANCH_RADIUS_RATIO: Double = 0.5
        private const val DRIPS_BRANCH_LENGTH_RATIO: Double = 0.6
        private const val DRIPS_BRANCH_MIN_PROTRUSION: Double = 1.0
        private const val DRIPS_BRANCH_PROTRUSION_RANGE: Double = 1.0
        private const val DRIPS_SPEED_VARIATION: Double = 0.3
        private const val DRIPS_DROPLET_COUNT: Int = 1
        private const val DRIPS_DROPLET_RADIUS_RATIO: Double = 0.35
        private const val DRIPS_DROPLET_MIN_OFFSET: Double = 24.0
        private const val DRIPS_DROPLET_OFFSET_RANGE: Double = 20.0
        private const val DRIPS_DROPLET_X_JITTER: Double = 6.0
        private const val DRIPS_HOLD_TICKS: Double = 3600.0
        private const val DRIPS_MAX_ACTIVE_PARTICLES: Int = 256
        private const val DRIPS_GPU_KIND: Float = 15f
    }

    private val dripsGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(DRIPS_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(dripsGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        repeat(DRIPS_GROUP_COUNT) {
            val color = randomColor()
            val groupDelay = Random.nextDouble() * DRIPS_GROUP_STAGGER_TICKS
            val mainRadius = DRIPS_MAIN_MIN_RADIUS + Random.nextDouble() * DRIPS_MAIN_RADIUS_RANGE
            val sourceCenterX = viewPort.x + Random.nextDouble() * viewPort.width
            val sourceY = viewPort.y + Random.nextDouble() * viewPort.height * DRIPS_START_BAND_RATIO
            val segments = mutableListOf<DripSegment>()
            segments += DripSegment(
                sourceCenterX, sourceY, sourceCenterX, sourceY, mainRadius * DRIPS_SOURCE_RADIUS_RATIO, groupDelay, 0.0
            )
            val mainLength = DRIPS_MAIN_MIN_LENGTH + Random.nextDouble() * DRIPS_MAIN_LENGTH_RANGE
            val mainLean = (Random.nextDouble() * 2.0 - 1.0) * DRIPS_MAIN_MAX_LEAN
            val mainDestinationX = sourceCenterX + sin(mainLean) * mainLength
            val mainDestinationY = sourceY + cos(mainLean) * mainLength
            val mainDelay = groupDelay
            val mainTravelTicks = mainLength / randomSpeed()
            segments += DripSegment(
                sourceCenterX, sourceY, mainDestinationX, mainDestinationY, mainRadius, mainDelay, mainTravelTicks
            )
            var branchOriginX = mainDestinationX
            var branchOriginY = mainDestinationY
            var branchRadius = mainRadius
            var branchLength = mainLength
            var branchDelay = mainDelay + mainTravelTicks
            repeat(1 + Random.nextInt(DRIPS_MAX_BRANCH_COUNT)) {
                val parentRadius = branchRadius
                branchRadius = max(branchRadius * DRIPS_BRANCH_RADIUS_RATIO, DRIPS_BRANCH_MIN_RADIUS)
                branchLength = max(branchLength * DRIPS_BRANCH_LENGTH_RATIO, DRIPS_BRANCH_MIN_LENGTH)
                val branchDirection = if (Random.nextBoolean()) 1.0 else -1.0
                val branchProtrusion = DRIPS_BRANCH_MIN_PROTRUSION + Random.nextDouble() * DRIPS_BRANCH_PROTRUSION_RANGE
                val branchStartX = branchOriginX + branchDirection * (parentRadius + branchProtrusion - branchRadius)
                val branchDestinationY = branchOriginY + branchLength
                val branchTravelTicks = branchLength / randomSpeed()
                segments += DripSegment(
                    branchStartX, branchOriginY, branchStartX, branchDestinationY, branchRadius, branchDelay, branchTravelTicks
                )
                branchOriginX = branchStartX
                branchOriginY = branchDestinationY
                branchDelay += branchTravelTicks
            }
            repeat(DRIPS_DROPLET_COUNT) {
                val dropletX = branchOriginX + (Random.nextDouble() * 2.0 - 1.0) * DRIPS_DROPLET_X_JITTER
                val dropletY = branchOriginY + DRIPS_DROPLET_MIN_OFFSET + Random.nextDouble() * DRIPS_DROPLET_OFFSET_RANGE
                segments += DripSegment(
                    dropletX, dropletY, dropletX, dropletY, mainRadius * DRIPS_DROPLET_RADIUS_RATIO, branchDelay, 0.0
                )
            }
            val groupEndTicks = segments.maxOf { it.delay + it.travelTicks }
            for (segment in segments) {
                val diameter = segment.radius * 2.0
                particles += Particle(
                    effect = ParticleEffect.DRIPS,
                    shape = ParticleShape.CIRCLE,
                    delay = segment.delay,
                    lifetime = groupEndTicks - segment.delay + DRIPS_HOLD_TICKS,
                    color = color,
                    width = diameter,
                    height = diameter,
                    radius = segment.radius,
                    x = segment.originX,
                    y = segment.originY,
                    originX = segment.originX.roundToInt(),
                    originY = segment.originY.roundToInt(),
                    destinationX = segment.destinationX.roundToInt(),
                    destinationY = segment.destinationY.roundToInt(),
                    gpuReservedSlot = dripsGpuSlotGroup.claimNextSlot(),
                    travelTicks = segment.travelTicks
                )
            }
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = dripsGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = DRIPS_GPU_KIND

    override fun gpuSpawnOffset(particle: Particle, size: Float): Float = 0f

    override fun gpuVelocityX(particle: Particle): Float = particle.destinationX.toFloat()

    override fun gpuVelocityY(particle: Particle): Float = particle.destinationY.toFloat()

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex] = particle.delay.toFloat()
        targetBuffer[parameterIndex + 1] = particle.travelTicks.toFloat()
    }

    private fun randomSpeed(): Double {
        val variation = 1.0 + (Random.nextDouble() * 2.0 - 1.0) * DRIPS_SPEED_VARIATION
        return physicsSettingsService.dripSpeed * variation
    }
}
