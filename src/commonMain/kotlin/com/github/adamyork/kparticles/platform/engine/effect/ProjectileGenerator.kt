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
class ProjectileGenerator : AbstractProjectileGenerator(ParticleEffect.PROJECTILE, PROJECTILE_GPU_KIND) {

    private companion object {
        private const val PROJECTILE_GPU_KIND: Float = 2f
    }
}
