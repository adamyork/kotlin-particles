package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AssetService
import kotlin.math.*
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CollisionGenerator : AbstractGenerator(ParticleEffect.COLLISION) {

    private companion object {
        private const val COLLISION_PARTICLE_COUNT: Int = 256
        private const val COLLISION_EXPLOSION_ANGLE_SPAN: Double = PI
        private const val COLLISION_MIN_RADIUS: Double = 2.0
        private const val COLLISION_RADIUS_RANGE: Double = 2.5
        private const val COLLISION_MIN_SPEED: Double = 4.0
        private const val COLLISION_SPEED_RANGE: Double = 6.0
        private const val COLLISION_LIFETIME: Double = 240.0
        private const val COLLISION_INITIAL_ALPHA: Double = 0.95
        private const val COLLISION_END_ALPHA: Double = 1.0
        private const val COLLISION_MASS_SCALE: Double = 0.04
        private const val COLLISION_RESTITUTION: Double = 0.8
        private const val COLLISION_DRAG: Double = 0.01
        private const val COLLISION_GPU_KIND: Float = 6f
    }

    private var color: Color = Color.White

    override fun populateColor(assetService: AssetService) {
        color = createColor(assetService.appProperties.particle.player.collision.color)
    }

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val particles = mutableListOf<Particle>()
        val explodeAngle = when (request.direction) {
            Direction.RIGHT -> 0.0
            Direction.LEFT -> PI
        }
        val angleStep = COLLISION_EXPLOSION_ANGLE_SPAN / (COLLISION_PARTICLE_COUNT - 1)
        val startAngle = explodeAngle - COLLISION_EXPLOSION_ANGLE_SPAN / 2.0
        repeat(COLLISION_PARTICLE_COUNT) { particleIndex ->
            val radius = Random.nextDouble() * COLLISION_RADIUS_RANGE + COLLISION_MIN_RADIUS
            val diameter = radius * 2.0
            val velocityAngle = startAngle + angleStep * particleIndex
            val speed = Random.nextDouble() * COLLISION_SPEED_RANGE + COLLISION_MIN_SPEED
            particles += Particle(
                effect = ParticleEffect.COLLISION,
                shape = ParticleShape.CIRCLE,
                lifetime = COLLISION_LIFETIME,
                color = color,
                alpha = COLLISION_INITIAL_ALPHA,
                endAlpha = COLLISION_END_ALPHA,
                width = diameter,
                height = diameter,
                radius = radius,
                x = centerX,
                y = centerY,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                xVelocity = cos(velocityAngle) * speed,
                yVelocity = sin(velocityAngle) * speed,
                drag = COLLISION_DRAG,
                mass = radius * COLLISION_MASS_SCALE,
                restitution = COLLISION_RESTITUTION
            )
        }
        return particles
    }

    override fun gpuKind(particle: Particle): Float = COLLISION_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        targetBuffer[parameterIndex + 1] = particle.endAlpha.toFloat()
    }
}
