package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AssetService
import kotlin.math.*
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class BaseProjectileGenerator(effect: ParticleEffect) : BaseGenerator(effect) {

    private companion object {
        private const val PROJECTILE_RADIUS: Double = 16.0
        private const val PROJECTILE_LIFETIME: Double = 512.0
        private const val PROJECTILE_MASS: Double = 0.15
        private const val PROJECTILE_DRAG: Double = 0.001
    }

    private var color: Color = Color.White

    override fun populateColor(assetService: AssetService) {
        val configuredColor = assetService.appProperties.particle.enemy.projectile.color
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
        val destinationX = request.destinationX
        val destinationY = request.destinationY
        val particleType = effect
        val launchAngle = atan2(destinationY - centerY, destinationX - centerX)
        val projectileParticleColor = color
        val diameter = PROJECTILE_RADIUS * 2.0
        return mutableListOf(
            Particle(
                id = Random.nextInt().toString(16),
                effect = particleType,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = PROJECTILE_LIFETIME,
                color = projectileParticleColor,
                startColor = projectileParticleColor,
                endColor = projectileParticleColor,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = PROJECTILE_RADIUS,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                originZ = 0,
                destinationX = destinationX.roundToInt(),
                destinationY = destinationY.roundToInt(),
                destinationZ = 0,
                xVelocity = cos(launchAngle) * GUIDED_LAUNCH_INITIAL_SPEED,
                yVelocity = sin(launchAngle) * GUIDED_LAUNCH_INITIAL_SPEED,
                zVelocity = 0.0,
                maxXVelocity = abs(cos(launchAngle) * GUIDED_LAUNCH_MAX_SPEED) + GUIDED_LAUNCH_VELOCITY_HEADROOM,
                maxYVelocity = abs(sin(launchAngle) * GUIDED_LAUNCH_MAX_SPEED) + GUIDED_LAUNCH_VELOCITY_HEADROOM,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = cos(launchAngle) * GUIDED_LAUNCH_THRUST,
                yAcceleration = sin(launchAngle) * GUIDED_LAUNCH_THRUST,
                zAcceleration = 0.0,
                drag = PROJECTILE_DRAG,
                mass = PROJECTILE_MASS,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false
            )
        )
    }
}
