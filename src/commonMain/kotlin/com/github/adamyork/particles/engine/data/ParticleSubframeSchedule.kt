package com.github.adamyork.particles.engine.data

import kotlin.math.ceil

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class ParticleSubframeSchedule(
    val subframeCount: Int,
    val subframeDeltaTime: Double
) {

    public companion object {
        private const val MINIMUM_SUBFRAME_DELTA_TIME: Double = 0.001

        public fun of(totalDeltaTime: Double, maxSubframeDeltaTime: Double, allowedSubframeCount: Int): ParticleSubframeSchedule {
            val safeTotalDeltaTime = totalDeltaTime.coerceAtLeast(0.0)
            val safeMaxSubframeDeltaTime = maxSubframeDeltaTime.coerceAtLeast(MINIMUM_SUBFRAME_DELTA_TIME)
            val wantedSubframeCount = ceil(safeTotalDeltaTime / safeMaxSubframeDeltaTime).toInt()
            val subframeCount = wantedSubframeCount.coerceIn(1, allowedSubframeCount.coerceAtLeast(1))
            return ParticleSubframeSchedule(subframeCount, safeTotalDeltaTime / subframeCount)
        }
    }
}
