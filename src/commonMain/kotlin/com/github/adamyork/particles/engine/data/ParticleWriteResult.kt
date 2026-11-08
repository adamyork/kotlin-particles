package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class ParticleWriteResult(
    val activeCount: Int,
    val dirtySlotRanges: List<IntRange>,
    val writtenSlots: List<Int>
)

