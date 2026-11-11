package com.github.adamyork.particles.service

import com.github.adamyork.particles.common.ParticleEngineLifeCycleState
import com.github.adamyork.particles.engine.data.ParticleSubframeSchedule

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface RuntimeService {

    public var particleEngineLifeCycleState: ParticleEngineLifeCycleState
    public var lastPaintTime: Double
    public val physicsSettingsService: ParticlePhysicsSettingsService

    public fun deriveGravityFromViewportHeight(viewportHeight: Int)

    public fun getDeltaTimeCoefficient(): Double

    public fun getSubframeSchedule(): ParticleSubframeSchedule

    public fun getDeltaTimeSeconds(): Float

    public fun getSpriteTicksPerFrame(): Double

    public fun getFps(): Double

    public fun setCurrentFrameTime(timestamp: Double)

    public fun getCurrentFrameTime(): Double


}


