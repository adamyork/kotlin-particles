package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AssetService
import kotlin.math.*
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class DustGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.DUST) {

    private companion object {
        private const val DUST_PUFF_COUNT: Int = 16
        private const val DUST_CLUSTER_RADIUS: Double = 12.0
        private const val DUST_CENTER_PUFF_RADIUS: Double = 9.0
        private const val DUST_PUFF_MIN_RADIUS: Double = 6.0
        private const val DUST_PUFF_RADIUS_RANGE: Double = 5.0
        private const val DUST_MIN_TRAVEL_DISTANCE: Double = 8.0
        private const val DUST_TRAVEL_DISTANCE_RANGE: Double = 4.0
        private const val DUST_MIN_SPEED: Double = 0.06
        private const val DUST_SPEED_RANGE: Double = 0.04
        private const val DUST_LIFETIME: Double = 128.0
        private const val DUST_INITIAL_ALPHA: Double = 0.85
        private const val DUST_END_ALPHA: Double = 0.0
        private const val DUST_GPU_KIND: Float = 1f
    }

    private var color: Color = Color.White

    override fun populateColor(assetService: AssetService) {
        color = createColor(assetService.appProperties.particle.player.movement.color)
    }

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val particles = mutableListOf<Particle>()
        repeat(DUST_PUFF_COUNT) { puffIndex ->
            val angle = (puffIndex.toDouble() / DUST_PUFF_COUNT) * PI * 2
            val distanceFromCenter = Random.nextDouble() * (DUST_CLUSTER_RADIUS * 0.6) + (DUST_CLUSTER_RADIUS * 0.3)
            val offsetX = cos(angle) * distanceFromCenter
            val offsetY = sin(angle) * distanceFromCenter
            val radius = Random.nextDouble() * DUST_PUFF_RADIUS_RANGE + DUST_PUFF_MIN_RADIUS
            particles += createDustPuff(centerX + offsetX, centerY + offsetY, radius)
        }
        particles += createDustPuff(centerX, centerY, DUST_CENTER_PUFF_RADIUS)
        return particles
    }

    override fun gpuKind(particle: Particle): Float = DUST_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex + 1] = particle.appearance.endAlpha.toFloat()
    }

    private fun createDustPuff(x: Double, y: Double, radius: Double): Particle {
        val destinationAngle = Random.nextDouble() * PI * 2
        val destinationDistance = DUST_MIN_TRAVEL_DISTANCE + Random.nextDouble() * DUST_TRAVEL_DISTANCE_RANGE
        val destinationX = x + cos(destinationAngle) * destinationDistance
        val destinationY = y + sin(destinationAngle) * destinationDistance
        val deltaX = destinationX - x
        val deltaY = destinationY - y
        val length = hypot(deltaX, deltaY).takeIf { it > 0 } ?: 1.0
        val speed = DUST_MIN_SPEED + Random.nextDouble() * DUST_SPEED_RANGE
        val particleId = nextParticleId()
        return Particle(
            id = particleId,
            lifecycle = ParticleLifecycle(lifetime = DUST_LIFETIME),
            appearance = ParticleAppearance(color = color, alpha = DUST_INITIAL_ALPHA, endAlpha = DUST_END_ALPHA),
            dimensions = CircleDimensions(radius = radius),
            transform = ParticleTransform(
                position = ParticleVector(
                    x, y, Particle.isolatedPlaneZ(particleId, physicsSettingsService.collisionPlaneThickness)
                ),
                origin = ParticleVector(x, y)
            ),
            dynamics = ParticleDynamics(velocity = ParticleVector((deltaX / length) * speed, (deltaY / length) * speed)),
            material = ParticleMaterial(density = physicsSettingsService.mediumDensity)
        )
    }
}
