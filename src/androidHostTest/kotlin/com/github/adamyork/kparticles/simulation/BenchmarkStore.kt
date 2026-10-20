package com.github.adamyork.kparticles.simulation

import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class BenchmarkStore(private val file: File) {

    fun load(): Map<String, Double> {
        if (!file.exists()) return emptyMap()
        val values = LinkedHashMap<String, Double>()
        for (line in file.readLines()) {
            if (line.isBlank()) continue
            val separatorIndex = line.indexOf('=')
            values[line.substring(0, separatorIndex)] = line.substring(separatorIndex + 1).toDouble()
        }
        return values
    }

    fun write(values: Map<String, Double>) {
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(values.toSortedMap().entries.joinToString(separator = "\n", postfix = "\n") { entry ->
            "${entry.key}=${entry.value}"
        })
    }

    fun compare(expected: Map<String, Double>, actual: Map<String, Double>): List<String> {
        val differences = ArrayList<String>()
        for (key in (expected.keys + actual.keys).toSortedSet()) {
            val expectedValue = expected[key]
            val actualValue = actual[key]
            if (expectedValue == null) {
                differences.add("$key is new: $actualValue")
            } else if (actualValue == null) {
                differences.add("$key is missing: expected $expectedValue")
            } else if (!isWithinTolerance(key, expectedValue, actualValue)) {
                differences.add("$key expected $expectedValue but was $actualValue")
            }
        }
        return differences
    }

    private fun isWithinTolerance(key: String, expected: Double, actual: Double): Boolean {
        val tolerance = BenchmarkTolerance.forKey(key) ?: return true
        val allowedDifference = max(tolerance.absolute, tolerance.relative * max(abs(expected), abs(actual)))
        return abs(expected - actual) <= allowedDifference
    }

    companion object {
        fun fromSystemProperties(): BenchmarkStore =
            BenchmarkStore(File(checkNotNull(System.getProperty("kparticles.benchmarkFile"))))

        fun isUpdateRequested(): Boolean = System.getProperty("kparticles.updateBenchmarks") == "true"
    }

}
