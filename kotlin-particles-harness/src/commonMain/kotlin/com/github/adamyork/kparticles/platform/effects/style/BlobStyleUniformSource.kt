package com.github.adamyork.kparticles.platform.effects.style

import com.github.adamyork.particles.engine.GpuStyleUniformSource
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class BlobStyleUniformSource : GpuStyleUniformSource {

    override val renderStyle: ParticleRenderStyle = EffectStyles.BLOB

    override val floatCount: Int = 5

    override fun writeUniforms(targetBuffer: FloatArray, startIndex: Int) {
        var writeIndex = startIndex
        targetBuffer[writeIndex++] = BlobStyleParameters.LOBE_COUNT.toFloat()
        targetBuffer[writeIndex++] = BlobStyleParameters.AMPLITUDE_RATIO
        targetBuffer[writeIndex++] = BlobStyleParameters.BASE_FREQUENCY
        targetBuffer[writeIndex++] = BlobStyleParameters.FREQUENCY_STEP
        targetBuffer[writeIndex] = BlobStyleParameters.PHASE_STEP
    }

}
