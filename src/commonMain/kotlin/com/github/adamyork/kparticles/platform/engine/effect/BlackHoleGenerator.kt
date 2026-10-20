package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.*
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class BlackHoleGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.BLACK_HOLE) {

    private companion object {
        private const val BLACK_HOLE_PLANE_ID: Long = 1_000_000L
        private const val BLACK_HOLE_CORE_RADIUS: Double = 5.0
        private const val BLACK_HOLE_CORE_DELAY: Double = 60.0
        private const val BLACK_HOLE_CORE_LIFETIME: Double = 2048.0
        private const val BLACK_HOLE_CORE_MASS: Double = 1.6
        private const val BLACK_HOLE_CORE_MAX_ACTIVE_PARTICLES: Int = 8
        private const val BLACK_HOLE_EXPLOSION_PARTICLE_COUNT: Int = 256
        private const val BLACK_HOLE_EXPLOSION_RADIUS: Double = 2.0
        private const val BLACK_HOLE_EXPLOSION_REACH: Double = 90.0
        private const val BLACK_HOLE_EXPLOSION_MIN_REACH_FRACTION: Double = 0.4
        private const val BLACK_HOLE_EXPLOSION_REACH_FRACTION_RANGE: Double = 0.6
        private const val BLACK_HOLE_EXPLOSION_DRAG_LENGTHS: Double = 2.0
        private const val BLACK_HOLE_EXPLOSION_SPAWN_SPACING: Double = 5.0
        private const val BLACK_HOLE_EXPLOSION_RESTITUTION: Double = 1.0
        private const val GOLDEN_ANGLE: Double = 2.399963229728653
        private const val BLACK_HOLE_EXPLOSION_LIFETIME: Double = BLACK_HOLE_CORE_LIFETIME
        private const val BLACK_HOLE_EXPLOSION_MAX_ACTIVE_PARTICLES: Int = 1024
    }

    private val blackHoleCoreGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(BLACK_HOLE_CORE_MAX_ACTIVE_PARTICLES)

    private val blackHoleExplosionGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(BLACK_HOLE_EXPLOSION_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(blackHoleCoreGpuSlotGroup, blackHoleExplosionGpuSlotGroup)

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val centerX = viewPort.x + viewPort.width / 2.0
        val centerY = viewPort.y + viewPort.height / 2.0
        val dimensions = CircleDimensions(radius = BLACK_HOLE_CORE_RADIUS)
        val blackHolePlaneZ = Particle.isolatedPlaneZ(BLACK_HOLE_PLANE_ID, physicsSettingsService.collisionPlaneThickness)
        val coreDensity = Particle.densityFor(BLACK_HOLE_CORE_MASS, dimensions)
        val coreNetGravity = Particle.netGravityOf(
            coreDensity, physicsSettingsService.gravity, physicsSettingsService.mediumDensity
        )
        particles += Particle(
            lifecycle = ParticleLifecycle(delay = BLACK_HOLE_CORE_DELAY, lifetime = BLACK_HOLE_CORE_LIFETIME),
            appearance = ParticleAppearance(color = Color.Black),
            dimensions = dimensions,
            transform = ParticleTransform(
                position = ParticleVector(centerX, centerY, blackHolePlaneZ),
                origin = ParticleVector(centerX, centerY)
            ),
            material = ParticleMaterial(density = coreDensity),
            dynamics = ParticleDynamics(thrust = ParticleVector(0.0, -BLACK_HOLE_CORE_MASS * coreNetGravity)),
            id = nextParticleId()
        )
        val mediumDensity = physicsSettingsService.mediumDensity
        val gravity = physicsSettingsService.gravity
        val linearDrag = max(physicsSettingsService.drag, Double.MIN_VALUE)
        val explosionDimensions = CircleDimensions(radius = BLACK_HOLE_EXPLOSION_RADIUS)
        val frontalLength = (explosionDimensions.boundingWidth() + explosionDimensions.boundingHeight()) / 2.0
        val targetDragRate = BLACK_HOLE_EXPLOSION_DRAG_LENGTHS / BLACK_HOLE_EXPLOSION_REACH
        val explosionDensity = max(
            mediumDensity,
            0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength / (explosionDimensions.area() * targetDragRate)
        )
        val quadraticDragRate = 0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength /
            (explosionDensity * explosionDimensions.area())
        val reachDivisor = max(quadraticDragRate, Double.MIN_VALUE) * (1.0 - exp(-linearDrag * BLACK_HOLE_CORE_DELAY))
        val holdThrustY = -explosionDensity * explosionDimensions.area() *
            Particle.netGravityOf(explosionDensity, gravity, mediumDensity)
        val spawnRadiusScale = BLACK_HOLE_EXPLOSION_SPAWN_SPACING / sqrt(PI)
        repeat(BLACK_HOLE_EXPLOSION_PARTICLE_COUNT) { explosionIndex ->
            val angle = explosionIndex * GOLDEN_ANGLE
            val spawnRadius = spawnRadiusScale * sqrt(explosionIndex + 0.5)
            val spawnX = centerX + cos(angle) * spawnRadius
            val spawnY = centerY + sin(angle) * spawnRadius
            val reachFraction = BLACK_HOLE_EXPLOSION_MIN_REACH_FRACTION +
                random.nextDouble() * BLACK_HOLE_EXPLOSION_REACH_FRACTION_RANGE
            val speed = linearDrag * expm1(quadraticDragRate * BLACK_HOLE_EXPLOSION_REACH * reachFraction) / reachDivisor
            val dimensions = CircleDimensions(radius = BLACK_HOLE_EXPLOSION_RADIUS)
            particles += Particle(
                lifecycle = ParticleLifecycle(lifetime = BLACK_HOLE_EXPLOSION_LIFETIME),
                appearance = ParticleAppearance(color = randomColor()),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(spawnX, spawnY, blackHolePlaneZ),
                    origin = ParticleVector(spawnX, spawnY)
                ),
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed),
                    thrust = ParticleVector(0.0, holdThrustY)
                ),
                material = ParticleMaterial(density = explosionDensity, restitution = BLACK_HOLE_EXPLOSION_RESTITUTION),
                id = nextParticleId()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup =
        if (isCore(particle)) blackHoleCoreGpuSlotGroup else blackHoleExplosionGpuSlotGroup

    private fun isCore(particle: Particle): Boolean = particle.lifecycle.delay > 0.0
}
