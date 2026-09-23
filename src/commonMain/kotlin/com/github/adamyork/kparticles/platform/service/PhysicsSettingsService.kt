package com.github.adamyork.kparticles.platform.service

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface PhysicsSettingsService {

    var maxXVelocity: Double
    var maxYVelocity: Double
    var jumpDistance: Double
    var gravity: Double
    var friction: Double
    var drag: Double
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
}
