package com.github.adamyork.kparticles.platform.effects

import androidx.compose.ui.graphics.Color
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractItemReturnGenerator(
    effect: ParticleEffect,
    itemReturnMaxFrames: Int,
    physicsSettingsService: ParticlePhysicsSettingsService
) : AbstractGuidedLaunchGenerator(
    effect, ITEM_RETURN_GPU_SLOT_GROUP, itemReturnMaxFrames, physicsSettingsService
) {

    private companion object {
        private const val ITEM_RETURN_MAX_ACTIVE_PARTICLES: Int = 1
        private val ITEM_RETURN_GPU_SLOT_GROUP: GpuSlotGroup = GpuSlotGroup(ITEM_RETURN_MAX_ACTIVE_PARTICLES)
    }

    override fun launchColor(): Color = Color.White
}
