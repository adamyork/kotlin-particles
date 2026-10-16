package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class AnimatedItemReturnGenerator(
    physicsSettingsService: PhysicsSettingsService
) : AbstractItemReturnGenerator(ParticleEffect.ANIMATED_ITEM_RETURN, ANIMATED_ITEM_RETURN_MAX_FRAMES, physicsSettingsService) {

    private companion object {
        private const val ANIMATED_ITEM_RETURN_MAX_FRAMES: Int = 8
    }
}
