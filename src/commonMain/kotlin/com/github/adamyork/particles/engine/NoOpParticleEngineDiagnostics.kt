package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleEngineType
import com.github.adamyork.particles.engine.data.ParticleRunningStatistic

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class NoOpParticleEngineDiagnostics : ParticleEngineDiagnostics {

    private val emptyStatistic = ParticleRunningStatistic()

    override val isEnabled: Boolean = false

    override var engineType: ParticleEngineType
        get() = ParticleEngineType.CPU
        set(value) {}

    override var liveParticleSampler: (suspend () -> Int)?
        get() = null
        set(value) {}

    override val framerate: ParticleRunningStatistic = emptyStatistic

    override val deltaTime: ParticleRunningStatistic = emptyStatistic

    override val engineStep: ParticleRunningStatistic = emptyStatistic

    override val liveParticles: Int = 0

    override val maximumLiveParticles: Int = 0

    override val hitchCount: Long = 0L

    override val maximumHitchMilliseconds: Double = 0.0

    override val runtimeSeconds: Double = 0.0

    override val registeredEffectCount: Int = 0

    override val totalParticlesSpawned: Long = 0L

    override val activeGeneratorCount: Int = 0

    override val inactiveGeneratorCount: Int = 0

    override val sleepingParticles: Int = 0

    override fun reset() {
    }

    override fun timestampMicroseconds(): Long = 0L

    override fun recordEffectCostSince(effect: ParticleEffect, startMicroseconds: Long) {
    }

    override fun recordBatchDrawCostSince(generators: List<ParticleEffectGenerator>, startMicroseconds: Long) {
    }

    override fun beginEngineStep() {
    }

    override fun endEngineStep() {
    }

    override fun recordSleepingParticles(count: Int) {
    }

    override suspend fun sampleLiveParticles() {
    }

    override fun endFrame(timestampMilliseconds: Double) {
    }

    override fun buildReport(): String = "Metrics are disabled"
}
