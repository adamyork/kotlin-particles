package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.DrawFunctionData
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonParticleRenderEngine<CanvasType>(
    private val particleEffectRegistry: ParticleEffectRegistry,
    private val styleRenderers: CpuStyleRenderers<CanvasType>,
    private val engineDiagnostics: EngineDiagnostics
) : ParticleRenderEngine<CanvasType> {

    private class Batch<CanvasType>(
        val renderer: CpuStyleRenderer<CanvasType>,
        val generators: List<ParticleEffectGenerator>
    )

    private val batches: List<Batch<CanvasType>> by lazy {
        val generatorsByBatchStyle = LinkedHashMap<ParticleRenderStyle, ArrayList<ParticleEffectGenerator>>()
        for (generator in particleEffectRegistry.generators()) {
            val batchStyle = styleRenderers.rendererFor(generator.effect.renderStyle).batchStyle
            generatorsByBatchStyle.getOrPut(batchStyle) { ArrayList() }.add(generator)
        }
        generatorsByBatchStyle
            .map { entry -> Batch(styleRenderers.rendererFor(entry.key), entry.value) }
            .sortedBy { batch -> batch.renderer.drawOrder }
    }

    override fun draw(viewPort: ParticleViewPort, canvas: CanvasType) {
        particleEffectRegistry.seal()
        for (batch in batches) {
            if (batch.generators.none { generator -> generator.particles.isNotEmpty() }) continue
            val drawFunctionData = DrawFunctionData(batch.generators, viewPort, canvas)
            val batchMark = engineDiagnostics.startTimer()
            batch.renderer.drawFunction(drawFunctionData)
            engineDiagnostics.stopTimer(batch.renderer.diagnosticPhase, batchMark)
        }
    }

}
