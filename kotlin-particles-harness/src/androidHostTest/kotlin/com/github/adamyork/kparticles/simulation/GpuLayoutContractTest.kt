package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.effects.style.BlobStyleParameters
import com.github.adamyork.kparticles.platform.effects.style.DripStyleSettings
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.kparticles.platform.gui.data.HarnessViewPort
import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.GpuStyleUniformSource
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import com.github.adamyork.particles.engine.shader.GpuShaderComposer
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.service.CommonParticlePhysicsSettingsService
import java.io.File
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class GpuLayoutContractTest {

    private companion object {
        const val WGSL_FILE_NAME: String = "particles.wgsl"
        const val SHADER_DIRECTORY: String = "../src/commonMain/composeResources/files"
        const val SETTING_BASE_VALUE: Double = 100.0
        const val SETTING_VALUE_STEP: Double = 0.5
        const val DRIP_SEED_START: Int = 40
        const val DELTA_TIME: Double = 0.625
        const val WORLD_TIME: Double = 1234.5
        const val SPRITE_TICKS_PER_FRAME: Double = 4.25
        const val MAXIMUM_PARTICLES: Int = 4096
        const val SUBFRAME_INDEX: Int = 5
        const val CANVAS_WIDTH: Int = 1280
        const val CANVAS_HEIGHT: Int = 720
        const val RENDER_SCALE: Float = 1.5f
        const val GRID_CELL_SIZE: Double = 48.0
        const val GRAVITY_SOURCE_COUNT: Int = 9
        const val SHATTER_MINIMUM_FRAGMENT_COUNT: Int = 3
        const val SHATTER_MAXIMUM_FRAGMENT_COUNT: Int = 31
    }

    @Test
    fun simulationPacketPlacesEverySettingInItsNamedSlot() {
        val settings = configuredSettings()
        val viewPort = HarnessViewPort(13, 29, 1031, 517)
        val packet = FloatArray(GpuDataPacketWriter.SIMULATION_PACKET_FLOATS) { Float.NaN }
        GpuDataPacketWriter(settings, styleUniformSources(DripStyleSettings())).writeSimulationDataPacket(
            packet, DELTA_TIME, WORLD_TIME, SPRITE_TICKS_PER_FRAME, MAXIMUM_PARTICLES, viewPort, SUBFRAME_INDEX
        )
        val cellSize = max(1.0, settings.spatialGridCellSize)
        val expectedValues = linkedMapOf(
            "deltaTime" to DELTA_TIME.toFloat(),
            "worldTime" to WORLD_TIME.toFloat(),
            "gravity" to settings.gravity.toFloat(),
            "mediumDensity" to settings.mediumDensity.toFloat(),
            "boundaryZ" to settings.boundaryZ.toFloat(),
            "boundaryDepth" to settings.boundaryDepth.toFloat(),
            "wallRestitution" to settings.wallRestitution.toFloat(),
            "environmentFriction" to settings.friction.toFloat(),
            "drag" to settings.drag.toFloat(),
            "dragCoefficient" to settings.dragCoefficient.toFloat(),
            "minActiveVelocity" to settings.minActiveVelocity.toFloat(),
            "spriteTicksPerFrame" to SPRITE_TICKS_PER_FRAME.toFloat(),
            "flowAmplitude" to settings.flowAmplitude.toFloat(),
            "flowFrequency" to settings.flowFrequency.toFloat(),
            "flowWavelength" to settings.flowWavelength.toFloat(),
            "maxParticles" to MAXIMUM_PARTICLES.toFloat(),
            "viewportX" to viewPort.x.toFloat(),
            "viewportY" to viewPort.y.toFloat(),
            "viewportWidth" to viewPort.width.toFloat(),
            "viewportHeight" to viewPort.height.toFloat(),
            "collisionPlaneThickness" to settings.collisionPlaneThickness.toFloat(),
            "gravityPlaneThickness" to settings.gravityPlaneThickness.toFloat(),
            "cellSize" to cellSize.toFloat(),
            "gridColumns" to max(1, (viewPort.width / cellSize).toInt() + 1).toFloat(),
            "gridRows" to max(1, (viewPort.height / cellSize).toInt() + 1).toFloat(),
            "gravitationalConstant" to settings.gravitationalConstant.toFloat(),
            "gravitationalSoftening" to settings.gravitationalSoftening.toFloat(),
            "gravitySourceCount" to settings.gravitySourceCount.toFloat(),
            "gravityMinimumAcceleration" to settings.gravityMinimumAcceleration.toFloat(),
            "accretionMassRatio" to settings.accretionMassRatio.toFloat(),
            "shatterMinimumFragmentRadius" to settings.shatterMinimumFragmentRadius.toFloat(),
            "shatterMinimumFragmentCount" to settings.shatterMinimumFragmentCount.toFloat(),
            "shatterMaximumFragmentCount" to settings.shatterMaximumFragmentCount.toFloat(),
            "shatterToughness" to settings.shatterToughness.toFloat(),
            "positionCorrectionSlop" to settings.positionCorrectionSlop.toFloat(),
            "reservedShatterB" to 0f,
            "positionCorrectionFactor" to settings.positionCorrectionFactor.toFloat(),
            "contactVelocityRelaxationExponent" to GpuDataPacketWriter.CONTACT_VELOCITY_RELAXATION_EXPONENT,
            "subframeIndex" to SUBFRAME_INDEX.toFloat(),
            "windVelocity" to settings.windVelocity.toFloat()
        )
        assertPacketMatchesShader(parseWgslFields("SimulationUniforms"), expectedValues, packet)
    }

    @Test
    fun renderPacketPlacesEverySettingInItsNamedSlot() {
        val settings = configuredSettings()
        val dripSettings = configuredDripSettings()
        val viewPort = HarnessViewPort(13, 29, 1031, 517)
        val packet = FloatArray(GpuDataPacketWriter.RENDER_PACKET_FLOATS) { Float.NaN }
        GpuDataPacketWriter(settings, styleUniformSources(dripSettings)).writeRenderDataPacket(
            packet,
            viewPort,
            CANVAS_WIDTH,
            CANVAS_HEIGHT,
            RENDER_SCALE,
            SPRITE_TICKS_PER_FRAME
        )
        val expectedValues = linkedMapOf(
            "viewportX" to viewPort.x.toFloat(),
            "viewportY" to viewPort.y.toFloat(),
            "canvasWidth" to CANVAS_WIDTH.toFloat(),
            "canvasHeight" to CANVAS_HEIGHT.toFloat(),
            "renderScale" to RENDER_SCALE,
            "spriteTicksPerFrame" to SPRITE_TICKS_PER_FRAME.toFloat(),
            *styleUniformExpectations(dripSettings).toList().toTypedArray()
        )
        assertPacketMatchesShader(parseWgslFields("RenderUniforms"), expectedValues, packet)
    }

    private fun styleUniformSources(dripSettings: DripStyleSettings): List<GpuStyleUniformSource> =
        styleShaders(dripSettings).mapNotNull { styleShader -> styleShader.uniformSource }

    private fun styleShaders(dripSettings: DripStyleSettings): List<GpuStyleShader> =
        TestStyleShaders.create(dripStyleSettings = dripSettings)

    private fun styleUniformExpectations(dripSettings: DripStyleSettings): Map<String, Float> {
        val shaders = styleShaders(dripSettings)
        val offsets = GpuShaderComposer.uniformOffsets(shaders)
        val expectations = LinkedHashMap<String, Float>()
        for (slot in 0 until GpuShaderComposer.STYLE_UNIFORM_CAPACITY) expectations["styleUniform$slot"] = 0f
        for ((shaderIndex, shader) in shaders.withIndex()) {
            val source = shader.uniformSource ?: continue
            val expectedFloats = when (shader.renderStyle) {
                ParticleRenderStyle.IMAGE -> floatArrayOf(
                    TestStyleShaders.FRAME_WIDTH.toFloat(),
                    TestStyleShaders.FRAME_HEIGHT.toFloat(),
                    TestStyleShaders.FRAME_COUNT.toFloat()
                )

                EffectStyles.BLOB -> floatArrayOf(
                    BlobStyleParameters.LOBE_COUNT.toFloat(),
                    BlobStyleParameters.AMPLITUDE_RATIO,
                    BlobStyleParameters.BASE_FREQUENCY,
                    BlobStyleParameters.FREQUENCY_STEP,
                    BlobStyleParameters.PHASE_STEP
                )

                EffectStyles.DRIP -> floatArrayOf(
                    dripSettings.dripTrailRadiusRatio.toFloat(),
                    dripSettings.dripNeckLengthRatio.toFloat(),
                    dripSettings.dripGlossIntensity.toFloat(),
                    dripSettings.dripGlossStripeOffsetRatio.toFloat(),
                    dripSettings.dripGlossStripeWidthRatio.toFloat(),
                    dripSettings.dripGlossSpecularRadiusRatio.toFloat(),
                    dripSettings.dripGlossSpecularOffsetRatio.toFloat()
                )

                else -> throw AssertionError("no expectations for ${shader.renderStyle.id}")
            }
            assertEquals(source.floatCount, expectedFloats.size)
            for ((floatIndex, value) in expectedFloats.withIndex()) {
                expectations["styleUniform${offsets[shaderIndex] + floatIndex}"] = value
            }
        }
        return expectations
    }

    private fun assertPacketMatchesShader(
        shaderFields: List<String>,
        expectedValues: Map<String, Float>,
        packet: FloatArray
    ) {
        assertEquals(shaderFields.toSet(), expectedValues.keys, "test expectations and shader fields differ")
        val nonZeroValues = expectedValues.values.filter { it != 0f }
        assertEquals(
            nonZeroValues.size,
            nonZeroValues.toSet().size,
            "test values must be distinct to detect slot mixups"
        )
        for ((slotIndex, fieldName) in shaderFields.withIndex()) {
            assertFalse(packet[slotIndex].isNaN(), "slot $slotIndex ($fieldName) was never written")
            assertEquals(expectedValues.getValue(fieldName), packet[slotIndex], "slot $slotIndex ($fieldName)")
        }
    }

    private fun configuredSettings(): CommonParticlePhysicsSettingsService {
        val settings = CommonParticlePhysicsSettingsService()
        var seed = 0
        settings.gravity = distinctValue(seed++)
        settings.mediumDensity = distinctValue(seed++)
        settings.boundaryZ = distinctValue(seed++)
        settings.boundaryDepth = distinctValue(seed++)
        settings.wallRestitution = distinctValue(seed++)
        settings.friction = distinctValue(seed++)
        settings.drag = distinctValue(seed++)
        settings.dragCoefficient = distinctValue(seed++)
        settings.minActiveVelocity = distinctValue(seed++)
        settings.flowAmplitude = distinctValue(seed++)
        settings.flowFrequency = distinctValue(seed++)
        settings.flowWavelength = distinctValue(seed++)
        settings.collisionPlaneThickness = distinctValue(seed++)
        settings.gravityPlaneThickness = distinctValue(seed++)
        settings.spatialGridCellSize = GRID_CELL_SIZE
        settings.gravitationalConstant = distinctValue(seed++)
        settings.gravitationalSoftening = distinctValue(seed++)
        settings.gravitySourceCount = GRAVITY_SOURCE_COUNT
        settings.gravityMinimumAcceleration = distinctValue(seed++)
        settings.accretionMassRatio = distinctValue(seed++)
        settings.shatterMinimumFragmentRadius = distinctValue(seed++)
        settings.shatterMinimumFragmentCount = SHATTER_MINIMUM_FRAGMENT_COUNT
        settings.shatterMaximumFragmentCount = SHATTER_MAXIMUM_FRAGMENT_COUNT
        settings.shatterToughness = distinctValue(seed++)
        settings.positionCorrectionSlop = distinctValue(seed++)
        settings.positionCorrectionFactor = distinctValue(seed++)
        settings.windVelocity = distinctValue(seed)
        return settings
    }

    private fun configuredDripSettings(): DripStyleSettings {
        val dripSettings = DripStyleSettings()
        var seed = DRIP_SEED_START
        dripSettings.dripTrailRadiusRatio = distinctValue(seed++)
        dripSettings.dripNeckLengthRatio = distinctValue(seed++)
        dripSettings.dripGlossIntensity = distinctValue(seed++)
        dripSettings.dripGlossStripeOffsetRatio = distinctValue(seed++)
        dripSettings.dripGlossStripeWidthRatio = distinctValue(seed++)
        dripSettings.dripGlossSpecularRadiusRatio = distinctValue(seed++)
        dripSettings.dripGlossSpecularOffsetRatio = distinctValue(seed)
        return dripSettings
    }

    private fun distinctValue(seed: Int): Double = SETTING_BASE_VALUE + seed * SETTING_VALUE_STEP

    private fun readWgslShader(): String {
        val shaderFile = File("$SHADER_DIRECTORY/$WGSL_FILE_NAME")
        assertTrue(shaderFile.exists(), "shader not found at ${shaderFile.absolutePath}")
        return shaderFile.readText()
    }

    private fun parseWgslFields(structName: String): List<String> {
        val structMatch = Regex("struct\\s+$structName\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL)
            .find(readWgslShader())
        assertTrue(structMatch != null, "struct $structName not found in $WGSL_FILE_NAME")
        val structBody = structMatch.groupValues[1]
        val floatFields = Regex("(\\w+)\\s*:\\s*f32").findAll(structBody).map { it.groupValues[1] }.toList()
        val declaredFieldCount = Regex("\\w+\\s*:").findAll(structBody).count()
        assertEquals(declaredFieldCount, floatFields.size, "$structName in $WGSL_FILE_NAME has a non-f32 field")
        return floatFields
    }

}
