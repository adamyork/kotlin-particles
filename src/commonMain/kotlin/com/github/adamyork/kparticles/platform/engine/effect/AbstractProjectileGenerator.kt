package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractProjectileGenerator(
    effect: ParticleEffect,
    projectileGpuKind: Float,
    physicsSettingsService: PhysicsSettingsService
) : AbstractGuidedLaunchGenerator(
    effect, projectileGpuKind, PROJECTILE_GPU_SLOT_GROUP, PROJECTILE_MAX_FRAMES, physicsSettingsService
) {

    private companion object {
        private const val PROJECTILE_MAX_ACTIVE_PARTICLES: Int = 1
        private const val PROJECTILE_MAX_FRAMES: Int = 1
        private val PROJECTILE_GPU_SLOT_GROUP: GpuSlotGroup = GpuSlotGroup(PROJECTILE_MAX_ACTIVE_PARTICLES)
    }

    private var color: Color = Color.White

    override fun populateColor(assetService: AssetService) {
        color = createColor(assetService.appProperties.particle.enemy.projectile.color)
    }

    override fun launchColor(): Color = color
}
