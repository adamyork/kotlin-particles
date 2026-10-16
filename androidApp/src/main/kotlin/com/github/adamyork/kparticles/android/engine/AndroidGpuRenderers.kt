package com.github.adamyork.kparticles.android.engine

import android.graphics.Canvas
import com.github.adamyork.kparticles.android.engine.data.AndroidPendingGpuFrame
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.GpuUniformWriter
import com.github.adamyork.kparticles.platform.engine.ParticleDrawFunction
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.Renderers
import com.github.adamyork.kparticles.platform.engine.data.RenderType
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class AndroidGpuRenderers(
    particleFactory: ParticleFactory,
    gpuUniformWriter: GpuUniformWriter
) : Renderers<Canvas> {

    val gpuParticleBufferCapacity = ParticleFactory.DEFAULT_GPU_PARTICLE_CAPACITY
    val gpuParticleSpawnBuffer = particleFactory.createGpuParticleStateBuffer(gpuParticleBufferCapacity)
    val gpuParticleRuntime = AndroidGpuParticleRuntime(gpuUniformWriter)
    internal var pendingGpuFrame: AndroidPendingGpuFrame? = null

    private val gpuDrawFunction: ParticleDrawFunction<Canvas> = { drawFunctionData ->
        val frame = pendingGpuFrame
        if (frame != null) {
            pendingGpuFrame = null
            gpuParticleRuntime.submitFrame(
                sourceBuffer = gpuParticleSpawnBuffer,
                dirtySlotRanges = frame.dirtySlotRanges,
                deltaTime = frame.deltaTime,
                worldTime = frame.worldTime,
                spriteTicksPerFrame = frame.spriteTicksPerFrame,
                viewPort = drawFunctionData.viewPort
            )
        }
    }

    override fun getForType(renderType: RenderType): ParticleDrawFunction<Canvas> = gpuDrawFunction

}
