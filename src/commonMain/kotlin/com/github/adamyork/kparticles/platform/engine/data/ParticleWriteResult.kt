package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ParticleWriteResult(
    val activeCount: Int,
    val dirtySlotRanges: List<IntRange>,
    val writtenSlots: List<Int>
)

