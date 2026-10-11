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
class DustGenerator : BaseGenerator(ParticleEffect.DUST) {

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
    }

    private var color: Color = Color.White

    override fun populateColor(assetService: AssetService) {
        val configuredColor = assetService.appProperties.particle.player.movement.color
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

    private fun createDustPuff(x: Double, y: Double, radius: Double): Particle {
        val destinationAngle = Random.nextDouble() * PI * 2
        val destinationDistance = DUST_MIN_TRAVEL_DISTANCE + Random.nextDouble() * DUST_TRAVEL_DISTANCE_RANGE
        val destinationX = x + cos(destinationAngle) * destinationDistance
        val destinationY = y + sin(destinationAngle) * destinationDistance
        val deltaX = destinationX - x
        val deltaY = destinationY - y
        val length = hypot(deltaX, deltaY).takeIf { it > 0 } ?: 1.0
        val speed = DUST_MIN_SPEED + Random.nextDouble() * DUST_SPEED_RANGE
        val dustPuffColor = color
        val diameter = radius * 2.0
        return Particle(
            id = Random.nextInt().toString(16),
            effect = ParticleEffect.DUST,
            shape = ParticleShape.CIRCLE,
            age = 0.0,
            delay = 0.0,
            lifetime = DUST_LIFETIME,
            color = dustPuffColor,
            startColor = dustPuffColor,
            endColor = dustPuffColor,
            alpha = DUST_INITIAL_ALPHA,
            endAlpha = DUST_END_ALPHA,
            initialAlpha = DUST_INITIAL_ALPHA,
            alphaMultiplier = 0.0,
            width = diameter,
            height = diameter,
            maxWidth = DEFAULT_MAX_WIDTH,
            maxHeight = DEFAULT_MAX_HEIGHT,
            radius = radius,
            maxRadius = DEFAULT_MAX_RADIUS,
            growthRate = 0.0,
            x = x,
            y = y,
            z = 0.0,
            originX = x.roundToInt(),
            originY = y.roundToInt(),
            originZ = 0,
            destinationX = destinationX.roundToInt(),
            destinationY = destinationY.roundToInt(),
            destinationZ = 0,
            xVelocity = (deltaX / length) * speed,
            yVelocity = (deltaY / length) * speed,
            zVelocity = 0.0,
            maxXVelocity = DEFAULT_MAX_VELOCITY,
            maxYVelocity = DEFAULT_MAX_VELOCITY,
            maxZVelocity = DEFAULT_MAX_VELOCITY,
            xAcceleration = 0.0,
            yAcceleration = 0.0,
            zAcceleration = 0.0,
            drag = 0.0,
            mass = 0.0,
            restitution = 0.0,
            attraction = 0.0,
            canCollide = false,
            visible = true,
            viewportBound = false
        )
    }
}
