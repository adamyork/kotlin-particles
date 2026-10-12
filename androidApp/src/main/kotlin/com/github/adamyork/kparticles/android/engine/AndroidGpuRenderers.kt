package com.github.adamyork.kparticles.android.engine

import android.graphics.Canvas
import com.github.adamyork.kparticles.android.engine.data.AndroidPendingGpuFrame
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.ParticleDrawFunction
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.Renderers
import com.github.adamyork.kparticles.platform.engine.data.RenderType
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class AndroidGpuRenderers(
    particleFactory: ParticleFactory,
    private val physicsSettingsService: PhysicsSettingsService
) : Renderers<Canvas> {

    val gpuParticleBufferCapacity = ParticleFactory.DEFAULT_GPU_PARTICLE_CAPACITY
    val gpuParticleSpawnBuffer = particleFactory.createGpuParticleComputeBuffer(gpuParticleBufferCapacity)
    val gpuParticleRuntime = AndroidGpuParticleRuntime()
    internal var pendingGpuFrame: AndroidPendingGpuFrame? = null

    private val gpuDrawFunction: ParticleDrawFunction<Canvas> = { drawFunctionData ->
        val frame = pendingGpuFrame
        if (frame != null) {
            pendingGpuFrame = null
            gpuParticleRuntime.submitFrame(
                activeParticleCount = frame.activeParticleCount,
                sourceBuffer = gpuParticleSpawnBuffer,
                viewPort = drawFunctionData.viewPort,
                viewPortWidth = frame.viewPortWidth,
                viewPortHeight = frame.viewPortHeight,
                sizeMultiplier = physicsSettingsService.collisionParticleSizeMultiplier,
                deltaTimeSeconds = frame.deltaTimeSeconds,
                gravity = frame.gravity,
                tickRate = frame.tickRate,
                simulationSpeed = frame.simulationSpeed,
                gravityBoost = frame.gravityBoost,
                lifetimeDecay = frame.lifetimeDecay,
                shatterPoolFirstSlot = frame.shatterPoolFirstSlot,
                shatterPoolSlotCount = frame.shatterPoolSlotCount
            )
        }
    }

    override fun getForType(renderType: RenderType): ParticleDrawFunction<Canvas> = gpuDrawFunction

}