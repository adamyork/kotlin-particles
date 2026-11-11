package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CommonGpuParticleStateWriter
import com.github.adamyork.particles.engine.GpuParticleStateWriter
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import com.github.adamyork.particles.engine.shader.GpuBlendMode
import com.github.adamyork.particles.engine.shader.GpuRenderPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class GpuSlotRangeTest {

    private val maxParticles = GpuParticleStateWriter.DEFAULT_GPU_PARTICLE_CAPACITY

    private val registry = SimulationHarness(SimulationRunner.SEED).particleEffectRegistry

    private val styleRanges = CommonGpuParticleStateWriter(registry).getStyleSlotRanges(maxParticles)

    private val styleShaders = TestStyleShaders.create()

    @Test
    fun dustHasItsOwnSmallRangeAndTheShapePassExcludesTheDustSlots() {
        val dustRange = styleRanges[EffectStyles.DUST]
        assertNotNull(dustRange)
        assertEquals(1024, dustRange.last - dustRange.first + 1)
        val shapeRange = styleRanges[ParticleRenderStyle.SHAPE]
        assertNotNull(shapeRange)
        assertTrue(shapeRange.last < dustRange.first || shapeRange.first > dustRange.last, "the shape pass must not cover the dust slots")
    }

    @Test
    fun everyGeneratorThatDeclaresSlotGroupsReturnsOneForEachParticleItCreates() {
        val harness = SimulationHarness(SimulationRunner.SEED)
        for (effect in harness.particleEffectRegistry.effects) {
            harness.spawn(effect)
        }
        harness.advance(1.0)
        for (generator in harness.particleEffectRegistry.generators()) {
            if (generator.gpuSlotGroups.isEmpty()) continue
            assertTrue(generator.particles.isNotEmpty(), "${generator.effect.id} spawned no particles")
            for (particle in generator.particles) {
                assertNotNull(generator.gpuSlotGroup(particle), "${generator.effect.id} returned no slot group")
            }
        }
    }

    @Test
    fun groupedStylesDoNotOverlapEachOther() {
        val groupedStyles = listOf(EffectStyles.BLOB, EffectStyles.BUBBLE, EffectStyles.DRIP, EffectStyles.DUST, ParticleRenderStyle.IMAGE)
        for (firstStyle in groupedStyles) {
            val firstRange = styleRanges[firstStyle]
            assertNotNull(firstRange, "${firstStyle.id} has no slot range")
            for (secondStyle in groupedStyles) {
                if (firstStyle === secondStyle) continue
                val secondRange = styleRanges.getValue(secondStyle)
                val overlaps = firstRange.first <= secondRange.last && secondRange.first <= firstRange.last
                assertTrue(!overlaps, "${firstStyle.id} $firstRange overlaps ${secondStyle.id} $secondRange")
            }
        }
    }

    @Test
    fun everyStyleRangeStaysInsideTheBuffer() {
        for ((renderStyle, styleRange) in styleRanges) {
            assertTrue(styleRange.first >= 0 && styleRange.last < maxParticles, "${renderStyle.id} $styleRange is outside the buffer")
            assertTrue(styleRange.first <= styleRange.last, "${renderStyle.id} $styleRange is empty")
        }
    }

    @Test
    fun everyRegisteredStyleGetsExactlyOnePassInStyleShaderOrder() {
        val renderPasses = GpuRenderPass.create(styleShaders, styleRanges)
        assertEquals(styleRanges.keys, renderPasses.map { pass -> pass.renderStyle }.toSet())
        assertEquals(styleRanges.size, renderPasses.size)
        for (renderPass in renderPasses) {
            val styleRange = styleRanges.getValue(renderPass.renderStyle)
            assertEquals(styleRange.first, renderPass.firstInstance)
            assertEquals(styleRange.last - styleRange.first + 1, renderPass.instanceCount)
        }
        val dustPass = renderPasses.single { pass -> pass.renderStyle === EffectStyles.DUST }
        assertEquals(GpuBlendMode.MAX, dustPass.blendMode)
        assertTrue(renderPasses.filter { pass -> pass.renderStyle !== EffectStyles.DUST }.all { pass -> pass.blendMode == GpuBlendMode.ALPHA })
    }

    @Test
    fun aStyleWithoutParticlesGetsNoPass() {
        val onlyShape = mapOf(ParticleRenderStyle.SHAPE to 10..19)
        val renderPasses = GpuRenderPass.create(styleShaders, onlyShape)
        assertEquals(1, renderPasses.size)
        assertEquals(10, renderPasses.single().firstInstance)
        assertEquals(10, renderPasses.single().instanceCount)
        assertTrue(GpuRenderPass.create(styleShaders, emptyMap()).isEmpty())
    }

}
