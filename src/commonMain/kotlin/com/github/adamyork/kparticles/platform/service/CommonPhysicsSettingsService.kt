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

    override var maxXVelocity: Double = 6.25
    override var maxYVelocity: Double = 40.0
    override var jumpDistance: Double = 256.0
    override var gravity: Double = 8.25
    override var friction: Double = 0.82
    override var drag: Double = 1.0
    override var yVelocityCoefficient: Double = 0.25
    override var xMovementDistance: Double = 1.0
    override var xAccelerationRate: Double = 2.25
    override var xDeaccelerationRate: Double = 2.0
    override var projectileSpeed: Double = 10.0
    override var collisionVelocityDecay: Double = 0.85
    override var minActiveVelocity: Double = 0.5
    override var collisionKnockbackStrength: Int = 15
    override var collisionParticleSpeedCoefficient: Double = .25
    override var collisionParticleFrameGrowthMultiplier: Double = 1.0
    override var collisionParticleSizeMultiplier: Int = 10
    override var mapItemReturnParticleMinTravelDist: Double = 5.0
    override var mapItemReturnParticleSpeed: Double = 30.0
    override var dustParticleSpeedCoefficient: Double = .25

}
