package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.Direction
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.AssetService

interface Particles {

    companion object {
        const val DEFAULT_GPU_PARTICLE_CAPACITY: Int = 4096
    }

    fun populateColorMap(assetService: AssetService)

    fun createGpuParticleComputeBuffer(maxParticles: Int = DEFAULT_GPU_PARTICLE_CAPACITY): FloatArray {
        throw EngineException("GPU particle compute buffer is not implemented for this engine")
    }

    fun create(
        mode: String,
        x: Double,
        y: Double,
        viewPort: ViewPort,
        destinationX: Double? = null,
        destinationY: Double? = null,
        direction: Direction
    ): MutableList<Particle>

//    fun writeGpuParticleSpawnBuffer(
//        player: Player,
//        mapParticles: List<Particle>,
//        targetBuffer: FloatArray,
//        maxParticles: Int = DEFAULT_GPU_PARTICLE_CAPACITY,
//        startSlot: Int = 0,
//        previouslyWrittenSlots: List<Int> = emptyList()
//    ): ParticleWriteResult {
//        throw EngineException("GPU particle spawn buffer writes are not implemented for this engine")
//    }

}