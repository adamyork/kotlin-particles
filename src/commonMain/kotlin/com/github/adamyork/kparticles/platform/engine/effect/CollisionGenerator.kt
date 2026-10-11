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
class CollisionGenerator : BaseGenerator(ParticleEffect.COLLISION) {

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
    }

    private var color: Color = Color.White

    override fun populateColor(assetService: AssetService) {
        val configuredColor = assetService.appProperties.particle.player.collision.color
        color = Color(
            configuredColor.r.toFloat() / 255f,
            configuredColor.g.toFloat() / 255f,
            configuredColor.b.toFloat() / 255f,
            configuredColor.a.toFloat() / 255f
        )
    }

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val direction = request.direction
        val particles = mutableListOf<Particle>()
        val explodeAngle = when (direction) {
            Direction.RIGHT -> 0.0
            Direction.LEFT -> PI
        }
        val collisionParticleColor = color
        val angleStep = COLLISION_EXPLOSION_ANGLE_SPAN / (COLLISION_PARTICLE_COUNT - 1)
        val startAngle = explodeAngle - COLLISION_EXPLOSION_ANGLE_SPAN / 2.0
        repeat(COLLISION_PARTICLE_COUNT) { particleIndex ->
            val radius = Random.nextDouble() * COLLISION_RADIUS_RANGE + COLLISION_MIN_RADIUS
            val diameter = radius * 2.0
            val velocityAngle = startAngle + angleStep * particleIndex
            val speed = Random.nextDouble() * COLLISION_SPEED_RANGE + COLLISION_MIN_SPEED
            particles += Particle(
                id = Random.nextInt().toString(16),
                effect = ParticleEffect.COLLISION,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = COLLISION_LIFETIME,
                color = collisionParticleColor,
                startColor = collisionParticleColor,
                endColor = collisionParticleColor,
                alpha = COLLISION_INITIAL_ALPHA,
                endAlpha = COLLISION_END_ALPHA,
                initialAlpha = COLLISION_INITIAL_ALPHA,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = radius,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                originZ = 0,
                destinationX = 0,
                destinationY = 0,
                destinationZ = 0,
                xVelocity = cos(velocityAngle) * speed,
                yVelocity = sin(velocityAngle) * speed,
                zVelocity = 0.0,
                maxXVelocity = DEFAULT_MAX_VELOCITY,
                maxYVelocity = DEFAULT_MAX_VELOCITY,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = COLLISION_DRAG,
                mass = radius * COLLISION_MASS_SCALE,
                restitution = COLLISION_RESTITUTION,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false
            )
        }

        return particles
    }
}
