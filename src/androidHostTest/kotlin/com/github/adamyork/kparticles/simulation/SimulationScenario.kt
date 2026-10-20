package com.github.adamyork.kparticles.simulation

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class SimulationScenario(
    val name: String,
    val frameTicks: List<Double>,
    val checkpointFrames: Set<Int>,
    val spawnFrames: Set<Int> = setOf(0)
) {

    companion object {
        val STEADY = SimulationScenario(
            name = "steady",
            frameTicks = List(360) { 1.0 },
            checkpointFrames = setOf(60, 180, 360)
        )

        val SLOW_MOTION = SimulationScenario(
            name = "slowMotion",
            frameTicks = List(480) { 0.5 },
            checkpointFrames = setOf(120, 360, 480)
        )

        val FAST = SimulationScenario(
            name = "fast",
            frameTicks = List(180) { 2.0 },
            checkpointFrames = setOf(30, 90, 180)
        )

        val HITCHES = SimulationScenario(
            name = "hitches",
            frameTicks = List(60) { 1.0 } + listOf(10.0) + List(60) { 1.0 } + listOf(30.0) + List(60) { 1.0 },
            checkpointFrames = setOf(60, 61, 121, 122, 182)
        )

        val HEAVY = SimulationScenario(
            name = "heavy",
            frameTicks = List(1500) { 1.0 },
            checkpointFrames = setOf(100, 400, 800, 1500),
            spawnFrames = setOf(0, 10, 20, 30)
        )

        val ALL = listOf(STEADY, SLOW_MOTION, FAST, HITCHES)
    }

}
