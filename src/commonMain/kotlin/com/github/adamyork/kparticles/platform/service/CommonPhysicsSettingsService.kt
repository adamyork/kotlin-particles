package com.github.adamyork.kparticles.platform.service

import com.github.adamyork.kparticles.platform.AppScope
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CommonPhysicsSettingsService : PhysicsSettingsService {

    override fun deriveGravityFromViewportHeight(viewportHeight: Int, ticksPerSecond: Int) {
        val pixelsPerMeter = viewportHeight / viewportHeightMeters
        val secondsPerTick = 1.0 / ticksPerSecond
        gravity = gravityMetersPerSecondSquared * pixelsPerMeter * secondsPerTick * secondsPerTick
        minActiveVelocity = gravity * minActiveVelocityPerGravity
    }

    override var maxXVelocity: Double = 6.25
    override var maxYVelocity: Double = 40.0
    override var jumpDistance: Double = 256.0
    override var gravity: Double = 0.7
    override var gravityMetersPerSecondSquared: Double = 9.81
    override var viewportHeightMeters: Double = 3.0
    override var mediumDensity: Double = 0.0001
    override var boundaryZ: Double = 0.0
    override var collisionPlaneThickness: Double = 0.5
    override var gravityPlaneThickness: Double = 0.005
    override var boundaryDepth: Double = 100.0
    override var wallRestitution: Double = 1.0
    override var gravitationalConstant: Double = 900.0
    override var gravitationalSoftening: Double = 8.0
    override var gravitySourceCount: Int = 16
    override var gravityMinimumAcceleration: Double = 0.01
    override var accretionMassRatio: Double = 4.0
    override var friction: Double = 0.1
    override var drag: Double = 0.01
    override var dragCoefficient: Double = 1.0
    override var flowAmplitude: Double = 0.1
    override var flowFrequency: Double = 0.002
    override var flowWavelength: Double = 400.0
    override var yVelocityCoefficient: Double = 0.25
    override var xMovementDistance: Double = 1.0
    override var xAccelerationRate: Double = 2.25
    override var xDeaccelerationRate: Double = 2.0
    override var projectileSpeed: Double = 10.0
    override var collisionVelocityDecay: Double = 0.85
    override var minActiveVelocity: Double = 0.04
    override var minActiveVelocityPerGravity: Double = 0.5 / 8.25
    override var collisionKnockbackStrength: Int = 15
    override var mapItemReturnParticleMinTravelDist: Double = 5.0
    override var mapItemReturnParticleSpeed: Double = 30.0
    override var dustParticleSpeedCoefficient: Double = .25
    override var spatialGridCellSize: Double = 64.0
    override var restStepsBeforeSleep: Int = 30
    override var positionCorrectionFactor: Double = 0.8
    override var positionCorrectionSlop: Double = 0.1
    override var maxSubframeDeltaTime: Double = 2.0
    override var maxSubframeCount: Int = 8
    override var maxFrameDeltaTime: Double = 60.0
    override var shatterMinimumFragmentRadius: Double = 1.0
    override var shatterMinimumFragmentCount: Int = 2
    override var shatterMaximumFragmentCount: Int = 16
    override var shatterToughness: Double = 25.0
    override var dripTrailRadiusRatio: Double = 0.45
    override var dripNeckLengthRatio: Double = 3.0
    override var dripGlossIntensity: Double = 0.6
    override var dripGlossStripeOffsetRatio: Double = 0.45
    override var dripGlossStripeWidthRatio: Double = 0.2
    override var dripGlossSpecularRadiusRatio: Double = 0.3
    override var dripGlossSpecularOffsetRatio: Double = 0.4

}
