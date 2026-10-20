package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
object SimulationRunner {

    const val SEED: Long = 20260101L

    fun run(effect: ParticleEffect, scenario: SimulationScenario, gravityScale: Double = 1.0): Map<String, Double> {
        val harness = SimulationHarness(SEED, gravityScale)
        val results = LinkedHashMap<String, Double>()
        for ((frameIndex, frameTicks) in scenario.frameTicks.withIndex()) {
            if (frameIndex in scenario.spawnFrames) {
                harness.spawn(effect)
            }
            harness.advance(frameTicks)
            val completedFrames = frameIndex + 1
            if (completedFrames in scenario.checkpointFrames) {
                for ((metricName, metricValue) in harness.snapshot()) {
                    results["${effect.name}.${scenario.name}.f$completedFrames.$metricName"] = metricValue
                }
            }
        }
        return results
    }

    fun runAll(gravityScale: Double = 1.0): Map<String, Double> {
        val results = LinkedHashMap<String, Double>()
        for (effect in ParticleEffect.entries) {
            for (scenario in SimulationScenario.ALL) {
                results.putAll(run(effect, scenario, gravityScale))
            }
            if (effect == ParticleEffect.STRESS_TEST) {
                results.putAll(run(effect, SimulationScenario.HEAVY, gravityScale))
            }
        }
        return results
    }

}
