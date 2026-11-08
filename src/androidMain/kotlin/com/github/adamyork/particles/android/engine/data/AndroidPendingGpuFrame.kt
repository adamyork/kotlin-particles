package com.github.adamyork.particles.android.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public data class AndroidPendingGpuFrame(
    val dirtySlotRanges: List<IntRange>,
    val subframeCount: Int,
    val deltaTime: Double,
    val worldTime: Double,
    val spriteTicksPerFrame: Double
)
