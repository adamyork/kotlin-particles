package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class AnimatedItemReturnGenerator :
    AbstractItemReturnGenerator(ParticleEffect.ANIMATED_ITEM_RETURN, ANIMATED_ITEM_RETURN_MAX_FRAMES) {

    private companion object {
        private const val ANIMATED_ITEM_RETURN_MAX_FRAMES: Int = 8
    }
}
