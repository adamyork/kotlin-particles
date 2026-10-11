package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.data.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class BaseItemReturnGenerator(effect: ParticleEffect) : BaseGenerator(effect) {

    private companion object {
        private const val ITEM_RETURN_RADIUS: Double = 16.0
        private const val ITEM_RETURN_LIFETIME: Double = 512.0
        private const val ITEM_RETURN_MASS: Double = 0.15
        private const val ITEM_RETURN_DRAG: Double = 0.001
        private const val ANIMATED_ITEM_RETURN_MAX_FRAMES: Int = 8
    }

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val centerX = request.x
        val centerY = request.y
        val destinationX = request.destinationX
        val destinationY = request.destinationY
        val effect = effect
        val launchAngle = atan2(destinationY - centerY, destinationX - centerX)
        val diameter = ITEM_RETURN_RADIUS * 2.0
        val maxFrames = if (effect == ParticleEffect.ANIMATED_ITEM_RETURN) ANIMATED_ITEM_RETURN_MAX_FRAMES else 1

        return mutableListOf(
            Particle(
                id = Random.nextInt().toString(16),
                effect = effect,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = ITEM_RETURN_LIFETIME,
                color = Color.White,
                startColor = Color.White,
                endColor = Color.White,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = ITEM_RETURN_RADIUS,
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
                drag = ITEM_RETURN_DRAG,
                mass = ITEM_RETURN_MASS,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false,
                frame = 0,
                maxFrames = maxFrames
            )
        )
    }
}
