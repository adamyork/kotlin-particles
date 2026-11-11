package com.github.adamyork.kparticles.android.effects

import android.graphics.Canvas
import com.github.adamyork.particles.android.engine.AndroidShapeRenderer
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AndroidDustRenderer(
    shapeRenderer: AndroidShapeRenderer
) : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = EffectStyles.DUST
    override val batchStyle: ParticleRenderStyle = ParticleRenderStyle.SHAPE
    override val drawOrder: Int = CpuDrawOrder.SHAPES
    override val drawFunction: ParticleDrawFunction<Canvas> = shapeRenderer.drawFunction

}
