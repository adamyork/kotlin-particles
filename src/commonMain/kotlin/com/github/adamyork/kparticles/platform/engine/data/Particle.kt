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
    val endAlpha: Double,
    var initialAlpha: Double,
    var alphaMultiplier: Double,
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
    val originX: Int,
    val originY: Int,
    val originZ: Int,
    var destinationX: Int,
    var destinationY: Int,
    val destinationZ: Int,
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
    var mass: Double,
    val restitution: Double,
    var attraction: Double,
    val canCollide: Boolean,
    var visible: Boolean,
    val viewportBound: Boolean
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

