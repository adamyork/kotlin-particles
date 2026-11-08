package com.github.adamyork.particles.android.engine

import android.graphics.Canvas
import com.github.adamyork.particles.android.engine.data.AndroidPendingGpuFrame
import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.GpuParticleStateWriter

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class AndroidGpuRenderers(
    gpuParticleStateWriter: GpuParticleStateWriter,
    gpuDataPacketWriter: GpuDataPacketWriter
) {

    public val gpuParticleBufferCapacity: Int = GpuParticleStateWriter.DEFAULT_GPU_PARTICLE_CAPACITY
    public val gpuParticleSpawnBuffer: FloatArray = gpuParticleStateWriter.createGpuParticleStateBuffer(gpuParticleBufferCapacity)
    public val gpuParticleRuntime: AndroidGpuParticleRuntime = AndroidGpuParticleRuntime(gpuDataPacketWriter)
    public var pendingGpuFrame: AndroidPendingGpuFrame? = null

    public val drawFunction: ParticleDrawFunction<Canvas> = { drawFunctionData ->
        val frame = pendingGpuFrame
        if (frame != null) {
            pendingGpuFrame = null
            gpuParticleRuntime.submitFrame(
                sourceBuffer = gpuParticleSpawnBuffer,
                dirtySlotRanges = frame.dirtySlotRanges,
                subframeCount = frame.subframeCount,
                deltaTime = frame.deltaTime,
                worldTime = frame.worldTime,
                spriteTicksPerFrame = frame.spriteTicksPerFrame,
                viewPort = drawFunctionData.viewPort
            )
        }
    }


}
