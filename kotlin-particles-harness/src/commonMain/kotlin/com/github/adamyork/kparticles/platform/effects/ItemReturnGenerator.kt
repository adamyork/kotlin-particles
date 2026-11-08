package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.data.*
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.service.PhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class ItemReturnGenerator(
    physicsSettingsService: PhysicsSettingsService
) : AbstractItemReturnGenerator(ParticleEffects.ITEM_RETURN, ITEM_RETURN_MAX_FRAMES, physicsSettingsService) {

    private companion object {
        private const val ITEM_RETURN_MAX_FRAMES: Int = 1
    }
}
