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
) : AbstractCollision(particlePhysics, spatialGrid, physicsSettingsService)