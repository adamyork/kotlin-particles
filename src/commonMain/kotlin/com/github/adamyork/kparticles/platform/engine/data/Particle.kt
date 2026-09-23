package com.github.adamyork.kparticles.platform.engine.data

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.common.data.ViewPort

data class Particle(
    val id: String,
    val type: ParticleType,
    val shape: ParticleShape,
    var age: Double,
    val delay: Double,
    val lifetime: Double,
    var color: Color,
    val startColor: Color,
    val endColor: Color,
    var alpha: Double,
    val endAlpha: Double?,
    var width: Double,
    var height: Double,
    val maxWidth: Double,
    val maxHeight: Double,
    var radius: Double,
    val maxRadius: Double,
    val growthRate: Double,
    var x: Double,
    var y: Double,
    var z: Double,
    val originX: Double,
    val originY: Double,
    val originZ: Double,
    val destinationX: Double,
    val destinationY: Double,
    val destinationZ: Double,
    var xVelocity: Double,
    var yVelocity: Double,
    var zVelocity: Double,
    val maxXVelocity: Double,
    val maxYVelocity: Double,
    val maxZVelocity: Double,
    var xAcceleration: Double,
    var yAcceleration: Double,
    var zAcceleration: Double,
    val drag: Double,
    val mass: Double?,
    val restitution: Double,
    val canCollide: Boolean,
    var isVisible: Boolean,
    val initialAlpha: Double?
) {
    companion object {
        private const val VISIBILITY_BUFFER = 50
    }

    fun cullingCheck(viewPort: ViewPort): Boolean {
        val localCord = viewPort.globalToLocal(x.toInt(), y.toInt())
        return localCord.y < viewPort.height &&
                localCord.y > -VISIBILITY_BUFFER &&
                localCord.x > -VISIBILITY_BUFFER &&
                localCord.x < viewPort.width + VISIBILITY_BUFFER
    }
}

