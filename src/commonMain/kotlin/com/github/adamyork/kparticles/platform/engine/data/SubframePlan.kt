package com.github.adamyork.kparticles.platform.engine.data

import kotlin.math.ceil

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class SubframePlan(
    val subframeCount: Int,
    val subframeDeltaTime: Double
) {

    companion object {
        private const val MINIMUM_SUBFRAME_DELTA_TIME: Double = 0.001

        fun of(totalDeltaTime: Double, maxSubframeDeltaTime: Double, maxSubframeCount: Int): SubframePlan {
            val safeTotalDeltaTime = totalDeltaTime.coerceAtLeast(0.0)
            val safeMaxSubframeDeltaTime = maxSubframeDeltaTime.coerceAtLeast(MINIMUM_SUBFRAME_DELTA_TIME)
            val wantedSubframeCount = ceil(safeTotalDeltaTime / safeMaxSubframeDeltaTime).toInt()
            val subframeCount = wantedSubframeCount.coerceIn(1, maxSubframeCount.coerceAtLeast(1))
            return SubframePlan(subframeCount, safeTotalDeltaTime / subframeCount)
        }
    }
}
