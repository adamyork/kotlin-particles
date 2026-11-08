package com.github.adamyork.kparticles.platform.effects.style

import com.github.adamyork.kparticles.ParticleScope
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class DripStyleSettings {

    var dripTrailRadiusRatio: Double = 0.45
    var dripNeckLengthRatio: Double = 3.0
    var dripGlossIntensity: Double = 0.6
    var dripGlossStripeOffsetRatio: Double = 0.45
    var dripGlossStripeWidthRatio: Double = 0.2
    var dripGlossSpecularRadiusRatio: Double = 0.3
    var dripGlossSpecularOffsetRatio: Double = 0.4

}
