package com.github.adamyork.kparticles.platform.engine.data

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class Particle(
    val id: String = Random.nextInt().toString(16),
    val effect: ParticleEffect,
    val shape: ParticleShape,
    var age: Double = 0.0,
    val delay: Double = 0.0,
    val lifetime: Double,
    var color: Color,
    val startColor: Color = color,
    val endColor: Color = color,
    var alpha: Double = 1.0,
    val endAlpha: Double = alpha,
    var initialAlpha: Double = alpha,
    var alphaMultiplier: Double = 0.0,
    var width: Double,
    var height: Double,
    val maxWidth: Double = DEFAULT_MAX_WIDTH,
    val maxHeight: Double = DEFAULT_MAX_HEIGHT,
    var radius: Double,
    val maxRadius: Double = DEFAULT_MAX_RADIUS,
    val growthRate: Double = 0.0,
    var x: Double,
    var y: Double,
    var z: Double = 0.0,
    val originX: Int = 0,
    val originY: Int = 0,
    val originZ: Int = 0,
    var destinationX: Int = 0,
    var destinationY: Int = 0,
    val destinationZ: Int = 0,
    var xVelocity: Double = 0.0,
    var yVelocity: Double = 0.0,
    var zVelocity: Double = 0.0,
    val maxXVelocity: Double = DEFAULT_MAX_VELOCITY,
    val maxYVelocity: Double = DEFAULT_MAX_VELOCITY,
    val maxZVelocity: Double = DEFAULT_MAX_VELOCITY,
    var xAcceleration: Double = 0.0,
    var yAcceleration: Double = 0.0,
    var zAcceleration: Double = 0.0,
    val drag: Double = 0.0,
    var mass: Double = 0.0,
    val restitution: Double = 0.0,
    var attraction: Double = 0.0,
    val canCollide: Boolean = false,
    var visible: Boolean = true,
    val viewportBound: Boolean = false,
    val hardness: Int = 0,
    var xForce: Double = 0.0,
    var yForce: Double = 0.0,
    var gpuReservedSlot: Int = -1,
    var frame: Int = 0,
    val maxFrames: Int = 1,
    val driftAmplitude: Double = 0.0,
    val driftFrequency: Double = 0.0,
    val driftPhase: Double = 0.0,
    val travelTicks: Double = 0.0
) {
    companion object {
        const val DEFAULT_MAX_WIDTH: Double = 10.0
        const val DEFAULT_MAX_HEIGHT: Double = 10.0
        const val DEFAULT_MAX_RADIUS: Double = 5.0
        const val DEFAULT_MAX_VELOCITY: Double = 10.0
        private const val VISIBILITY_BUFFER = 50
    }

    fun cullingCheck(viewPort: ViewPort): Boolean {
        val localCord = viewPort.globalToLocal(x.toInt(), y.toInt())
        return localCord.y < viewPort.height &&
                localCord.y > -VISIBILITY_BUFFER &&
                localCord.x > -VISIBILITY_BUFFER &&
                localCord.x < viewPort.width + VISIBILITY_BUFFER
    }

    fun travelProgress(): Double {
        val travelDistance = hypot((destinationX - originX).toDouble(), (destinationY - originY).toDouble())
        if (travelDistance <= 0.0 || travelTicks <= 0.0) return 1.0
        val travelTime = ((age - delay) / travelTicks).coerceIn(0.0, 1.0)
        return travelTime * travelTime * (3.0 - 2.0 * travelTime)
    }

    fun canInteract(): Boolean = canCollide || attraction > 0.0
}

