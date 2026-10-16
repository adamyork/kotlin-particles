package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
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
class BlackHoleGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.BLACK_HOLE) {

    private companion object {
        private const val BLACK_HOLE_PLANE_ID: Long = 1_000_000L
        private const val BLACK_HOLE_CORE_RADIUS: Double = 5.0
        private const val BLACK_HOLE_CORE_DELAY: Double = 60.0
        private const val BLACK_HOLE_CORE_LIFETIME: Double = 2048.0
        private const val BLACK_HOLE_CORE_MASS: Double = 100.0
        private const val BLACK_HOLE_CORE_MAX_ACTIVE_PARTICLES: Int = 8
        private const val BLACK_HOLE_CORE_GPU_KIND: Float = 9f
        private const val BLACK_HOLE_EXPLOSION_PARTICLE_COUNT: Int = 256
        private const val BLACK_HOLE_EXPLOSION_RADIUS: Double = 2.0
        private const val BLACK_HOLE_EXPLOSION_MIN_SPEED: Double = 1.5
        private const val BLACK_HOLE_EXPLOSION_SPEED_RANGE: Double = 4.5
        private const val BLACK_HOLE_EXPLOSION_LIFETIME: Double = BLACK_HOLE_CORE_LIFETIME
        private const val BLACK_HOLE_EXPLOSION_MASS: Double = 0.05
        private const val BLACK_HOLE_EXPLOSION_ATTRACTION: Double = 1.0
        private const val BLACK_HOLE_EXPLOSION_MAX_ACTIVE_PARTICLES: Int = 1024
        private const val BLACK_HOLE_EXPLOSION_GPU_KIND: Float = 10f
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
        repeat(BLACK_HOLE_EXPLOSION_PARTICLE_COUNT) {
            val angle = Random.nextDouble() * PI * 2
            val speed = BLACK_HOLE_EXPLOSION_MIN_SPEED + Random.nextDouble() * BLACK_HOLE_EXPLOSION_SPEED_RANGE
            val dimensions = CircleDimensions(radius = BLACK_HOLE_EXPLOSION_RADIUS)
            particles += Particle(
                lifecycle = ParticleLifecycle(lifetime = BLACK_HOLE_EXPLOSION_LIFETIME),
                appearance = ParticleAppearance(color = randomColor()),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(centerX, centerY, blackHolePlaneZ),
                    origin = ParticleVector(centerX, centerY)
                ),
                dynamics = ParticleDynamics(velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed)),
                material = ParticleMaterial(density = physicsSettingsService.mediumDensity),
                id = nextParticleId()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup =
        if (isCore(particle)) blackHoleCoreGpuSlotGroup else blackHoleExplosionGpuSlotGroup

    override fun gpuKind(particle: Particle): Float =
        if (isCore(particle)) BLACK_HOLE_CORE_GPU_KIND else BLACK_HOLE_EXPLOSION_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        val firstOffset = if (isCore(particle)) 0 else 2
        targetBuffer[parameterIndex + firstOffset] =
            (if (isCore(particle)) BLACK_HOLE_CORE_MASS else BLACK_HOLE_EXPLOSION_MASS).toFloat()
        targetBuffer[parameterIndex + firstOffset + 1] =
            if (isCore(particle)) particle.transform.origin.x.toFloat() * 2f else BLACK_HOLE_EXPLOSION_ATTRACTION.toFloat()
    }

    private fun isCore(particle: Particle): Boolean = particle.lifecycle.delay > 0.0
}
