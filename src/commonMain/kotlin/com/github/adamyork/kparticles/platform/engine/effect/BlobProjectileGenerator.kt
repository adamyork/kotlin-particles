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
class BlobProjectileGenerator : AbstractProjectileGenerator(ParticleEffect.BLOB_PROJECTILE, BLOB_PROJECTILE_GPU_KIND) {

    companion object {
        const val PROJECTILE_BLOB_LOBE_COUNT: Int = 7
        const val PROJECTILE_BLOB_AMPLITUDE_RATIO: Float = 0.22f
        const val PROJECTILE_BLOB_BASE_FREQUENCY: Float = 0.05f
        const val PROJECTILE_BLOB_FREQUENCY_STEP: Float = 0.015f
        const val PROJECTILE_BLOB_PHASE_STEP: Float = 1.1f
        const val PROJECTILE_BLOB_SPLINE_TENSION: Float = 6f
        private const val BLOB_PROJECTILE_GPU_KIND: Float = 12f
    }
}
