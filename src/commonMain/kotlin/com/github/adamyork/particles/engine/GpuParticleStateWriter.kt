package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.ParticleWriteResult
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface GpuParticleStateWriter {

    public companion object {
        public const val DEFAULT_GPU_PARTICLE_CAPACITY: Int = 32768
    }

    public fun createGpuParticleStateBuffer(maxParticles: Int = DEFAULT_GPU_PARTICLE_CAPACITY): FloatArray {
        throw EngineException("GPU particle state buffer is not implemented for this engine")
    }

    public fun getStyleSlotRanges(maxParticles: Int): Map<ParticleRenderStyle, IntRange> {
        throw EngineException("GPU style slot ranges are not implemented for this engine")
    }

    public fun writeGpuParticleStateSpawnBuffer(
        targetBuffer: FloatArray,
        maxParticles: Int = DEFAULT_GPU_PARTICLE_CAPACITY,
        startSlot: Int = 0,
        previouslyWrittenSlots: List<Int> = emptyList()
    ): ParticleWriteResult {
        throw EngineException("GPU particle state spawn buffer writes are not implemented for this engine")
    }

}
