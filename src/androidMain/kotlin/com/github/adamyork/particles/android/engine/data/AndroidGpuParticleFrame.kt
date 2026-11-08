package com.github.adamyork.particles.android.engine.data

import com.github.adamyork.particles.common.data.ParticleViewPort

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal class AndroidGpuParticleFrame(
    val sourceBuffer: FloatArray,
    val dirtySlotRanges: List<IntRange>,
    val subframeCount: Int,
    val deltaTime: Double,
    val worldTime: Double,
    val spriteTicksPerFrame: Double,
    val viewPort: ParticleViewPort
)
