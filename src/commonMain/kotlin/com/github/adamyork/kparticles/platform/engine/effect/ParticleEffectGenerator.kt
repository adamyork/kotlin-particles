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
    }

    val effect: ParticleEffect

    val gpuSlotGroups: List<GpuSlotGroup>
        get() = emptyList()

    fun populateColor(assetService: AssetService) {}

    fun create(request: ParticleEffectRequest): MutableList<Particle>

    fun gpuSlotGroup(particle: Particle): GpuSlotGroup? = null

    fun shouldWriteGpuParticle(particle: Particle): Boolean = true

    fun writeGpuParticle(particle: Particle, targetBuffer: FloatArray, baseIndex: Int)

}
