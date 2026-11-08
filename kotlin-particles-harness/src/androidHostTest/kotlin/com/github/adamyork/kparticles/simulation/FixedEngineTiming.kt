package com.github.adamyork.kparticles.simulation

import com.github.adamyork.particles.service.EngineTiming

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class FixedEngineTiming(
    override val tickTargetPerSec: Int = TICKS_PER_SECOND,
    override val spriteAnimationFramePerSec: Int = SPRITE_FRAMES_PER_SECOND
) : EngineTiming {

    companion object {
        const val TICKS_PER_SECOND: Int = 50
        const val SPRITE_FRAMES_PER_SECOND: Int = 12
    }

}
