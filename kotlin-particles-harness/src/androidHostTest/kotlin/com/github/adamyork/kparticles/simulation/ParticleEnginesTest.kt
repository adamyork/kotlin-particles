package com.github.adamyork.kparticles.simulation

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.gui.data.HarnessViewPort
import com.github.adamyork.kparticles.platform.effects.ParticleEffects
import com.github.adamyork.kparticles.platform.effects.style.BlobStyleParameters
import com.github.adamyork.particles.engine.AbstractGenerator
import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.ParticleEngines
import com.github.adamyork.particles.engine.data.CircleDimensions
import com.github.adamyork.particles.engine.data.ParticleDirection
import com.github.adamyork.particles.engine.data.Particle
import com.github.adamyork.particles.engine.data.ParticleAppearance
import com.github.adamyork.particles.engine.data.ParticleEffectRequest
import com.github.adamyork.particles.engine.data.ParticleLifecycle
import com.github.adamyork.particles.engine.data.ParticleMaterial
import com.github.adamyork.particles.engine.data.ParticleTransform
import com.github.adamyork.particles.engine.data.ParticleVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.github.adamyork.particles.engine.shader.GpuShaderComposer
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class ParticleEnginesTest {

    private class OneParticleGenerator : AbstractGenerator(ParticleEffects.COLLISION) {

        override fun create(request: ParticleEffectRequest): MutableList<Particle> = mutableListOf(
            Particle(
                id = nextParticleId(),
                lifecycle = ParticleLifecycle(lifetime = 500.0),
                appearance = ParticleAppearance(color = Color.White),
                dimensions = CircleDimensions(radius = 4.0, maxRadius = 4.0),
                transform = ParticleTransform(position = ParticleVector(100.0, 100.0, 0.0)),
                material = ParticleMaterial(density = 1.0)
            )
        )

    }

    private val viewPort = HarnessViewPort(0, 768, 1024, 768)

    @Test
    fun theFactoryWiresEveryEngineToTheSameRegistryAndSettings() {
        val engines = ParticleEngines.createDefault(FixedEngineTiming())
        assertSame(engines.runtimeService.physicsSettingsService, engines.physicsSettingsService)
        val generator = OneParticleGenerator()
        engines.registry.register(generator)
        engines.registry.spawn(ParticleEffects.COLLISION, 100.0, 100.0, viewPort, direction = ParticleDirection.RIGHT)
        assertEquals(1, generator.particles.size)
        engines.runtimeService.deriveGravityFromViewportHeight(viewPort.height)
        engines.runtimeService.lastPaintTime = 1000.0
        engines.runtimeService.setCurrentFrameTime(1020.0)
        val schedule = engines.physicsEngine.applyPhysics(viewPort)
        assertTrue(schedule.subframeCount >= 1)
        assertTrue(generator.particles.single().lifecycle.age > 0.0, "physics must age the registered particle")
    }

    @Test
    fun renderEnginesFromTheFactoryShareTheRegistryAndSealIt() {
        val engines = ParticleEngines.createDefault(FixedEngineTiming())
        var drawCalls = 0
        val gpuRenderEngine = engines.gpuRenderEngine<Unit> { drawCalls++ }
        assertTrue(!engines.registry.isSealed)
        gpuRenderEngine.draw(viewPort, Unit)
        assertEquals(1, drawCalls)
        assertTrue(engines.registry.isSealed)
    }

    @Test
    fun packetWriterUniformSourcesAreDerivedFromTheStyleShaderList() {
        val styleShaders = TestStyleShaders.create()
        val engines = ParticleEngines.createDefault(FixedEngineTiming(), styleShaders)
        val packet = FloatArray(GpuDataPacketWriter.RENDER_PACKET_FLOATS) { Float.NaN }
        engines.gpuDataPacketWriter.writeRenderDataPacket(packet, viewPort, 1024, 768, 1f, 4.0)
        val blobIndex = styleShaders.indexOfFirst { styleShader -> styleShader.renderStyle === EffectStyles.BLOB }
        val blobOffset = GpuShaderComposer.uniformOffsets(styleShaders)[blobIndex]
        assertEquals(BlobStyleParameters.LOBE_COUNT.toFloat(), packet[GpuDataPacketWriter.LIBRARY_RENDER_FLOATS + blobOffset])
        assertTrue(packet.none { value -> value.isNaN() }, "every slot must be written")
    }

}
