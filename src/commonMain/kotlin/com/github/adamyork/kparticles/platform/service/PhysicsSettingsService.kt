package com.github.adamyork.kparticles.platform.service

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface PhysicsSettingsService {

    fun deriveGravityFromViewportHeight(viewportHeight: Int, ticksPerSecond: Int)

    var gravity: Double
    var gravityMetersPerSecondSquared: Double
    var viewportHeightMeters: Double
    var mediumDensity: Double
    var boundaryZ: Double
    var collisionPlaneThickness: Double
    var gravityPlaneThickness: Double
    var boundaryDepth: Double
    var wallRestitution: Double
    var gravitationalConstant: Double
    var gravitationalSoftening: Double
    var gravitySourceCount: Int
    var gravityMinimumAcceleration: Double
    var accretionMassRatio: Double
    var friction: Double
    var drag: Double
    var dragCoefficient: Double
    var flowAmplitude: Double
    var flowFrequency: Double
    var flowWavelength: Double
    var windVelocity: Double
    var minActiveVelocity: Double
    var minActiveVelocityPerGravity: Double
    var spatialGridCellSize: Double
    var restStepsBeforeSleep: Int
    var positionCorrectionFactor: Double
    var positionCorrectionSlop: Double
    var maxSubframeDeltaTime: Double
    var maxSubframeCount: Int
    var maxFrameDeltaTime: Double
    var shatterMinimumFragmentRadius: Double
    var shatterMinimumFragmentCount: Int
    var shatterMaximumFragmentCount: Int
    var shatterToughness: Double
    var spriteExplodePixelStride: Int
    var dripTrailRadiusRatio: Double
    var dripNeckLengthRatio: Double
    var dripGlossIntensity: Double
    var dripGlossStripeOffsetRatio: Double
    var dripGlossStripeWidthRatio: Double
    var dripGlossSpecularRadiusRatio: Double
    var dripGlossSpecularOffsetRatio: Double
}
