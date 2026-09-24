package com.github.adamyork.kparticles.platform.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.LifeCycleState
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CommonRuntimeService(
    val assetService: AssetService
) : RuntimeService {

    private val logger = KotlinLogging.logger {}

    private companion object {
        const val FPS_SAMPLE_WINDOW_MS: Double = 1000.0
        const val MIN_DELTA_SECONDS: Float = 1f / 240f
        const val MAX_DELTA_SECONDS: Float = 1f / 15f
    }

    override var lastPaintTime: Double = 0.0

    private var lifeCycleStateState by mutableStateOf(LifeCycleState.INITIALIZING)
    override var lifeCycleState: LifeCycleState
        get() = lifeCycleStateState
        set(value) {
            if (lifeCycleStateState != value) {
                logger.info { "LifeCycleState changed: $lifeCycleStateState -> $value" }
                lifeCycleStateState = value
            }
        }

    private var fpsWindowStartTime: Double = 0.0
    private var fpsFrameCountInWindow: Int = 0
    private var lastObservedPaintTime: Double = 0.0
    private var cachedFps: Double = 0.0
    private var currentFrameTime: Double = 0.0

    override fun getDeltaTimeCoefficient(): Double {
        val targetFps = assetService.appProperties.engine.tickTargetPerSec.toDouble()
        val targetDeltaTimeMs = 1000.0 / targetFps
        if (lastPaintTime <= 0.0 || currentFrameTime <= 0.0) {
            return 1.0
        }
        val actualDeltaTimeMs = currentFrameTime - lastPaintTime
        val coefficient = actualDeltaTimeMs / targetDeltaTimeMs
        return coefficient.coerceIn(0.5, 2.0)
    }

    override fun getDeltaTimeSeconds(): Float {
        val measuredDeltaSeconds = if (currentFrameTime > 0.0 && lastPaintTime > 0.0) {
            ((currentFrameTime - lastPaintTime) / 1000.0).toFloat()
        } else {
            0f
        }
        val fallbackDeltaSeconds = 1f / assetService.appProperties.engine.tickTargetPerSec.toFloat().coerceAtLeast(1f)
        val resolvedDeltaSeconds = if (measuredDeltaSeconds > 0f) measuredDeltaSeconds else fallbackDeltaSeconds
        return resolvedDeltaSeconds.coerceIn(MIN_DELTA_SECONDS, MAX_DELTA_SECONDS)
    }

    override fun getFps(): Double {
        val currentPaintTime = lastPaintTime
        if (currentPaintTime <= 0.0) {
            return 0.0
        }

        if (currentPaintTime != lastObservedPaintTime) {
            lastObservedPaintTime = currentPaintTime

            if (fpsWindowStartTime == 0.0) {
                fpsWindowStartTime = currentPaintTime
                fpsFrameCountInWindow = 1
            } else {
                fpsFrameCountInWindow += 1
            }

            val elapsedWindowMs = currentPaintTime - fpsWindowStartTime
            if (elapsedWindowMs >= FPS_SAMPLE_WINDOW_MS) {
                cachedFps = (fpsFrameCountInWindow.toDouble() * 1000.0) / elapsedWindowMs
                fpsWindowStartTime = currentPaintTime
                fpsFrameCountInWindow = 0
            }
        }
        return cachedFps
    }

    override fun setCurrentFrameTime(timestamp: Double) {
        this.currentFrameTime = timestamp
    }

    override fun getCurrentFrameTime(): Double {
        return currentFrameTime
    }


}
