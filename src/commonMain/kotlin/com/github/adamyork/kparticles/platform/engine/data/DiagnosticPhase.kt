package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
enum class DiagnosticPhase(val label: String) {
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
    DRAW_ANIMATED_SPRITE("  animated sprites"),
    SNAPSHOT("surface snapshot")
}
