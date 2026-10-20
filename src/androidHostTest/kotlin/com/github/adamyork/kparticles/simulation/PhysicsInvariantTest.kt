package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class PhysicsInvariantTest {

    @Test
    fun noEffectProducesNonFiniteState() {
        for (effect in ParticleEffect.entries) {
            for (scenario in SimulationScenario.ALL) {
                val results = SimulationRunner.run(effect, scenario)
                for ((metricName, metricValue) in results) {
                    if (metricName.endsWith(".nonFinite")) {
                        assertEquals(0.0, metricValue, metricName)
                    }
                }
            }
        }
    }

    @Test
    fun sameSeedProducesIdenticalRuns() {
        for (effect in listOf(ParticleEffect.STRESS_TEST, ParticleEffect.COLLIDING_BITS, ParticleEffect.SHATTER)) {
            val firstRun = SimulationRunner.run(effect, SimulationScenario.HITCHES)
            val secondRun = SimulationRunner.run(effect, SimulationScenario.HITCHES)
            assertEquals(firstRun, secondRun, "$effect is not deterministic")
        }
    }

    @Test
    fun hostTimeIsNeverDropped() {
        val harness = SimulationHarness(SimulationRunner.SEED)
        harness.spawn(ParticleEffect.STRESS_TEST)
        var hostTicks = 0.0
        for (frameTicks in SimulationScenario.HITCHES.frameTicks + listOf(7.5, 1.0, 1.0, 45.0, 1.0, 59.0)) {
            harness.advance(frameTicks)
            hostTicks += frameTicks
            assertEquals(hostTicks, harness.simulatedTime(), 1e-9, "simulated time diverged from host time")
        }
    }

    @Test
    fun overcrowdingDoesNotCascadeIntoFragmentRunaway() {
        val harness = SimulationHarness(SimulationRunner.SEED)
        var countAfterSpawns = 0.0
        repeat(OVERCROWDING_FRAMES) { frameIndex ->
            if (frameIndex % OVERCROWDING_SPAWN_INTERVAL == 0 && frameIndex < OVERCROWDING_SPAWN_COUNT * OVERCROWDING_SPAWN_INTERVAL) {
                harness.spawn(ParticleEffect.STRESS_TEST)
            }
            harness.advance(1.0)
            val snapshot = harness.snapshot()
            assertEquals(0.0, snapshot.getValue("nonFinite"), "frame $frameIndex has non finite state")
            val count = snapshot.getValue("count")
            if (frameIndex == OVERCROWDING_SPAWN_COUNT * OVERCROWDING_SPAWN_INTERVAL) {
                countAfterSpawns = count
            }
            if (frameIndex > OVERCROWDING_SPAWN_COUNT * OVERCROWDING_SPAWN_INTERVAL) {
                assertTrue(
                    count <= countAfterSpawns * OVERCROWDING_COUNT_ALLOWANCE,
                    "frame $frameIndex count $count grew past $countAfterSpawns after spawning stopped"
                )
            }
        }
    }

    private companion object {
        const val OVERCROWDING_FRAMES: Int = 400
        const val OVERCROWDING_SPAWN_COUNT: Int = 8
        const val OVERCROWDING_SPAWN_INTERVAL: Int = 10
        const val OVERCROWDING_COUNT_ALLOWANCE: Double = 1.5
    }

}
