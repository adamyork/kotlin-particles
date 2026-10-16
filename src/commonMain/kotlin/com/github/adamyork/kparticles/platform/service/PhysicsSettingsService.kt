package com.github.adamyork.kparticles.platform.service

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface PhysicsSettingsService {

    var maxXVelocity: Double
    var maxYVelocity: Double
    var maxParticleSpeed: Double
    var jumpDistance: Double
    var gravity: Double
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
    var flowAmplitude: Double
    var flowFrequency: Double
    var flowWavelength: Double
    var yVelocityCoefficient: Double
    var xMovementDistance: Double
    var xAccelerationRate: Double
    var xDeaccelerationRate: Double
    var projectileSpeed: Double
    var collisionVelocityDecay: Double
    var minActiveVelocity: Double
    var collisionKnockbackStrength: Int
    var collisionParticleSpeedCoefficient: Double
    var collisionParticleFrameGrowthMultiplier: Double
    var collisionParticleSizeMultiplier: Int
    var mapItemReturnParticleMinTravelDist: Double
    var mapItemReturnParticleSpeed: Double
    var dustParticleSpeedCoefficient: Double
    var spatialGridCellSize: Double
    var shatterMinimumFragmentRadius: Double
    var shatterMinimumFragmentCount: Int
    var shatterMaximumFragmentCount: Int
    var shatterMomentumPerFragment: Double
    var shatterSpreadSpeed: Double
    var shatterMaxVelocity: Double
    var dripTrailRadiusRatio: Double
    var dripNeckLengthRatio: Double
    var dripGlossIntensity: Double
    var dripGlossStripeOffsetRatio: Double
    var dripGlossStripeWidthRatio: Double
    var dripGlossSpecularRadiusRatio: Double
    var dripGlossSpecularOffsetRatio: Double
}
