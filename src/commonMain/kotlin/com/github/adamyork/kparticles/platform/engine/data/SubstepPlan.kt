package com.github.adamyork.kparticles.platform.engine.data

import kotlin.math.ceil

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class SubstepPlan(
    val substepCount: Int,
    val substepDeltaTime: Double
) {

    companion object {
        private const val MINIMUM_SUBSTEP_DELTA_TIME: Double = 0.001

        fun of(totalDeltaTime: Double, maxSubstepDeltaTime: Double, maxSubstepCount: Int): SubstepPlan {
            val safeTotalDeltaTime = totalDeltaTime.coerceAtLeast(0.0)
            val safeMaxSubstepDeltaTime = maxSubstepDeltaTime.coerceAtLeast(MINIMUM_SUBSTEP_DELTA_TIME)
            val wantedSubstepCount = ceil(safeTotalDeltaTime / safeMaxSubstepDeltaTime).toInt()
            val substepCount = wantedSubstepCount.coerceIn(1, maxSubstepCount.coerceAtLeast(1))
            return SubstepPlan(substepCount, safeTotalDeltaTime / substepCount)
        }
    }
}
