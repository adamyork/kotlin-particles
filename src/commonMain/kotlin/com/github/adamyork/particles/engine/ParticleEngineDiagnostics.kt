package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleEngineType
import com.github.adamyork.particles.engine.data.ParticleRunningStatistic

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface ParticleEngineDiagnostics {

    public val isEnabled: Boolean

    public var engineType: ParticleEngineType

    public var liveParticleSampler: (suspend () -> Int)?

    public val framerate: ParticleRunningStatistic

    public val deltaTime: ParticleRunningStatistic

    public val engineStep: ParticleRunningStatistic

    public val liveParticles: Int

    public val maximumLiveParticles: Int

    public val hitchCount: Long

    public val maximumHitchMilliseconds: Double

    public val runtimeSeconds: Double

    public val registeredEffectCount: Int

    public val totalParticlesSpawned: Long

    public val activeGeneratorCount: Int

    public val inactiveGeneratorCount: Int

    public val sleepingParticles: Int

    public fun reset()

    public fun timestampMicroseconds(): Long

    public fun recordEffectCostSince(effect: ParticleEffect, startMicroseconds: Long)

    public fun recordBatchDrawCostSince(generators: List<ParticleEffectGenerator>, startMicroseconds: Long)

    public fun beginEngineStep()

    public fun endEngineStep()

    public fun recordSleepingParticles(count: Int)

    public suspend fun sampleLiveParticles()

    public fun endFrame(timestampMilliseconds: Double)

    public fun buildReport(): String
}
