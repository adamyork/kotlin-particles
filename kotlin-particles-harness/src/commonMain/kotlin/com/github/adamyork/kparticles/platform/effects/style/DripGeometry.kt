package com.github.adamyork.kparticles.platform.effects.style

import com.github.adamyork.particles.engine.data.Particle
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class DripGeometry(
    private val dripStyleSettings: DripStyleSettings
) {

    private companion object {
        const val STRIPE_VISIBILITY_THRESHOLD: Float = 0.5f
    }

    var originX: Float = 0f
        private set
    var originY: Float = 0f
        private set
    var headX: Float = 0f
        private set
    var headY: Float = 0f
        private set
    var beadRadius: Float = 0f
        private set
    var trailRadius: Float = 0f
        private set
    var streakLength: Float = 0f
        private set
    var directionX: Float = 0f
        private set
    var directionY: Float = 0f
        private set
    var perpendicularX: Float = 0f
        private set
    var neckLength: Float = 0f
        private set
    var tailRadius: Float = 0f
        private set
    var neckStartX: Float = 0f
        private set
    var neckStartY: Float = 0f
        private set
    var hasStripe: Boolean = false
        private set
    var stripeOffset: Float = 0f
        private set
    var stripeRadius: Float = 0f
        private set
    var specularCenterX: Float = 0f
        private set
    var specularCenterY: Float = 0f
        private set
    var specularRadius: Float = 0f
        private set

    fun update(particle: Particle, viewPortOffsetX: Float, viewPortOffsetY: Float) {
        originX = particle.transform.origin.x.toFloat() - viewPortOffsetX
        originY = particle.transform.origin.y.toFloat() - viewPortOffsetY
        headX = particle.transform.position.x.toFloat() - viewPortOffsetX
        headY = particle.transform.position.y.toFloat() - viewPortOffsetY
        beadRadius = particle.collisionRadius().toFloat()
        trailRadius = beadRadius * dripStyleSettings.dripTrailRadiusRatio.toFloat()
        streakLength = hypot(headX - originX, headY - originY)
        val hasDirection = streakLength > CpuRendererConstants.DRIP_MINIMUM_LENGTH
        directionX = if (hasDirection) (headX - originX) / streakLength else 0f
        directionY = if (hasDirection) (headY - originY) / streakLength else 1f
        perpendicularX = -directionY
        val neckSetting = max(beadRadius * dripStyleSettings.dripNeckLengthRatio.toFloat(), CpuRendererConstants.DRIP_MINIMUM_LENGTH)
        neckLength = min(streakLength, neckSetting)
        tailRadius = beadRadius + (trailRadius - beadRadius) * (streakLength / neckSetting).coerceIn(0f, 1f)
        neckStartX = headX - directionX * neckLength
        neckStartY = headY - directionY * neckLength
        hasStripe = streakLength - neckLength > STRIPE_VISIBILITY_THRESHOLD
        stripeOffset = trailRadius * dripStyleSettings.dripGlossStripeOffsetRatio.toFloat()
        stripeRadius = trailRadius * dripStyleSettings.dripGlossStripeWidthRatio.toFloat()
        val specularOffset = beadRadius * dripStyleSettings.dripGlossSpecularOffsetRatio.toFloat()
        specularCenterX = headX - directionX * specularOffset + perpendicularX * specularOffset
        specularCenterY = headY - directionY * specularOffset + directionX * specularOffset
        specularRadius = max(beadRadius * dripStyleSettings.dripGlossSpecularRadiusRatio.toFloat(), CpuRendererConstants.DRIP_MINIMUM_LENGTH)
    }

}
