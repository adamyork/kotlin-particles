package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import com.github.adamyork.particles.service.CommonParticlePhysicsSettingsService
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class GpuUniformLayoutTest {

    private companion object {
        const val WGSL_FILE_NAME: String = "particles.wgsl"
        const val SHADER_DIRECTORY: String = "src/commonMain/composeResources/files"
    }

    @Test
    fun simulationUniformFieldsMatchAcrossShaders() {
        val webGpuFields = parseWgslFields("SimulationUniforms")
        val computeFields = parseGlslFields("particles.comp.glsl", "SimulationUniforms")
        assertEquals(webGpuFields, computeFields, "WGSL and GLSL simulation uniform fields differ")
        assertEquals(GpuDataPacketWriter.SIMULATION_PACKET_FLOATS, webGpuFields.size, "simulation packet size")
    }

    @Test
    fun renderUniformFieldsMatchAcrossShaders() {
        val webGpuFields = parseWgslFields("RenderUniforms")
        val vertexFields = parseGlslFields("particles.vert.glsl", "RenderUniforms")
        val fragmentFields = parseGlslFields("particles.frag.glsl", "RenderUniforms")
        assertEquals(webGpuFields, vertexFields, "WGSL and GLSL vertex render uniform fields differ")
        assertEquals(webGpuFields, fragmentFields, "WGSL and GLSL fragment render uniform fields differ")
        assertEquals(GpuDataPacketWriter.RENDER_PACKET_FLOATS, webGpuFields.size, "render packet size")
    }

    @Test
    fun styleUniformsBeyondPacketCapacityAreRejected() {
        val oversizedSource = object : GpuStyleUniformSource {
            override val renderStyle: ParticleRenderStyle = ParticleRenderStyle.SHAPE
            override val floatCount: Int = GpuDataPacketWriter.RENDER_PACKET_FLOATS
            override fun writeUniforms(targetBuffer: FloatArray, startIndex: Int) = Unit
        }
        assertFailsWith<EngineException> {
            GpuDataPacketWriter(CommonParticlePhysicsSettingsService(), listOf(oversizedSource))
        }
    }

    private fun readShader(fileName: String): String {
        val shaderFile = File("$SHADER_DIRECTORY/$fileName")
        assertTrue(shaderFile.exists(), "shader not found at ${shaderFile.absolutePath}")
        return shaderFile.readText()
    }

    private fun parseWgslFields(structName: String): List<String> {
        val structMatch = Regex("struct\\s+$structName\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL)
            .find(readShader(WGSL_FILE_NAME))
        assertTrue(structMatch != null, "struct $structName not found in $WGSL_FILE_NAME")
        val structBody = structMatch.groupValues[1]
        val floatFields = Regex("(\\w+)\\s*:\\s*f32").findAll(structBody).map { it.groupValues[1] }.toList()
        val declaredFieldCount = Regex("\\w+\\s*:").findAll(structBody).count()
        assertEquals(declaredFieldCount, floatFields.size, "$structName in $WGSL_FILE_NAME has a non-f32 field")
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
