package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.kparticles.ParticleScope
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class ProjectileGenerator(
    physicsSettingsService: ParticlePhysicsSettingsService,
    assetService: AssetService
) : AbstractProjectileGenerator(ParticleEffects.PROJECTILE, physicsSettingsService, assetService)
