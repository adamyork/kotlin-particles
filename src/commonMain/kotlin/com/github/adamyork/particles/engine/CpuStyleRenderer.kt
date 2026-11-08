package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.DiagnosticPhase
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public interface CpuStyleRenderer<CanvasType> {

    public val renderStyle: ParticleRenderStyle

    public val batchStyle: ParticleRenderStyle
        get() = renderStyle

    public val drawOrder: Int

    public val diagnosticPhase: DiagnosticPhase

    public val drawFunction: ParticleDrawFunction<CanvasType>

}
