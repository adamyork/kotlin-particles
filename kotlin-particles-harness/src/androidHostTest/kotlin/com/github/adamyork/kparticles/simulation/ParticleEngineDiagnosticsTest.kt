package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.effects.ParticleEffects
import com.github.adamyork.particles.engine.CommonParticleEngineDiagnostics
import com.github.adamyork.particles.engine.NoOpParticleEngineDiagnostics
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ParticleEngines
import com.github.adamyork.particles.engine.data.ParticleEngineType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class ParticleEngineDiagnosticsTest {

    @Test
    fun metricsAreOffByDefault() {
        val diagnostics = ParticleEngines.createDefault(FixedEngineTiming()).particleEngineDiagnostics
        assertFalse(diagnostics.isEnabled)
        assertEquals("Metrics are disabled", diagnostics.buildReport())
    }

    @Test
    fun metricsCanBeEnabledWhenTheEnginesAreCreated() {
        val diagnostics = ParticleEngines.createDefault(FixedEngineTiming(), metricsEnabled = true).particleEngineDiagnostics
        assertTrue(diagnostics.isEnabled)
        assertTrue(diagnostics.buildReport().contains("Engine Type"))
    }

    @Test
    fun disabledDiagnosticsIgnoreEveryUpdateCall() {
        val diagnostics = NoOpParticleEngineDiagnostics()
        diagnostics.beginEngineStep()
        diagnostics.endEngineStep()
        diagnostics.recordSleepingParticles(5)
        diagnostics.endFrame(16.0)
        diagnostics.endFrame(32.0)
        diagnostics.engineType = ParticleEngineType.GPU
        diagnostics.liveParticleSampler = { 1 }
        assertEquals(0L, diagnostics.timestampMicroseconds())
        assertEquals(ParticleEngineType.CPU, diagnostics.engineType)
        assertNull(diagnostics.liveParticleSampler)
        assertEquals(0, diagnostics.sleepingParticles)
        assertEquals(0.0, diagnostics.framerate.current)
        assertEquals(0L, diagnostics.hitchCount)
    }

    @Test
    fun enabledDiagnosticsRecordFramesAfterTheWarmup() {
        val diagnostics = CommonParticleEngineDiagnostics(ParticleEffectRegistry())
        repeat(60) { frameIndex -> diagnostics.endFrame(frameIndex * 16.0) }
        assertTrue(diagnostics.framerate.average > 60.0)
        assertEquals(0L, diagnostics.hitchCount)
        diagnostics.endFrame(60 * 16.0 + 100.0)
        assertEquals(1L, diagnostics.hitchCount)
    }

    @Test
    fun simulationResultsAreIdenticalWithAndWithoutMetrics() {
        val withMetrics = SimulationHarness(SimulationRunner.SEED, metricsEnabled = true)
        val withoutMetrics = SimulationHarness(SimulationRunner.SEED, metricsEnabled = false)
        withMetrics.spawn(ParticleEffects.STRESS_TEST)
        withoutMetrics.spawn(ParticleEffects.STRESS_TEST)
        repeat(120) {
            withMetrics.advance(1.0)
            withoutMetrics.advance(1.0)
        }
        assertEquals(withMetrics.snapshot(), withoutMetrics.snapshot())
    }
}
