package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class ParticleRunningStatistic {

    public var current: Double = 0.0
        private set

    public var minimum: Double = 0.0
        private set

    public var maximum: Double = 0.0
        private set

    public val average: Double
        get() = if (sampleCount == 0L) 0.0 else sampleSum / sampleCount

    private var sampleCount = 0L

    private var sampleSum = 0.0

    public fun updateCurrent(value: Double) {
        current = value
    }

    public fun record(value: Double) {
        current = value
        if (sampleCount == 0L || value < minimum) minimum = value
        if (sampleCount == 0L || value > maximum) maximum = value
        sampleSum += value
        sampleCount++
    }

    public fun reset() {
        sampleCount = 0L
        sampleSum = 0.0
        current = 0.0
        minimum = 0.0
        maximum = 0.0
    }
}
