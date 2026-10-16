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
class ProjectileGenerator(
    physicsSettingsService: PhysicsSettingsService
) : AbstractProjectileGenerator(ParticleEffect.PROJECTILE, PROJECTILE_GPU_KIND, physicsSettingsService) {

    private companion object {
        private const val PROJECTILE_GPU_KIND: Float = 2f
    }
}
