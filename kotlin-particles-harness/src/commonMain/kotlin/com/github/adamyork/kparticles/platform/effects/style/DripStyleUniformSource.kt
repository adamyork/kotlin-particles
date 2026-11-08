package com.github.adamyork.kparticles.platform.effects.style

import com.github.adamyork.particles.engine.GpuStyleUniformSource
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class DripStyleUniformSource(private val dripStyleSettings: DripStyleSettings) : GpuStyleUniformSource {

    override val renderStyle: ParticleRenderStyle = EffectStyles.DRIP

    override val floatCount: Int = 7

    override fun writeUniforms(targetBuffer: FloatArray, startIndex: Int) {
        var writeIndex = startIndex
        targetBuffer[writeIndex++] = dripStyleSettings.dripTrailRadiusRatio.toFloat()
        targetBuffer[writeIndex++] = dripStyleSettings.dripNeckLengthRatio.toFloat()
        targetBuffer[writeIndex++] = dripStyleSettings.dripGlossIntensity.toFloat()
        targetBuffer[writeIndex++] = dripStyleSettings.dripGlossStripeOffsetRatio.toFloat()
        targetBuffer[writeIndex++] = dripStyleSettings.dripGlossStripeWidthRatio.toFloat()
        targetBuffer[writeIndex++] = dripStyleSettings.dripGlossSpecularRadiusRatio.toFloat()
        targetBuffer[writeIndex] = dripStyleSettings.dripGlossSpecularOffsetRatio.toFloat()
    }

}
