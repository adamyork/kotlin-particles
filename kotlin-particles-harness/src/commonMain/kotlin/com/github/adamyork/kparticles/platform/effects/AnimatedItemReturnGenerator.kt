package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.kparticles.ParticleScope
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AnimatedItemReturnGenerator(
    physicsSettingsService: ParticlePhysicsSettingsService
) : AbstractItemReturnGenerator(ParticleEffects.ANIMATED_ITEM_RETURN, ANIMATED_ITEM_RETURN_MAX_FRAMES, physicsSettingsService) {

    private companion object {
        private const val ANIMATED_ITEM_RETURN_MAX_FRAMES: Int = 8
    }
}
