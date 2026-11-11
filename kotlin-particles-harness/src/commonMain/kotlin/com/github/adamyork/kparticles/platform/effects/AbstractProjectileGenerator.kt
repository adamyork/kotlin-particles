package com.github.adamyork.kparticles.platform.effects

import androidx.compose.ui.graphics.Color
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractProjectileGenerator(
    effect: ParticleEffect,
    physicsSettingsService: ParticlePhysicsSettingsService,
    private val assetService: AssetService
) : AbstractGuidedLaunchGenerator(
    effect, PROJECTILE_GPU_SLOT_GROUP, PROJECTILE_MAX_FRAMES, physicsSettingsService
) {

    private companion object {
        private const val PROJECTILE_MAX_ACTIVE_PARTICLES: Int = 1
        private const val PROJECTILE_MAX_FRAMES: Int = 1
        private val PROJECTILE_GPU_SLOT_GROUP: GpuSlotGroup = GpuSlotGroup(PROJECTILE_MAX_ACTIVE_PARTICLES)
    }

    private var color: Color = Color.White

    override fun onAssetsReady() {
        color = ConfiguredColors.createColor(assetService.appProperties.particle.enemy.projectile.color)
    }

    override fun launchColor(): Color = color
}
