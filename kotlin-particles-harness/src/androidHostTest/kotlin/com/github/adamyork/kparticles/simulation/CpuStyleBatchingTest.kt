package com.github.adamyork.kparticles.simulation

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.gui.data.HarnessViewPort
import com.github.adamyork.kparticles.platform.effects.ParticleEffects
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.AbstractGenerator
import com.github.adamyork.particles.engine.CommonParticleRenderEngine
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.CpuStyleRenderers
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.particles.engine.EngineException
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.data.CircleDimensions
import com.github.adamyork.particles.engine.data.DiagnosticPhase
import com.github.adamyork.particles.engine.data.Particle
import com.github.adamyork.particles.engine.data.ParticleAppearance
import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleEffectRequest
import com.github.adamyork.particles.engine.data.ParticleLifecycle
import com.github.adamyork.particles.engine.data.ParticleMaterial
import com.github.adamyork.particles.engine.data.ParticleTransform
import com.github.adamyork.particles.engine.data.ParticleVector
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class CpuStyleBatchingTest {

    private class StubGenerator(effect: ParticleEffect) : AbstractGenerator(effect) {

        override fun create(request: ParticleEffectRequest): MutableList<Particle> = mutableListOf()

        fun addOneParticle() {
            adopt(
                Particle(
                    id = 0,
                    lifecycle = ParticleLifecycle(lifetime = 100.0),
                    appearance = ParticleAppearance(color = Color.White),
                    dimensions = CircleDimensions(radius = 4.0, maxRadius = 4.0),
                    transform = ParticleTransform(position = ParticleVector(10.0, 10.0, 0.0)),
                    material = ParticleMaterial(density = 1.0)
                )
            )
        }

    }

    private class RecordingRenderer(
        override val renderStyle: ParticleRenderStyle,
        override val drawOrder: Int,
        override val batchStyle: ParticleRenderStyle = renderStyle,
        private val calls: MutableList<String>
    ) : CpuStyleRenderer<Unit> {

        override val diagnosticPhase: DiagnosticPhase = DiagnosticPhase.DRAW_SIMPLE_SHAPE

        override val drawFunction: ParticleDrawFunction<Unit> = { data ->
            calls.add(renderStyle.id + ":" + data.generators.joinToString(",") { generator -> generator.effect.id })
        }

    }

    private val viewPort = HarnessViewPort(0, 768, 1024, 768)

    private fun engineWith(
        generators: List<StubGenerator>,
        renderers: List<CpuStyleRenderer<Unit>>
    ): Pair<CommonParticleRenderEngine<Unit>, ParticleEffectRegistry> {
        val registry = ParticleEffectRegistry(ParticleEffects.ALL)
        for (generator in generators) registry.register(generator)
        val engine = CommonParticleRenderEngine(registry, CpuStyleRenderers(renderers), EngineDiagnostics(registry))
        return engine to registry
    }

    @Test
    fun stylesDrawInDrawOrderAndABatchStyleMergesGeneratorsIntoOneCall() {
        val calls = ArrayList<String>()
        val dust = StubGenerator(ParticleEffects.DUST)
        val collision = StubGenerator(ParticleEffects.COLLISION)
        val bubble = StubGenerator(ParticleEffects.BUBBLE)
        val image = StubGenerator(ParticleEffects.ITEM_RETURN)
        val generators = listOf(image, bubble, dust, collision)
        generators.forEach { generator -> generator.addOneParticle() }
        val (engine, _) = engineWith(
            generators,
            listOf(
                RecordingRenderer(ParticleRenderStyle.IMAGE, CpuDrawOrder.IMAGES, calls = calls),
                RecordingRenderer(EffectStyles.BUBBLE, CpuDrawOrder.COMPLEX_SHAPES, calls = calls),
                RecordingRenderer(EffectStyles.DUST, CpuDrawOrder.SHAPES, batchStyle = ParticleRenderStyle.SHAPE, calls = calls),
                RecordingRenderer(ParticleRenderStyle.SHAPE, CpuDrawOrder.SHAPES, calls = calls)
            )
        )
        engine.draw(viewPort, Unit)
        assertEquals(listOf("SHAPE:DUST,COLLISION", "BUBBLE:BUBBLE", "IMAGE:ITEM_RETURN"), calls)
    }

    @Test
    fun batchesWithoutParticlesAreSkipped() {
        val calls = ArrayList<String>()
        val collision = StubGenerator(ParticleEffects.COLLISION)
        val bubble = StubGenerator(ParticleEffects.BUBBLE)
        collision.addOneParticle()
        val (engine, _) = engineWith(
            listOf(collision, bubble),
            listOf(
                RecordingRenderer(ParticleRenderStyle.SHAPE, CpuDrawOrder.SHAPES, calls = calls),
                RecordingRenderer(EffectStyles.BUBBLE, CpuDrawOrder.COMPLEX_SHAPES, calls = calls)
            )
        )
        engine.draw(viewPort, Unit)
        assertEquals(listOf("SHAPE:COLLISION"), calls)
    }

    @Test
    fun aMissingRendererFailsFastAndDuplicatesAreRejected() {
        val bubble = StubGenerator(ParticleEffects.BUBBLE)
        bubble.addOneParticle()
        val (engine, _) = engineWith(listOf(bubble), listOf(RecordingRenderer(ParticleRenderStyle.SHAPE, CpuDrawOrder.SHAPES, calls = ArrayList())))
        assertFailsWith<EngineException> { engine.draw(viewPort, Unit) }
        assertFailsWith<EngineException> {
            CpuStyleRenderers(
                listOf(
                    RecordingRenderer(ParticleRenderStyle.SHAPE, CpuDrawOrder.SHAPES, calls = ArrayList()),
                    RecordingRenderer(ParticleRenderStyle.SHAPE, CpuDrawOrder.SHAPES, calls = ArrayList())
                )
            )
        }
    }

    @Test
    fun drawingSealsTheRegistry() {
        val shape = StubGenerator(ParticleEffects.COLLISION)
        val (engine, registry) = engineWith(
            listOf(shape),
            listOf(RecordingRenderer(ParticleRenderStyle.SHAPE, CpuDrawOrder.SHAPES, calls = ArrayList()))
        )
        assertTrue(!registry.isSealed)
        engine.draw(viewPort, Unit)
        assertTrue(registry.isSealed)
    }

}
