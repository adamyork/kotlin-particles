package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AssetService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticleEffectGenerator {

    companion object {
        const val GPU_FLOATS_PER_PARTICLE: Int = 24
        const val GPU_STATE_FLOATS_PER_PARTICLE: Int = 64
    }

    val effect: ParticleEffect

    val particles: ArrayList<Particle>

    val gpuSlotGroups: List<GpuSlotGroup>
        get() = emptyList()

    val companionEffects: List<ParticleEffect>
        get() = emptyList()

    fun populateColor(assetService: AssetService) {}

    fun create(request: ParticleEffectRequest): MutableList<Particle>

    fun adopt(particle: Particle)

    fun spawn(request: ParticleEffectRequest) {
        particles.addAll(create(request))
    }

    fun gpuSlotGroup(particle: Particle): GpuSlotGroup? = null

    fun shouldWriteGpuParticle(particle: Particle): Boolean = true

    fun writeGpuParticle(particle: Particle, targetBuffer: FloatArray, baseIndex: Int)

    fun writeGpuState(particle: Particle, targetBuffer: FloatArray, baseIndex: Int)

}
