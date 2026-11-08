package com.github.adamyork.kparticles.platform.assets

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.service.EngineTiming
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AssetEngineTiming(private val assetService: AssetService) : EngineTiming {

    override val tickTargetPerSec: Int
        get() = assetService.appProperties.engine.tickTargetPerSec

    override val spriteAnimationFramePerSec: Int
        get() = assetService.appProperties.engine.spriteAnimationFramePerSec

}
