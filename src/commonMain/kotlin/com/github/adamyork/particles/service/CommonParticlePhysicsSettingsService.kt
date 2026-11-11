package com.github.adamyork.particles.service


/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonParticlePhysicsSettingsService : ParticlePhysicsSettingsService {

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
    override var windVelocity: Double = 0.0
    override var minActiveVelocity: Double = 0.04
    override var minActiveVelocityPerGravity: Double = 0.5 / 8.25
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
    override var spriteExplodePixelStride: Int = 1

    override fun deriveGravityFromViewportHeight(viewportHeight: Int, ticksPerSecond: Int) {
        val pixelsPerMeter = viewportHeight / viewportHeightMeters
        val secondsPerTick = 1.0 / ticksPerSecond
        gravity = gravityMetersPerSecondSquared * pixelsPerMeter * secondsPerTick * secondsPerTick
        minActiveVelocity = gravity * minActiveVelocityPerGravity
    }

}
