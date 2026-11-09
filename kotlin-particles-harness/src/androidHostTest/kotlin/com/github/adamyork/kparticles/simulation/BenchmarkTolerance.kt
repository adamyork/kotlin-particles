package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.effects.ParticleEffects

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class BenchmarkTolerance(val relative: Double, val absolute: Double) {

    companion object {
        private val EXACT = BenchmarkTolerance(relative = 1e-6, absolute = 1e-9)

        private val NOISE_SENSITIVE = BenchmarkTolerance(relative = 1e-3, absolute = 1e-6)

        private val EXACT_METRICS = setOf("substeps", "simulatedTime", "nonFinite")

        private val NOISE_SENSITIVE_EFFECTS = setOf(ParticleEffects.SHATTER.id)

        private val STATISTICAL_EFFECTS = setOf(ParticleEffects.STRESS_TEST.id)

        private val SMALL_SAMPLE_EFFECTS = setOf(ParticleEffects.COLLIDING_BITS.id, ParticleEffects.SPINNERS.id)

        private val STATISTICAL_TOLERANCES = mapOf(
            "count" to BenchmarkTolerance(relative = 0.12, absolute = 3.0),
            "peakCount" to BenchmarkTolerance(relative = 0.05, absolute = 3.0),
            "completed" to BenchmarkTolerance(relative = 0.12, absolute = 3.0),
            "outsideViewport" to BenchmarkTolerance(relative = 0.7, absolute = 3.0),
            "centerX" to BenchmarkTolerance(relative = 0.08, absolute = 1.0),
            "centerY" to BenchmarkTolerance(relative = 0.08, absolute = 1.0),
            "speedMean" to BenchmarkTolerance(relative = 0.3, absolute = 0.01),
            "kineticEnergy" to BenchmarkTolerance(relative = 1.5, absolute = 0.01)
        )

        private val SMALL_SAMPLE_TOLERANCES = mapOf(
            "count" to BenchmarkTolerance(relative = 0.25, absolute = 3.0),
            "completed" to BenchmarkTolerance(relative = 0.25, absolute = 3.0)
        )

        fun forKey(key: String): BenchmarkTolerance? {
            val keyParts = key.split('.')
            val effectName = keyParts.first()
            val metricName = keyParts.last()
            if (metricName in EXACT_METRICS) return EXACT
            if (effectName in STATISTICAL_EFFECTS) return STATISTICAL_TOLERANCES[metricName]
            if (effectName in SMALL_SAMPLE_EFFECTS) return SMALL_SAMPLE_TOLERANCES[metricName]
            if (effectName in NOISE_SENSITIVE_EFFECTS) return NOISE_SENSITIVE
            return EXACT
        }
    }

}
