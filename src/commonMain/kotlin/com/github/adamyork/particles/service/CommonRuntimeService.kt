package com.github.adamyork.particles.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.adamyork.particles.common.ParticleEngineLifeCycleState
import com.github.adamyork.particles.engine.data.ParticleSubframeSchedule
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonRuntimeService(
    private val engineTiming: EngineTiming,
    override val physicsSettingsService: ParticlePhysicsSettingsService
) : RuntimeService {

    private companion object {
        const val FPS_SAMPLE_WINDOW_MS: Double = 1000.0
        const val MIN_DELTA_SECONDS: Float = 1f / 240f
        const val MAX_DELTA_SECONDS: Float = 1f / 15f
        const val HITCH_RATIO: Double = 2.0
        const val SMOOTHED_DELTA_BLEND: Double = 0.1
    }

    override var lastPaintTime: Double = 0.0

    override var particleEngineLifeCycleState: ParticleEngineLifeCycleState
        get() = mutableParticleEngineLifeCycleState
        set(value) {
            if (mutableParticleEngineLifeCycleState != value) {
                logger.info { "ParticleEngineLifeCycleState changed: $mutableParticleEngineLifeCycleState -> $value" }
                mutableParticleEngineLifeCycleState = value
            }
        }

    private val logger = KotlinLogging.logger {}

    private var mutableParticleEngineLifeCycleState by mutableStateOf(ParticleEngineLifeCycleState.INITIALIZING)

    private var fpsWindowStartTime: Double = 0.0
    private var fpsFrameCountInWindow: Int = 0
    private var lastObservedPaintTime: Double = 0.0
    private var cachedFps: Double = 0.0
    private var currentFrameTime: Double = 0.0
    private var smoothedDeltaTime: Double = 0.0
    private var previousScheduleWasSubdivided: Boolean = false

    override fun getSpriteTicksPerFrame(): Double =
        (engineTiming.tickTargetPerSec.toDouble() /
            engineTiming.spriteAnimationFramePerSec.toDouble()).coerceAtLeast(1.0)

    override fun deriveGravityFromViewportHeight(viewportHeight: Int) {
        physicsSettingsService.deriveGravityFromViewportHeight(
            viewportHeight,
            engineTiming.tickTargetPerSec
        )
        logger.info { "gravity derived from viewport height $viewportHeight: ${physicsSettingsService.gravity} px/tick^2" }
    }

    override fun getDeltaTimeCoefficient(): Double {
        val targetFps = engineTiming.tickTargetPerSec.toDouble()
        val targetDeltaTimeMs = 1000.0 / targetFps
        if (lastPaintTime <= 0.0 || currentFrameTime <= 0.0) {
            return 1.0
        }
        val actualDeltaTimeMs = currentFrameTime - lastPaintTime
        val coefficient = actualDeltaTimeMs / targetDeltaTimeMs
        return coefficient.coerceAtLeast(0.0)
    }

    override fun getSubframeSchedule(): ParticleSubframeSchedule {
        val deltaTime = getDeltaTimeCoefficient().coerceAtMost(physicsSettingsService.maxFrameDeltaTime)
        val isHitch = smoothedDeltaTime > 0.0 && deltaTime > smoothedDeltaTime * HITCH_RATIO
        val blendTarget = if (smoothedDeltaTime > 0.0) minOf(deltaTime, smoothedDeltaTime * HITCH_RATIO) else deltaTime
        smoothedDeltaTime = if (smoothedDeltaTime > 0.0) {
            smoothedDeltaTime + (blendTarget - smoothedDeltaTime) * SMOOTHED_DELTA_BLEND
        } else {
            blendTarget
        }
        val subframeSchedule = ParticleSubframeSchedule.of(
            deltaTime,
            physicsSettingsService.maxSubframeDeltaTime,
            if (isHitch && !previousScheduleWasSubdivided) physicsSettingsService.maxSubframeCount else 1
        )
        previousScheduleWasSubdivided = subframeSchedule.subframeCount > 1
        return subframeSchedule
    }

    override fun getDeltaTimeSeconds(): Float {
        val measuredDeltaSeconds = if (currentFrameTime > 0.0 && lastPaintTime > 0.0) {
            ((currentFrameTime - lastPaintTime) / 1000.0).toFloat()
        } else {
            0f
        }
        val fallbackDeltaSeconds = 1f / engineTiming.tickTargetPerSec.toFloat().coerceAtLeast(1f)
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
