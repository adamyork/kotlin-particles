package com.github.adamyork.kparticles.platform.service

import com.github.adamyork.kparticles.platform.common.LifeCycleState

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface RuntimeService {

    var lifeCycleState: LifeCycleState
    var lastPaintTime: Double

    fun getDeltaTimeCoefficient(): Double

    fun getDeltaTimeSeconds(): Float

    fun getFps(): Double

    fun setCurrentFrameTime(timestamp: Double)

    fun getCurrentFrameTime(): Double

    fun reset()

}


