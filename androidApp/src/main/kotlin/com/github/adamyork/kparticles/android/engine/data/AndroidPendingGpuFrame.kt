package com.github.adamyork.kparticles.android.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal data class AndroidPendingGpuFrame(
    val dirtySlotRanges: List<IntRange>,
    val deltaTime: Double,
    val worldTime: Double,
    val spriteTicksPerFrame: Double
)
