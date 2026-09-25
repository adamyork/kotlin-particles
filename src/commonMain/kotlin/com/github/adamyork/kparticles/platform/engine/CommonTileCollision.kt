package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
abstract class CommonTileCollision(
    physics: Physics,
) : BaseCollision(physics) {

    companion object {
        private const val TILE_SIZE: Int = 16
    }

}
