package com.github.adamyork.particles.service

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface ParticlePhysicsSettingsService {

    public fun deriveGravityFromViewportHeight(viewportHeight: Int, ticksPerSecond: Int)

    public var gravity: Double
    public var gravityMetersPerSecondSquared: Double
    public var viewportHeightMeters: Double
    public var mediumDensity: Double
    public var boundaryZ: Double
    public var collisionPlaneThickness: Double
    public var gravityPlaneThickness: Double
    public var boundaryDepth: Double
    public var wallRestitution: Double
    public var gravitationalConstant: Double
    public var gravitationalSoftening: Double
    public var gravitySourceCount: Int
    public var gravityMinimumAcceleration: Double
    public var accretionMassRatio: Double
    public var friction: Double
    public var drag: Double
    public var dragCoefficient: Double
    public var flowAmplitude: Double
    public var flowFrequency: Double
    public var flowWavelength: Double
    public var windVelocity: Double
    public var minActiveVelocity: Double
    public var minActiveVelocityPerGravity: Double
    public var spatialGridCellSize: Double
    public var restStepsBeforeSleep: Int
    public var positionCorrectionFactor: Double
    public var positionCorrectionSlop: Double
    public var maxSubframeDeltaTime: Double
    public var maxSubframeCount: Int
    public var maxFrameDeltaTime: Double
    public var shatterMinimumFragmentRadius: Double
    public var shatterMinimumFragmentCount: Int
    public var shatterMaximumFragmentCount: Int
    public var shatterToughness: Double
    public var spriteExplodePixelStride: Int
}
