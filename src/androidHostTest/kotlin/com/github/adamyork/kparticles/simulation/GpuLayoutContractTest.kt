package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.GpuDataPacketWriter
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.service.CommonPhysicsSettingsService
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
        const val SHADER_DIRECTORY: String = "src/commonMain/composeResources/files"
        const val SETTING_BASE_VALUE: Double = 100.0
        const val SETTING_VALUE_STEP: Double = 0.5
        const val DELTA_TIME: Double = 0.625
        const val WORLD_TIME: Double = 1234.5
        const val SPRITE_TICKS_PER_FRAME: Double = 4.25
        const val MAXIMUM_PARTICLES: Int = 4096
        const val SUBFRAME_INDEX: Int = 5
        const val CANVAS_WIDTH: Int = 1280
        const val CANVAS_HEIGHT: Int = 720
        const val RENDER_SCALE: Float = 1.5f
        const val SPRITE_FRAME_WIDTH: Int = 48
        const val SPRITE_FRAME_HEIGHT: Int = 64
        const val SPRITE_SHEET_FRAME_COUNT: Int = 12
        const val GRID_CELL_SIZE: Double = 48.0
        const val GRAVITY_SOURCE_COUNT: Int = 9
        const val SHATTER_MINIMUM_FRAGMENT_COUNT: Int = 3
        const val SHATTER_MAXIMUM_FRAGMENT_COUNT: Int = 31
    }

    @Test
    fun simulationUniformFieldsMatchAcrossShaders() {
        val webGpuFields = parseWgslFields("particles.wgsl", "SimulationUniforms")
        val computeFields = parseGlslFields("particles.comp.glsl", "SimulationUniforms")
        assertEquals(webGpuFields, computeFields, "WGSL and GLSL simulation uniform fields differ")
        assertEquals(GpuDataPacketWriter.SIMULATION_PACKET_FLOATS, webGpuFields.size, "simulation packet size")
    }

    @Test
    fun renderUniformFieldsMatchAcrossShaders() {
        val webGpuFields = parseWgslFields("particles.wgsl", "RenderUniforms")
        val vertexFields = parseGlslFields("particles.vert.glsl", "RenderUniforms")
        val fragmentFields = parseGlslFields("particles.frag.glsl", "RenderUniforms")
        assertEquals(webGpuFields, vertexFields, "WGSL and GLSL vertex render uniform fields differ")
        assertEquals(webGpuFields, fragmentFields, "WGSL and GLSL fragment render uniform fields differ")
        assertEquals(GpuDataPacketWriter.RENDER_PACKET_FLOATS, webGpuFields.size, "render packet size")
    }

    @Test
    fun simulationPacketPlacesEverySettingInItsNamedSlot() {
        val settings = configuredSettings()
        val viewPort = ViewPort(13, 29, 1031, 517)
        val packet = FloatArray(GpuDataPacketWriter.SIMULATION_PACKET_FLOATS) { Float.NaN }
        GpuDataPacketWriter(settings).writeSimulationDataPacket(
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
        assertPacketMatchesShader(parseWgslFields("particles.wgsl", "SimulationUniforms"), expectedValues, packet)
    }

    @Test
    fun renderPacketPlacesEverySettingInItsNamedSlot() {
        val settings = configuredSettings()
        val viewPort = ViewPort(13, 29, 1031, 517)
        val packet = FloatArray(GpuDataPacketWriter.RENDER_PACKET_FLOATS) { Float.NaN }
        GpuDataPacketWriter(settings).writeRenderDataPacket(
            packet,
            viewPort,
            CANVAS_WIDTH,
            CANVAS_HEIGHT,
            RENDER_SCALE,
            SPRITE_FRAME_WIDTH,
            SPRITE_FRAME_HEIGHT,
            SPRITE_SHEET_FRAME_COUNT,
            SPRITE_TICKS_PER_FRAME
        )
        val expectedValues = linkedMapOf(
            "viewportX" to viewPort.x.toFloat(),
            "viewportY" to viewPort.y.toFloat(),
            "canvasWidth" to CANVAS_WIDTH.toFloat(),
            "canvasHeight" to CANVAS_HEIGHT.toFloat(),
            "renderScale" to RENDER_SCALE,
            "spriteFrameWidth" to SPRITE_FRAME_WIDTH.toFloat(),
            "spriteFrameHeight" to SPRITE_FRAME_HEIGHT.toFloat(),
            "spriteSheetFrameCount" to SPRITE_SHEET_FRAME_COUNT.toFloat(),
            "spriteTicksPerFrame" to SPRITE_TICKS_PER_FRAME.toFloat(),
            "dripTrailRadiusRatio" to settings.dripTrailRadiusRatio.toFloat(),
            "dripNeckLengthRatio" to settings.dripNeckLengthRatio.toFloat(),
            "dripGlossIntensity" to settings.dripGlossIntensity.toFloat(),
            "dripGlossStripeOffsetRatio" to settings.dripGlossStripeOffsetRatio.toFloat(),
            "dripGlossStripeWidthRatio" to settings.dripGlossStripeWidthRatio.toFloat(),
            "dripGlossSpecularRadiusRatio" to settings.dripGlossSpecularRadiusRatio.toFloat(),
            "dripGlossSpecularOffsetRatio" to settings.dripGlossSpecularOffsetRatio.toFloat(),
            "blobLobeCount" to BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT.toFloat(),
            "blobAmplitudeRatio" to BlobProjectileGenerator.PROJECTILE_BLOB_AMPLITUDE_RATIO,
            "blobBaseFrequency" to BlobProjectileGenerator.PROJECTILE_BLOB_BASE_FREQUENCY,
            "blobFrequencyStep" to BlobProjectileGenerator.PROJECTILE_BLOB_FREQUENCY_STEP,
            "blobPhaseStep" to BlobProjectileGenerator.PROJECTILE_BLOB_PHASE_STEP,
            "reservedA" to 0f,
            "reservedB" to 0f,
            "reservedC" to 0f
        )
        assertPacketMatchesShader(parseWgslFields("particles.wgsl", "RenderUniforms"), expectedValues, packet)
    }

    private fun assertPacketMatchesShader(
        shaderFields: List<String>,
        expectedValues: Map<String, Float>,
        packet: FloatArray
    ) {
        assertEquals(shaderFields.toSet(), expectedValues.keys, "test expectations and shader fields differ")
        val nonZeroValues = expectedValues.values.filter { it != 0f }
        assertEquals(nonZeroValues.size, nonZeroValues.toSet().size, "test values must be distinct to detect slot mixups")
        for ((slotIndex, fieldName) in shaderFields.withIndex()) {
            assertFalse(packet[slotIndex].isNaN(), "slot $slotIndex ($fieldName) was never written")
            assertEquals(expectedValues.getValue(fieldName), packet[slotIndex], "slot $slotIndex ($fieldName)")
        }
    }

    private fun configuredSettings(): CommonPhysicsSettingsService {
        val settings = CommonPhysicsSettingsService()
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
        settings.windVelocity = distinctValue(seed++)
        settings.dripTrailRadiusRatio = distinctValue(seed++)
        settings.dripNeckLengthRatio = distinctValue(seed++)
        settings.dripGlossIntensity = distinctValue(seed++)
        settings.dripGlossStripeOffsetRatio = distinctValue(seed++)
        settings.dripGlossStripeWidthRatio = distinctValue(seed++)
        settings.dripGlossSpecularRadiusRatio = distinctValue(seed++)
        settings.dripGlossSpecularOffsetRatio = distinctValue(seed++)
        return settings
    }

    private fun distinctValue(seed: Int): Double = SETTING_BASE_VALUE + seed * SETTING_VALUE_STEP

    private fun readShader(fileName: String): String {
        val shaderFile = File("$SHADER_DIRECTORY/$fileName")
        assertTrue(shaderFile.exists(), "shader not found at ${shaderFile.absolutePath}")
        return shaderFile.readText()
    }

    private fun parseWgslFields(fileName: String, structName: String): List<String> {
        val structMatch = Regex("struct\\s+$structName\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL)
            .find(readShader(fileName))
        assertTrue(structMatch != null, "struct $structName not found in $fileName")
        val structBody = structMatch.groupValues[1]
        val floatFields = Regex("(\\w+)\\s*:\\s*f32").findAll(structBody).map { it.groupValues[1] }.toList()
        val declaredFieldCount = Regex("\\w+\\s*:").findAll(structBody).count()
        assertEquals(declaredFieldCount, floatFields.size, "$structName in $fileName has a non-f32 field")
        return floatFields
    }

    private fun parseGlslFields(fileName: String, blockName: String): List<String> {
        val blockMatch = Regex("uniform\\s+$blockName\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
            .find(readShader(fileName))
        assertTrue(blockMatch != null, "uniform block $blockName not found in $fileName")
        val blockBody = blockMatch.groupValues[1]
        val floatFields = Regex("float\\s+(\\w+)\\s*;").findAll(blockBody).map { it.groupValues[1] }.toList()
        val declaredFieldCount = Regex(";").findAll(blockBody).count()
        assertEquals(declaredFieldCount, floatFields.size, "$blockName in $fileName has a non-float field")
        return floatFields
    }

}
