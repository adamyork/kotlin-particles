package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
abstract class CommonTileCollision(
    particlePhysics: ParticlePhysics,
    spatialGrid: SpatialGrid,
    physicsSettingsService: PhysicsSettingsService,
) : BaseCollision(particlePhysics, spatialGrid, physicsSettingsService) {

    companion object {
        private const val TILE_SIZE: Int = 16
    }

}
