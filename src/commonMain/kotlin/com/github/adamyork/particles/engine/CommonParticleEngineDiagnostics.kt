package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleEngineType
import com.github.adamyork.particles.engine.data.ParticleRunningStatistic
import kotlin.time.TimeSource

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonParticleEngineDiagnostics(
    private val particleEffectRegistry: ParticleEffectRegistry
) : ParticleEngineDiagnostics {

    private companion object {
        private const val WARMUP_FRAMES: Long = 30L
        private const val HITCH_MINIMUM_MILLISECONDS: Double = 33.0
        private const val HITCH_AVERAGE_MULTIPLIER: Double = 2.0
        private const val MILLISECONDS_PER_SECOND: Double = 1000.0
        private const val MICROSECONDS_PER_MILLISECOND: Double = 1000.0
    }

    private class EffectCost(val displayName: String) {
        var frameMilliseconds: Double = 0.0
        var totalMilliseconds: Double = 0.0
        var peakFrameMilliseconds: Double = 0.0
    }

    private val clockOrigin = TimeSource.Monotonic.markNow()

    private val effectCosts = LinkedHashMap<String, EffectCost>()

    private var costFrameCount = 0L

    private var frameCount = 0L

    private var lastTimestampMilliseconds = -1.0

    private var engineStepStartMicroseconds = -1L

    private var sleepingParticleCount = 0

    override val isEnabled: Boolean = true

    override var engineType: ParticleEngineType = ParticleEngineType.CPU

    override var liveParticleSampler: (suspend () -> Int)? = null

    override val framerate: ParticleRunningStatistic = ParticleRunningStatistic()

    override val deltaTime: ParticleRunningStatistic = ParticleRunningStatistic()

    override val engineStep: ParticleRunningStatistic = ParticleRunningStatistic()

    override var liveParticles: Int = 0
        private set

    override var maximumLiveParticles: Int = 0
        private set

    override var hitchCount: Long = 0L
        private set

    override var maximumHitchMilliseconds: Double = 0.0
        private set

    override val runtimeSeconds: Double
        get() = clockOrigin.elapsedNow().inWholeMicroseconds / (MICROSECONDS_PER_MILLISECOND * MILLISECONDS_PER_SECOND)

    override val registeredEffectCount: Int
        get() = particleEffectRegistry.count

    override val totalParticlesSpawned: Long
        get() = particleEffectRegistry.totalSpawnedParticles

    override val activeGeneratorCount: Int
        get() = particleEffectRegistry.generators().count { generator -> generator.particles.isNotEmpty() }

    override val inactiveGeneratorCount: Int
        get() = particleEffectRegistry.generators().size - activeGeneratorCount

    override val sleepingParticles: Int
        get() = sleepingParticleCount

    override fun reset() {
        frameCount = 0L
        lastTimestampMilliseconds = -1.0
        engineStepStartMicroseconds = -1L
        framerate.reset()
        deltaTime.reset()
        engineStep.reset()
        maximumLiveParticles = liveParticles
        hitchCount = 0L
        maximumHitchMilliseconds = 0.0
        effectCosts.clear()
        costFrameCount = 0L
    }

    override fun timestampMicroseconds(): Long = nowMicroseconds()

    private fun recordEffectCost(effect: ParticleEffect, milliseconds: Double) {
        effectCosts.getOrPut(effect.id) { EffectCost(effect.displayName) }.frameMilliseconds += milliseconds
    }

    override fun recordEffectCostSince(effect: ParticleEffect, startMicroseconds: Long) {
        recordEffectCost(effect, (nowMicroseconds() - startMicroseconds) / MICROSECONDS_PER_MILLISECOND)
    }

    override fun recordBatchDrawCostSince(generators: List<ParticleEffectGenerator>, startMicroseconds: Long) {
        val drawMilliseconds = (nowMicroseconds() - startMicroseconds) / MICROSECONDS_PER_MILLISECOND
        var batchParticleCount = 0
        for (generator in generators) {
            batchParticleCount += generator.particles.size
        }
        if (batchParticleCount == 0) return
        for (generator in generators) {
            if (generator.particles.isEmpty()) continue
            recordEffectCost(generator.effect, drawMilliseconds * generator.particles.size / batchParticleCount)
        }
    }

    private fun foldEffectCosts() {
        val shouldRecord = frameCount >= WARMUP_FRAMES
        for (effectCost in effectCosts.values) {
            if (shouldRecord) {
                effectCost.totalMilliseconds += effectCost.frameMilliseconds
                if (effectCost.frameMilliseconds > effectCost.peakFrameMilliseconds) {
                    effectCost.peakFrameMilliseconds = effectCost.frameMilliseconds
                }
            }
            effectCost.frameMilliseconds = 0.0
        }
        if (shouldRecord) costFrameCount++
    }

    private fun buildCostliestEffectLine(): String {
        var attributedTotal = 0.0
        var costliest: EffectCost? = null
        for (effectCost in effectCosts.values) {
            attributedTotal += effectCost.totalMilliseconds
            if (costliest == null || effectCost.totalMilliseconds > costliest.totalMilliseconds) costliest = effectCost
        }
        if (costliest == null || attributedTotal <= 0.0 || costFrameCount == 0L) return "Costliest Effect: n/a"
        val sharePercent = costliest.totalMilliseconds / attributedTotal * 100.0
        val averageMilliseconds = costliest.totalMilliseconds / costFrameCount
        return "Costliest Effect: ${costliest.displayName} ${format(averageMilliseconds)} ms avg " +
            "${format(sharePercent)}% peak ${format(costliest.peakFrameMilliseconds)} ms"
    }

    override fun beginEngineStep() {
        engineStepStartMicroseconds = nowMicroseconds()
    }

    override fun endEngineStep() {
        if (engineStepStartMicroseconds < 0L) return
        val stepMilliseconds = (nowMicroseconds() - engineStepStartMicroseconds) / MICROSECONDS_PER_MILLISECOND
        engineStepStartMicroseconds = -1L
        if (frameCount < WARMUP_FRAMES) engineStep.updateCurrent(stepMilliseconds) else engineStep.record(stepMilliseconds)
    }

    override fun recordSleepingParticles(count: Int) {
        sleepingParticleCount = count
    }

    override suspend fun sampleLiveParticles() {
        val sampler = liveParticleSampler ?: return
        val sampledCount = sampler()
        liveParticles = sampledCount
        if (sampledCount > maximumLiveParticles) maximumLiveParticles = sampledCount
    }

    override fun endFrame(timestampMilliseconds: Double) {
        foldEffectCosts()
        if (liveParticleSampler == null) {
            var liveCount = 0
            for (generator in particleEffectRegistry.generators()) liveCount += generator.particles.size
            liveParticles = liveCount
            if (liveCount > maximumLiveParticles) maximumLiveParticles = liveCount
        }
        val previousTimestamp = lastTimestampMilliseconds
        lastTimestampMilliseconds = timestampMilliseconds
        if (previousTimestamp < 0.0) return
        val deltaMilliseconds = timestampMilliseconds - previousTimestamp
        if (deltaMilliseconds <= 0.0) return
        val framesPerSecond = MILLISECONDS_PER_SECOND / deltaMilliseconds
        if (frameCount < WARMUP_FRAMES) {
            deltaTime.updateCurrent(deltaMilliseconds)
            framerate.updateCurrent(framesPerSecond)
        } else {
            val hitchThreshold = maxOf(HITCH_MINIMUM_MILLISECONDS, deltaTime.average * HITCH_AVERAGE_MULTIPLIER)
            if (deltaMilliseconds > hitchThreshold) {
                hitchCount++
                if (deltaMilliseconds > maximumHitchMilliseconds) maximumHitchMilliseconds = deltaMilliseconds
            }
            deltaTime.record(deltaMilliseconds)
            framerate.record(framesPerSecond)
        }
        frameCount++
    }

    override fun buildReport(): String {
        val report = StringBuilder()
        report.appendLine("Engine Type: ${engineType.name}")
        report.appendLine("Engine Runtime Seconds: ${format(runtimeSeconds)}")
        report.appendLine()
        report.appendLine("Registered Effect Count: $registeredEffectCount")
        report.appendLine("Active Generators: $activeGeneratorCount")
        report.appendLine("Inactive Generators: $inactiveGeneratorCount")
        report.appendLine()
        report.appendLine("Current Framerate: ${format(framerate.current)}")
        report.appendLine("Min Framerate: ${format(framerate.minimum)}")
        report.appendLine("Max Framerate: ${format(framerate.maximum)}")
        report.appendLine("Average Framerate: ${format(framerate.average)}")
        report.appendLine()
        report.appendLine("Total Particles Spawned: $totalParticlesSpawned")
        report.appendLine("Live Particles: $liveParticles")
        report.appendLine("Max Live Particles: $maximumLiveParticles")
        report.appendLine("Sleeping Particles: $sleepingParticleCount")
        report.appendLine()
        report.appendLine("Current Delta Time ms: ${format(deltaTime.current)}")
        report.appendLine("Min Delta Time ms: ${format(deltaTime.minimum)}")
        report.appendLine("Max Delta Time ms: ${format(deltaTime.maximum)}")
        report.appendLine("Average Delta Time ms: ${format(deltaTime.average)}")
        report.appendLine()
        report.appendLine("Engine Step ms: ${format(engineStep.current)}")
        report.appendLine("Min Engine Step ms: ${format(engineStep.minimum)}")
        report.appendLine("Max Engine Step ms: ${format(engineStep.maximum)}")
        report.appendLine("Average Engine Step ms: ${format(engineStep.average)}")
        report.appendLine()
        report.appendLine("Hitch Count: $hitchCount")
        report.appendLine("Max Hitch ms: ${format(maximumHitchMilliseconds)}")
        report.appendLine()
        report.appendLine(buildCostliestEffectLine())
        return report.toString()
    }

    private fun nowMicroseconds(): Long = clockOrigin.elapsedNow().inWholeMicroseconds

    private fun format(value: Double): String = ((value * 100.0).toLong() / 100.0).toString()
}
