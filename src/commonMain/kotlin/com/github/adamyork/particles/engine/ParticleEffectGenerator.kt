package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface ParticleEffectGenerator {

    public companion object {
        public const val GPU_STATE_FLOATS_PER_PARTICLE: Int = 64
    }

    public val effect: ParticleEffect

    public val particles: ArrayList<Particle>

    public val gpuSlotGroups: List<GpuSlotGroup>
        get() = emptyList()

    public val companionEffects: List<ParticleEffect>
        get() = emptyList()

    public fun onAssetsReady() {}

    public fun create(request: ParticleEffectRequest): MutableList<Particle>

    public fun adopt(particle: Particle)

    public fun spawn(request: ParticleEffectRequest) {
        particles.addAll(create(request))
    }

    public fun gpuSlotGroup(particle: Particle): GpuSlotGroup? = null

    public fun writeGpuState(particle: Particle, targetBuffer: FloatArray, baseIndex: Int)
}
