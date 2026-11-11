package com.github.adamyork.kparticles.simulation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class PhysicsBenchmarkTest {

    private companion object {
        const val FLOATING_POINT_NOISE_SCALE: Double = 1.0 + 4.4e-16
        const val MAXIMUM_REPORTED_DIFFERENCES: Int = 40
    }

    @Test
    fun simulationMatchesStoredBenchmarks() {
        val benchmarkStore = BenchmarkStore.fromSystemProperties()
        val startNanoseconds = System.nanoTime()
        val actualValues = SimulationRunner.runAll()
        val elapsedMilliseconds = (System.nanoTime() - startNanoseconds) / 1_000_000
        println("simulation of ${actualValues.size} metrics took $elapsedMilliseconds ms")
        val expectedValues = benchmarkStore.load()
        val differences = benchmarkStore.compare(expectedValues, actualValues)
        if (BenchmarkStore.isUpdateRequested()) {
            println("benchmark drift: ${differences.size} differences")
            differences.forEach { println("  $it") }
            benchmarkStore.write(actualValues)
            println("benchmarks written for ${actualValues.size} metrics")
            return
        }
        assertTrue(expectedValues.isNotEmpty(), "no benchmarks stored; run ./gradlew updateBenchmarks")
        if (differences.isNotEmpty()) {
            val differencesByScenario = differences.groupingBy { difference ->
                difference.split('.').take(2).joinToString(".")
            }.eachCount()
            fail(
                "${differences.size} benchmark differences; if intended run ./gradlew updateBenchmarks\n" +
                    differencesByScenario.entries.joinToString("\n") { "${it.key}: ${it.value}" } + "\n\n" +
                    differences.take(MAXIMUM_REPORTED_DIFFERENCES).joinToString("\n")
            )
        }
    }

    @Test
    fun benchmarksSurviveFloatingPointNoise() {
        val benchmarkStore = BenchmarkStore.fromSystemProperties()
        val expectedValues = SimulationRunner.runAll()
        val noisyValues = SimulationRunner.runAll(gravityScale = FLOATING_POINT_NOISE_SCALE)
        val differences = benchmarkStore.compare(expectedValues, noisyValues)
        assertTrue(
            differences.isEmpty(),
            "benchmarks are too tight for platform floating point differences:\n" +
                differences.take(MAXIMUM_REPORTED_DIFFERENCES).joinToString("\n")
        )
    }

}
