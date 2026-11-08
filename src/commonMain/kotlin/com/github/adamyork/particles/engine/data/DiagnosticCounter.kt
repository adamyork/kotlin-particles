package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public enum class DiagnosticCounter(public val label: String) {
    SUBFRAMES("subframes"),
    AWAKE_PARTICLES("awake particles"),
    ASLEEP_PARTICLES("asleep particles"),
    GRID_PARTICLES("grid particles"),
    SMALL_PARTICLES("  small (in cells)"),
    LARGE_PARTICLES("  large (bigger than a cell)"),
    GRID_TESTED_PAIRS("pairs tested by grid"),
    CANDIDATE_PAIRS("pairs with overlapping bounds"),
    CONTACT_PAIRS("pairs in contact"),
    SIMPLE_DRAWN("simple shapes drawn"),
    SIMPLE_CULLED("simple shapes culled"),
    SIMPLE_COLOR_GROUPS("simple shape colour groups")
}
