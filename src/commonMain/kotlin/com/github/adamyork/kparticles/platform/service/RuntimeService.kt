package com.github.adamyork.kparticles.platform.service

import com.github.adamyork.kparticles.platform.common.LifeCycleState
import com.github.adamyork.kparticles.platform.engine.data.SubstepPlan

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface RuntimeService {

    var lifeCycleState: LifeCycleState
    var lastPaintTime: Double

    fun deriveGravityFromViewportHeight(viewportHeight: Int)

    fun getDeltaTimeCoefficient(): Double

    fun getSubstepPlan(): SubstepPlan

    fun getDeltaTimeSeconds(): Float

    fun getFps(): Double

    fun setCurrentFrameTime(timestamp: Double)

    fun getCurrentFrameTime(): Double


}


