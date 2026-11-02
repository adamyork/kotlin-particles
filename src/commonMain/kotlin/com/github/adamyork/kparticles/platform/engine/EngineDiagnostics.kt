package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.DiagnosticCounter
import com.github.adamyork.kparticles.platform.engine.data.DiagnosticPhase
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect
import me.tatarka.inject.annotations.Inject
import kotlin.time.TimeSource

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class EngineDiagnostics {

    private companion object {
        private const val HISTORY_FRAMES: Int = 600
        private const val TIMING_SAMPLE_INTERVAL_FRAMES: Long = 1L
        private const val NOT_SAMPLED_MICROSECONDS: Long = -1L
        private const val NOT_SAMPLED_MILLISECONDS: Float = -1f
        private const val MICROSECONDS_PER_MILLISECOND: Float = 1000f
        private const val INITIAL_WARMUP_FRAMES: Long = 120L
        private const val HISTOGRAM_BIN_MILLISECONDS: Float = 0.25f
        private const val HISTOGRAM_BINS: Int = 400
        private const val SLOW_FRAME_MILLISECONDS: Float = 20f
        private const val ENGINE_SHARE_OF_SLOW_FRAME: Float = 0.5f
        private const val PHASE_AVERAGE_BUDGET_MILLISECONDS: Float = 6f
        private const val PHASE_MAXIMUM_BUDGET_MILLISECONDS: Float = 8f
    }

    private val phaseCount = DiagnosticPhase.entries.size
    private val counterCount = DiagnosticCounter.entries.size
    private val effectCount = ParticleEffect.entries.size
    private val clockOrigin = TimeSource.Monotonic.markNow()
    private val currentPhaseMicroseconds = LongArray(phaseCount)
    private val currentCounters = IntArray(counterCount)
    private val currentEffectParticles = IntArray(effectCount)
    private val currentEffectDrawMicroseconds = LongArray(effectCount)
    private val phaseHistory = FloatArray(HISTORY_FRAMES * phaseCount)
    private val counterHistory = IntArray(HISTORY_FRAMES * counterCount)
    private val effectParticleHistory = IntArray(HISTORY_FRAMES * effectCount)
    private val effectDrawHistory = FloatArray(HISTORY_FRAMES * effectCount)
    private val frameIntervalHistory = FloatArray(HISTORY_FRAMES)
    private var completedFrames = 0L
    private var isSampledFrame = true
    private var lastTimestampMilliseconds = -1.0
    private val lifetimeWorstCounters = IntArray(counterCount)
    private val lifetimeWorstEffectParticles = IntArray(effectCount)
    private var lifetimeWorstIntervalMilliseconds = 0f
    private var lifetimeWorstFrameNumber = 0L
    private val lifetimeWorstPhases = FloatArray(phaseCount)
    private var warmupFrames = INITIAL_WARMUP_FRAMES
    private var runIntervalFrames = 0L
    private var runFrames = 0L
    private var runIntervalSum = 0.0
    private var runIntervalMaximum = 0f
    private val runIntervalHistogram = IntArray(HISTOGRAM_BINS + 1)
    private var runSlowFrames = 0L
    private var runSlowEngineFrames = 0L
    private var runSlowOutsideFrames = 0L
    private var runEngineWorkSum = 0.0
    private var runEngineWorkMaximum = 0f
    private var runEngineWorkFrames = 0L
    private val runPhaseSums = DoubleArray(phaseCount)
    private val runPhaseCounts = LongArray(phaseCount)
    private val runPhaseMaximums = FloatArray(phaseCount)
    private val runCounterSums = LongArray(counterCount)
    private val runCounterMaximums = IntArray(counterCount)
    private val runEffectParticleSums = LongArray(effectCount)
    private val runEffectParticleMaximums = IntArray(effectCount)
    private val runEffectDrawSums = DoubleArray(effectCount)
    private val runEffectDrawCounts = LongArray(effectCount)
    private var runAliveSum = 0L
    private var lifetimeWorstEngineWorkMilliseconds = 0f
    private val engineWorkHistory = FloatArray(HISTORY_FRAMES)
    private var engineWorkStartMicroseconds = NOT_SAMPLED_MICROSECONDS
    private val expiredCounts = LongArray(effectCount)
    private val expiredLifespanSums = DoubleArray(effectCount)
    private val longestLifespans = DoubleArray(effectCount)
    private val lifetimeCounterTotals = LongArray(counterCount)
    private val averageSpeedHistory = FloatArray(HISTORY_FRAMES)
    private val maximumSpeedHistory = FloatArray(HISTORY_FRAMES)
    private var currentSpeedSum = 0.0
    private var currentSpeedMaximum = 0.0
    private var currentMotionCount = 0
    private var currentOldestAge = 0.0
    private var currentOldestEffectOrdinal = -1
    private var lastOldestAge = 0.0
    private var lastOldestEffectOrdinal = -1
    private var lifetimeFastestSpeed = 0.0
    private var lifetimePeakAlive = 0

    var engineLabel: String = "unknown"

    fun reset() {
        completedFrames = 0L
        isSampledFrame = true
        warmupFrames = 0L
        engineWorkStartMicroseconds = NOT_SAMPLED_MICROSECONDS
        lifetimeWorstIntervalMilliseconds = 0f
        lifetimeWorstFrameNumber = 0L
        lifetimeWorstEngineWorkMilliseconds = 0f
        lifetimeWorstPhases.fill(0f)
        lifetimeWorstCounters.fill(0)
        lifetimeWorstEffectParticles.fill(0)
        expiredCounts.fill(0L)
        expiredLifespanSums.fill(0.0)
        longestLifespans.fill(0.0)
        lifetimeCounterTotals.fill(0L)
        currentPhaseMicroseconds.fill(0L)
        currentCounters.fill(0)
        currentEffectDrawMicroseconds.fill(0L)
        currentSpeedSum = 0.0
        currentSpeedMaximum = 0.0
        currentMotionCount = 0
        currentOldestAge = 0.0
        currentOldestEffectOrdinal = -1
        lastOldestAge = 0.0
        lastOldestEffectOrdinal = -1
        lifetimeFastestSpeed = 0.0
        lifetimePeakAlive = 0
        runIntervalFrames = 0L
        runFrames = 0L
        runIntervalSum = 0.0
        runIntervalMaximum = 0f
        runIntervalHistogram.fill(0)
        runSlowFrames = 0L
        runSlowEngineFrames = 0L
        runSlowOutsideFrames = 0L
        runEngineWorkSum = 0.0
        runEngineWorkMaximum = 0f
        runEngineWorkFrames = 0L
        runPhaseSums.fill(0.0)
        runPhaseCounts.fill(0L)
        runPhaseMaximums.fill(0f)
        runCounterSums.fill(0L)
        runCounterMaximums.fill(0)
        runEffectParticleSums.fill(0L)
        runEffectParticleMaximums.fill(0)
        runEffectDrawSums.fill(0.0)
        runEffectDrawCounts.fill(0L)
        runAliveSum = 0L
    }

    fun beginEngineWork() {
        engineWorkStartMicroseconds = nowMicroseconds()
    }

    fun isSamplingFrame(): Boolean = isSampledFrame

    fun recordMotion(
        speedSum: Double,
        speedMaximum: Double,
        particleCount: Int,
        oldestAge: Double,
        oldestEffectOrdinal: Int
    ) {
        currentSpeedSum += speedSum
        currentSpeedMaximum = maxOf(currentSpeedMaximum, speedMaximum)
        currentMotionCount += particleCount
        if (oldestAge > currentOldestAge) {
            currentOldestAge = oldestAge
            currentOldestEffectOrdinal = oldestEffectOrdinal
        }
    }

    fun recordExpired(effectOrdinal: Int, lifespan: Double) {
        expiredCounts[effectOrdinal]++
        expiredLifespanSums[effectOrdinal] += lifespan
        if (lifespan > longestLifespans[effectOrdinal]) longestLifespans[effectOrdinal] = lifespan
    }

    fun startTimer(): Long = if (isSampledFrame) nowMicroseconds() else NOT_SAMPLED_MICROSECONDS

    fun stopTimer(phase: DiagnosticPhase, startMicroseconds: Long) {
        if (startMicroseconds == NOT_SAMPLED_MICROSECONDS) return
        currentPhaseMicroseconds[phase.ordinal] += nowMicroseconds() - startMicroseconds
    }

    fun stopEffectTimer(effect: ParticleEffect, startMicroseconds: Long) {
        if (startMicroseconds == NOT_SAMPLED_MICROSECONDS) return
        currentEffectDrawMicroseconds[effect.ordinal] += nowMicroseconds() - startMicroseconds
    }

    fun count(counter: DiagnosticCounter, amount: Int) {
        currentCounters[counter.ordinal] += amount
    }

    fun countEffectParticles(effect: ParticleEffect, amount: Int) {
        currentEffectParticles[effect.ordinal] = amount
    }

    fun endFrame(timestampMilliseconds: Double) {
        val slot = (completedFrames % HISTORY_FRAMES).toInt()
        frameIntervalHistory[slot] =
            if (lastTimestampMilliseconds < 0.0) 0f else (timestampMilliseconds - lastTimestampMilliseconds).toFloat()
        lastTimestampMilliseconds = timestampMilliseconds
        engineWorkHistory[slot] =
            if (engineWorkStartMicroseconds == NOT_SAMPLED_MICROSECONDS) NOT_SAMPLED_MILLISECONDS
            else (nowMicroseconds() - engineWorkStartMicroseconds) / MICROSECONDS_PER_MILLISECOND
        engineWorkStartMicroseconds = NOT_SAMPLED_MICROSECONDS
        if (completedFrames >= warmupFrames && frameIntervalHistory[slot] > lifetimeWorstIntervalMilliseconds) {
            lifetimeWorstIntervalMilliseconds = frameIntervalHistory[slot]
            for (phaseIndex in 0 until phaseCount) {
                lifetimeWorstPhases[phaseIndex] =
                    if (isSampledFrame) currentPhaseMicroseconds[phaseIndex] / MICROSECONDS_PER_MILLISECOND else NOT_SAMPLED_MILLISECONDS
            }
            lifetimeWorstFrameNumber = completedFrames
            lifetimeWorstEngineWorkMilliseconds = engineWorkHistory[slot]
            currentCounters.copyInto(lifetimeWorstCounters)
            currentEffectParticles.copyInto(lifetimeWorstEffectParticles)
        }
        var aliveParticles = 0
        for (effectIndex in 0 until effectCount) aliveParticles += currentEffectParticles[effectIndex]
        lifetimePeakAlive = maxOf(lifetimePeakAlive, aliveParticles)
        for (counterIndex in 0 until counterCount) lifetimeCounterTotals[counterIndex] += currentCounters[counterIndex].toLong()
        if (currentMotionCount > 0) {
            averageSpeedHistory[slot] = (currentSpeedSum / currentMotionCount).toFloat()
            maximumSpeedHistory[slot] = currentSpeedMaximum.toFloat()
            lifetimeFastestSpeed = maxOf(lifetimeFastestSpeed, currentSpeedMaximum)
            lastOldestAge = currentOldestAge
            lastOldestEffectOrdinal = currentOldestEffectOrdinal
        } else {
            averageSpeedHistory[slot] = NOT_SAMPLED_MILLISECONDS
            maximumSpeedHistory[slot] = NOT_SAMPLED_MILLISECONDS
        }
        currentSpeedSum = 0.0
        currentSpeedMaximum = 0.0
        currentMotionCount = 0
        currentOldestAge = 0.0
        currentOldestEffectOrdinal = -1
        for (phaseIndex in 0 until phaseCount) {
            phaseHistory[slot * phaseCount + phaseIndex] =
                if (isSampledFrame) currentPhaseMicroseconds[phaseIndex] / MICROSECONDS_PER_MILLISECOND else NOT_SAMPLED_MILLISECONDS
            currentPhaseMicroseconds[phaseIndex] = 0L
        }
        for (counterIndex in 0 until counterCount) {
            counterHistory[slot * counterCount + counterIndex] = currentCounters[counterIndex]
            currentCounters[counterIndex] = 0
        }
        for (effectIndex in 0 until effectCount) {
            effectParticleHistory[slot * effectCount + effectIndex] = currentEffectParticles[effectIndex]
            effectDrawHistory[slot * effectCount + effectIndex] =
                if (isSampledFrame) currentEffectDrawMicroseconds[effectIndex] / MICROSECONDS_PER_MILLISECOND else NOT_SAMPLED_MILLISECONDS
            currentEffectDrawMicroseconds[effectIndex] = 0L
        }
        if (completedFrames >= warmupFrames) accumulateRun(slot)
        completedFrames++
        isSampledFrame = completedFrames % TIMING_SAMPLE_INTERVAL_FRAMES == 0L
    }

    fun buildReport(): String {
        val frameCount = minOf(completedFrames, HISTORY_FRAMES.toLong()).toInt()
        val report = StringBuilder()
        report.appendLine("engine: $engineLabel")
        val samplingText =
            if (TIMING_SAMPLE_INTERVAL_FRAMES == 1L) "every frame" else "1 in $TIMING_SAMPLE_INTERVAL_FRAMES frames"
        report.appendLine("frames since start or reset: $completedFrames, recent window: $frameCount (timings sampled $samplingText)")
        if (frameCount < 2) {
            report.appendLine("not enough frames yet, let it run and open this again")
            return report.toString()
        }
        appendRunFrameRate(report)
        appendFrameIntervals(report, frameCount)
        appendSlowFrames(report, frameCount)
        appendPopulation(report, frameCount)
        appendPhases(report, frameCount)
        appendCounters(report, frameCount)
        appendEffects(report)
        return report.toString()
    }

    private fun accumulateRun(slot: Int) {
        runFrames++
        val interval = frameIntervalHistory[slot]
        val engineWork = engineWorkHistory[slot]
        if (interval > 0f) {
            runIntervalFrames++
            runIntervalSum += interval
            if (interval > runIntervalMaximum) runIntervalMaximum = interval
            runIntervalHistogram[minOf((interval / HISTOGRAM_BIN_MILLISECONDS).toInt(), HISTOGRAM_BINS)]++
            if (interval > SLOW_FRAME_MILLISECONDS) {
                runSlowFrames++
                if (engineWork >= 0f && engineWork >= interval * ENGINE_SHARE_OF_SLOW_FRAME) runSlowEngineFrames++ else runSlowOutsideFrames++
            }
            if (engineWork >= 0f) {
                runEngineWorkSum += engineWork
                runEngineWorkFrames++
                if (engineWork > runEngineWorkMaximum) runEngineWorkMaximum = engineWork
            }
        }
        for (phaseIndex in 0 until phaseCount) {
            val value = phaseHistory[slot * phaseCount + phaseIndex]
            if (value < 0f) continue
            runPhaseSums[phaseIndex] += value.toDouble()
            runPhaseCounts[phaseIndex]++
            if (value > runPhaseMaximums[phaseIndex]) runPhaseMaximums[phaseIndex] = value
        }
        for (counterIndex in 0 until counterCount) {
            val value = counterHistory[slot * counterCount + counterIndex]
            runCounterSums[counterIndex] += value.toLong()
            if (value > runCounterMaximums[counterIndex]) runCounterMaximums[counterIndex] = value
        }
        var aliveInFrame = 0
        for (effectIndex in 0 until effectCount) {
            val particles = effectParticleHistory[slot * effectCount + effectIndex]
            aliveInFrame += particles
            runEffectParticleSums[effectIndex] += particles.toLong()
            if (particles > runEffectParticleMaximums[effectIndex]) runEffectParticleMaximums[effectIndex] = particles
            val drawMilliseconds = effectDrawHistory[slot * effectCount + effectIndex]
            if (drawMilliseconds < 0f) continue
            runEffectDrawSums[effectIndex] += drawMilliseconds.toDouble()
            runEffectDrawCounts[effectIndex]++
        }
        runAliveSum += aliveInFrame.toLong()
    }

    private fun runIntervalPercentile(fraction: Float): Float {
        val target = (runIntervalFrames * fraction).toLong().coerceAtLeast(1L)
        var cumulative = 0L
        for (binIndex in 0 until HISTOGRAM_BINS) {
            cumulative += runIntervalHistogram[binIndex]
            if (cumulative >= target) return (binIndex + 1) * HISTOGRAM_BIN_MILLISECONDS
        }
        return runIntervalMaximum
    }

    private fun nowMicroseconds(): Long = clockOrigin.elapsedNow().inWholeMicroseconds

    private fun appendFrameIntervals(report: StringBuilder, frameCount: Int) {
        val intervals = ArrayList<Float>(frameCount)
        for (frameIndex in 0 until frameCount) {
            if (frameIntervalHistory[frameIndex] > 0f) intervals.add(frameIntervalHistory[frameIndex])
        }
        if (intervals.isEmpty()) return
        intervals.sort()
        val averageInterval = intervals.sum() / intervals.size
        val ninetyFifthInterval = intervals[((intervals.size - 1) * 0.95f).toInt()]
        val ninetyNinthInterval = intervals[((intervals.size - 1) * 0.99f).toInt()]
        val worstInterval = intervals.last()
        report.appendLine()
        report.appendLine("frame rate, last $frameCount frames")
        appendFrameRateLine(report, "average", averageInterval)
        appendFrameRateLine(report, "95th percentile", ninetyFifthInterval)
        appendFrameRateLine(report, "99th percentile", ninetyNinthInterval)
        appendFrameRateLine(report, "worst", worstInterval)
    }

    private fun appendRunFrameRate(report: StringBuilder) {
        if (runIntervalFrames == 0L) return
        val averageInterval = (runIntervalSum / runIntervalFrames).toFloat()
        val ninetyFifthInterval = runIntervalPercentile(0.95f)
        val ninetyNinthInterval = runIntervalPercentile(0.99f)
        report.appendLine()
        report.appendLine("frame rate, whole run ($runIntervalFrames frames, percentiles to 0.25 ms)")
        appendFrameRateLine(report, "average", averageInterval)
        appendFrameRateLine(report, "95th percentile", ninetyFifthInterval)
        appendFrameRateLine(report, "99th percentile", ninetyNinthInterval)
        appendFrameRateLine(report, "worst", runIntervalMaximum)
        if (runEngineWorkFrames > 0L) {
            report.appendLine(
                "  engine work per frame: average ${format((runEngineWorkSum / runEngineWorkFrames).toFloat())} ms, max ${
                    format(
                        runEngineWorkMaximum
                    )
                } ms"
            )
        }
    }

    private fun appendFrameRateLine(report: StringBuilder, label: String, interval: Float) {
        report.appendLine("  $label ${format(1000f / interval)} fps (${format(interval)} ms)${slowFrameMark(interval)}")
    }

    private fun appendSlowFrames(report: StringBuilder, frameCount: Int) {
        var slowFrameCount = 0
        var slowEngineFrameCount = 0
        var slowOutsideFrameCount = 0
        var worstSlot = -1
        var worstInterval = 0f
        for (frameIndex in 0 until frameCount) {
            val interval = frameIntervalHistory[frameIndex]
            if (interval > SLOW_FRAME_MILLISECONDS) {
                slowFrameCount++
                val engineWork = engineWorkHistory[frameIndex]
                if (engineWork >= 0f && engineWork >= interval * ENGINE_SHARE_OF_SLOW_FRAME) slowEngineFrameCount++ else slowOutsideFrameCount++
            }
            if (interval > worstInterval) {
                worstInterval = interval
                worstSlot = frameIndex
            }
        }
        report.appendLine()
        report.appendLine("slow frames (over ${format(SLOW_FRAME_MILLISECONDS)} ms)")
        report.appendLine("  whole run: $runSlowFrames of $runIntervalFrames")
        report.appendLine("    engine work was most of the frame: $runSlowEngineFrames")
        report.appendLine("    time was spent outside the engine: $runSlowOutsideFrames")
        report.appendLine("  last $frameCount frames: $slowFrameCount")
        report.appendLine("    engine work was most of the frame: $slowEngineFrameCount")
        report.appendLine("    time was spent outside the engine: $slowOutsideFrameCount")
        if (worstSlot >= 0) {
            appendFrameDetail(
                report,
                "worst frame in last $frameCount",
                worstInterval,
                engineWorkHistory[worstSlot],
                phaseHistory,
                worstSlot * phaseCount,
                counterHistory,
                worstSlot * counterCount,
                effectParticleHistory,
                worstSlot * effectCount
            )
        }
        if (lifetimeWorstFrameNumber > 0L) {
            appendFrameDetail(
                report,
                "worst frame of whole run (frame $lifetimeWorstFrameNumber)",
                lifetimeWorstIntervalMilliseconds,
                lifetimeWorstEngineWorkMilliseconds,
                lifetimeWorstPhases,
                0,
                lifetimeWorstCounters,
                0,
                lifetimeWorstEffectParticles,
                0
            )
        }
    }

    private fun appendPopulation(report: StringBuilder, frameCount: Int) {
        var aliveTotal = 0L
        var alivePeak = 0
        for (frameIndex in 0 until frameCount) {
            var aliveInFrame = 0
            for (effectIndex in 0 until effectCount) aliveInFrame += effectParticleHistory[frameIndex * effectCount + effectIndex]
            aliveTotal += aliveInFrame
            alivePeak = maxOf(alivePeak, aliveInFrame)
        }
        val latestSlot = ((completedFrames - 1L) % HISTORY_FRAMES).toInt()
        var aliveNow = 0
        for (effectIndex in 0 until effectCount) aliveNow += effectParticleHistory[latestSlot * effectCount + effectIndex]
        var expiredTotal = 0L
        var lifespanTotal = 0.0
        var longestLifespan = 0.0
        var longestEffectOrdinal = -1
        for (effectIndex in 0 until effectCount) {
            expiredTotal += expiredCounts[effectIndex]
            lifespanTotal += expiredLifespanSums[effectIndex]
            if (longestLifespans[effectIndex] > longestLifespan) {
                longestLifespan = longestLifespans[effectIndex]
                longestEffectOrdinal = effectIndex
            }
        }
        var speedAverageTotal = 0f
        var speedMaximum = 0f
        var speedSampleCount = 0
        for (frameIndex in 0 until frameCount) {
            if (averageSpeedHistory[frameIndex] < 0f) continue
            speedAverageTotal += averageSpeedHistory[frameIndex]
            speedMaximum = maxOf(speedMaximum, maximumSpeedHistory[frameIndex])
            speedSampleCount++
        }
        report.appendLine()
        report.appendLine("population (ages in ticks, speeds in px per tick)")
        report.appendLine("  alive now: $aliveNow")
        if (runFrames > 0L) report.appendLine("  alive over whole run: average ${format(runAliveSum.toFloat() / runFrames)}")
        report.appendLine("  alive in window: average ${format(aliveTotal.toFloat() / frameCount)}, peak $alivePeak")
        report.appendLine("  alive peak since start: $lifetimePeakAlive")
        report.appendLine("  expired since start: $expiredTotal")
        if (expiredTotal > 0L) report.appendLine("  average lifespan: ${format((lifespanTotal / expiredTotal).toFloat())}")
        if (longestEffectOrdinal >= 0) {
            report.appendLine(
                "  longest lived: ${ParticleEffect.entries[longestEffectOrdinal].displayName} ${
                    format(
                        longestLifespan.toFloat()
                    )
                }"
            )
        }
        if (lastOldestEffectOrdinal >= 0) {
            report.appendLine(
                "  oldest alive: ${ParticleEffect.entries[lastOldestEffectOrdinal].displayName} ${
                    format(
                        lastOldestAge.toFloat()
                    )
                }"
            )
        }
        if (speedSampleCount > 0) {
            report.appendLine(
                "  speed in window: average ${format(speedAverageTotal / speedSampleCount)}, max ${
                    format(
                        speedMaximum
                    )
                }"
            )
            report.appendLine("  fastest since start: ${format(lifetimeFastestSpeed.toFloat())}")
        }
        report.appendLine("  contact pairs since start: ${lifetimeCounterTotals[DiagnosticCounter.CONTACT_PAIRS.ordinal]}")
        report.appendLine("  by effect (expired, average lifespan, longest)")
        for (effectIndex in 0 until effectCount) {
            if (expiredCounts[effectIndex] == 0L) continue
            val averageLifespan = (expiredLifespanSums[effectIndex] / expiredCounts[effectIndex]).toFloat()
            report.appendLine(
                "    ${ParticleEffect.entries[effectIndex].displayName}: ${expiredCounts[effectIndex]}, " +
                        "${format(averageLifespan)}, ${format(longestLifespans[effectIndex].toFloat())}"
            )
        }
    }

    private fun appendFrameDetail(
        report: StringBuilder,
        heading: String,
        interval: Float,
        engineWork: Float,
        phases: FloatArray,
        phaseOffset: Int,
        counters: IntArray,
        counterOffset: Int,
        effectParticles: IntArray,
        effectOffset: Int
    ) {
        report.appendLine("  $heading: ${format(interval)} ms (${format(1000f / interval)} fps)")
        if (engineWork >= 0f) {
            report.appendLine(
                "    engine work: ${format(engineWork)} ms, outside the engine: ${
                    format(
                        maxOf(
                            interval - engineWork,
                            0f
                        )
                    )
                } ms"
            )
        }
        for (phase in DiagnosticPhase.entries) {
            val value = phases[phaseOffset + phase.ordinal]
            if (value <= 0f) continue
            report.appendLine("    ${phase.label}: ${format(value)} ms")
        }
        for (counter in DiagnosticCounter.entries) {
            val value = counters[counterOffset + counter.ordinal]
            if (value == 0) continue
            report.appendLine("    ${counter.label}: $value")
        }
        for (effect in ParticleEffect.entries) {
            val value = effectParticles[effectOffset + effect.ordinal]
            if (value == 0) continue
            report.appendLine("    ${effect.displayName}: $value particles")
        }
    }

    private fun slowFrameMark(interval: Float): String =
        if (interval > SLOW_FRAME_MILLISECONDS) "  <-- over ${format(SLOW_FRAME_MILLISECONDS)} ms" else ""

    private fun phaseMark(average: Float, maximum: Float): String = when {
        maximum > PHASE_MAXIMUM_BUDGET_MILLISECONDS -> "  <-- max over ${format(PHASE_MAXIMUM_BUDGET_MILLISECONDS)} ms"
        average > PHASE_AVERAGE_BUDGET_MILLISECONDS -> "  <-- average over ${format(PHASE_AVERAGE_BUDGET_MILLISECONDS)} ms"
        else -> ""
    }

    private fun appendPhases(report: StringBuilder, frameCount: Int) {
        report.appendLine()
        report.appendLine("timings, ms per frame, whole run (average / max), last $frameCount frames average in brackets")
        for (phase in DiagnosticPhase.entries) {
            if (runPhaseCounts[phase.ordinal] == 0L) continue
            val average = (runPhaseSums[phase.ordinal] / runPhaseCounts[phase.ordinal]).toFloat()
            val maximum = runPhaseMaximums[phase.ordinal]
            var windowTotal = 0f
            var windowSamples = 0
            for (frameIndex in 0 until frameCount) {
                val value = phaseHistory[frameIndex * phaseCount + phase.ordinal]
                if (value < 0f) continue
                windowTotal += value
                windowSamples++
            }
            val windowAverage = if (windowSamples == 0) 0f else windowTotal / windowSamples
            report.appendLine(
                "  ${phase.label}: ${format(average)} / ${format(maximum)} (${format(windowAverage)})${
                    phaseMark(
                        average,
                        maximum
                    )
                }"
            )
        }
    }

    private fun appendCounters(report: StringBuilder, frameCount: Int) {
        report.appendLine()
        report.appendLine("work per frame, whole run (average / max), last $frameCount frames average in brackets")
        for (counter in DiagnosticCounter.entries) {
            val maximum = runCounterMaximums[counter.ordinal]
            if (maximum == 0) continue
            var windowTotal = 0L
            for (frameIndex in 0 until frameCount) windowTotal += counterHistory[frameIndex * counterCount + counter.ordinal]
            val average = runCounterSums[counter.ordinal].toFloat() / runFrames
            report.appendLine("  ${counter.label}: ${format(average)} / $maximum (${format(windowTotal.toFloat() / frameCount)})")
        }
    }

    private fun appendEffects(report: StringBuilder) {
        report.appendLine()
        report.appendLine("live particles by effect, whole run (average / max), draw ms average for complex shapes")
        for (effect in ParticleEffect.entries) {
            val particleMaximum = runEffectParticleMaximums[effect.ordinal]
            if (particleMaximum == 0) continue
            val drawSamples = runEffectDrawCounts[effect.ordinal]
            val drawAverage = if (drawSamples == 0L) 0f else (runEffectDrawSums[effect.ordinal] / drawSamples).toFloat()
            val drawSuffix = if (drawAverage > 0f) ", draw ${format(drawAverage)} ms" else ""
            report.appendLine("  ${effect.displayName}: ${format(runEffectParticleSums[effect.ordinal].toFloat() / runFrames)} / $particleMaximum$drawSuffix")
        }
    }

    private fun format(value: Float): String = ((value * 100f).toInt() / 100.0).toString()
}
