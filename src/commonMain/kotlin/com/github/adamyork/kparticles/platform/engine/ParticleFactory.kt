package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.Direction
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect
import com.github.adamyork.kparticles.platform.engine.data.ParticleWriteResult
import com.github.adamyork.kparticles.platform.service.AssetService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticleFactory {

    companion object {
        const val DEFAULT_GPU_PARTICLE_CAPACITY: Int = 8192
    }

    fun populateColorMap(assetService: AssetService)

    fun generators(): List<ParticleEffectGenerator>

    fun createGpuParticleComputeBuffer(maxParticles: Int = DEFAULT_GPU_PARTICLE_CAPACITY): FloatArray {
        throw EngineException("GPU particle compute buffer is not implemented for this engine")
    }

    fun createGpuParticleStateBuffer(maxParticles: Int = DEFAULT_GPU_PARTICLE_CAPACITY): FloatArray {
        throw EngineException("GPU particle state buffer is not implemented for this engine")
    }

    fun gpuSlotRange(effect: ParticleEffect, maxParticles: Int): IntRange = IntRange.EMPTY

    fun create(
        type: ParticleEffect,
        x: Double,
        y: Double,
        viewPort: ViewPort,
        destinationX: Double? = null,
        destinationY: Double? = null,
        direction: Direction
    )

    fun writeGpuParticleSpawnBuffer(
        targetBuffer: FloatArray,
        maxParticles: Int = DEFAULT_GPU_PARTICLE_CAPACITY,
        startSlot: Int = 0,
        previouslyWrittenSlots: List<Int> = emptyList()
    ): ParticleWriteResult {
        throw EngineException("GPU particle spawn buffer writes are not implemented for this engine")
    }

    fun writeGpuParticleStateSpawnBuffer(
        targetBuffer: FloatArray,
        maxParticles: Int = DEFAULT_GPU_PARTICLE_CAPACITY,
        startSlot: Int = 0,
        previouslyWrittenSlots: List<Int> = emptyList()
    ): ParticleWriteResult {
        throw EngineException("GPU particle state spawn buffer writes are not implemented for this engine")
    }

}
