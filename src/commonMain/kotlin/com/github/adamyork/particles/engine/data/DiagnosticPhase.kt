package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public enum class DiagnosticPhase(public val label: String) {
    PHYSICS("physics"),
    COLLISION_TOTAL("collision total"),
    COLLISION_PLACE("  place particles"),
    GRAVITATION("  gravitation"),
    PAIR_SOLVE("  pair solve"),
    POSITION_CORRECTION("  position correction"),
    FRAGMENT_ADOPT("  fragment adopt"),
    BOUNDARY("  boundary"),
    DRAW_PARTICLES("draw particles"),
    DRAW_SIMPLE_SHAPE("  simple shapes"),
    SIMPLE_GROUPING("    grouping"),
    SIMPLE_FILL("    fill"),
    DRAW_COMPLEX_SHAPE("  complex shapes"),
    DRAW_SPRITE("  sprites"),
    SNAPSHOT("surface snapshot")
}
