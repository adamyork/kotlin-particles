package com.github.adamyork.kparticles.android.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.CommonTileCollision
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.SpatialGrid
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class AndroidTileCollision(
    particlePhysics: ParticlePhysics,
    spatialGrid: SpatialGrid,
    physicsSettingsService: PhysicsSettingsService,
) : CommonTileCollision(particlePhysics, spatialGrid, physicsSettingsService)
