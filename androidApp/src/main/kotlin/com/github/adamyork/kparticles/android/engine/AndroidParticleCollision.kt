package com.github.adamyork.kparticles.android.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.CommonParticleCollision
import com.github.adamyork.kparticles.platform.engine.ContactDetector
import com.github.adamyork.kparticles.platform.engine.ContactSolver
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
class AndroidParticleCollision(
    particlePhysics: ParticlePhysics,
    spatialGrid: SpatialGrid,
    contactDetector: ContactDetector,
    contactSolver: ContactSolver,
    physicsSettingsService: PhysicsSettingsService,
) : CommonParticleCollision(particlePhysics, spatialGrid, contactDetector, contactSolver, physicsSettingsService)
