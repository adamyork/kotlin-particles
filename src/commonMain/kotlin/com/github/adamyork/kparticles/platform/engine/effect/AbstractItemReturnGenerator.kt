package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractItemReturnGenerator(
    effect: ParticleEffect,
    itemReturnMaxFrames: Int,
    physicsSettingsService: PhysicsSettingsService
) : AbstractGuidedLaunchGenerator(
    effect, ITEM_RETURN_GPU_KIND, ITEM_RETURN_GPU_SLOT_GROUP, itemReturnMaxFrames, physicsSettingsService
) {

    private companion object {
        private const val ITEM_RETURN_GPU_KIND: Float = 3f
        private const val ITEM_RETURN_MAX_ACTIVE_PARTICLES: Int = 1
        private val ITEM_RETURN_GPU_SLOT_GROUP: GpuSlotGroup = GpuSlotGroup(ITEM_RETURN_MAX_ACTIVE_PARTICLES)
    }

    override fun launchColor(): Color = Color.White

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        super.writeGpuParameters(particle, targetBuffer, parameterIndex)
        targetBuffer[parameterIndex + 2] = (particle.animation?.maxFrames ?: 1).toFloat()
    }
}
