package com.github.adamyork.kparticles.android.engine.data

import com.github.adamyork.kparticles.platform.common.data.ViewPort

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal class AndroidGpuParticleFrame(
    val sourceBuffer: FloatArray,
    val dirtySlotRanges: List<IntRange>,
    val deltaTime: Double,
    val worldTime: Double,
    val spriteTicksPerFrame: Double,
    val viewPort: ViewPort
)
