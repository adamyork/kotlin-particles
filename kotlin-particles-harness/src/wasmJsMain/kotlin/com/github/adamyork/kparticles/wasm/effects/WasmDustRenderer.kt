package com.github.adamyork.kparticles.wasm.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.DiagnosticPhase
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import com.github.adamyork.particles.wasm.engine.WasmShapeRenderer
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.Canvas

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class WasmDustRenderer(
    shapeRenderer: WasmShapeRenderer
) : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = EffectStyles.DUST
    override val batchStyle: ParticleRenderStyle = ParticleRenderStyle.SHAPE
    override val drawOrder: Int = CpuDrawOrder.SHAPES
    override val diagnosticPhase: DiagnosticPhase = DiagnosticPhase.DRAW_SIMPLE_SHAPE
    override val drawFunction: ParticleDrawFunction<Canvas> = shapeRenderer.drawFunction

}
