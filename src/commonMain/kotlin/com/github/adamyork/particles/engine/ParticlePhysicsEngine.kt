package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.ParticleSubframeSchedule

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface ParticlePhysicsEngine {

    public fun applyPhysics(viewPort: ParticleViewPort): ParticleSubframeSchedule

}
