package com.github.adamyork.particles.engine.shader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class GpuStyleContractTest {

    private companion object {
        const val SHADER_DIRECTORY: String = "src/commonMain/composeResources/files"
        val WGSL_TO_GLSL_TYPES: Map<String, String> = mapOf(
            "vec4<f32>" to "vec4",
            "vec2<f32>" to "vec2",
            "f32" to "float",
            "bool" to "bool"
        )
    }

    private fun readShader(fileName: String): String = File("$SHADER_DIRECTORY/$fileName").readText()

    private fun wgslStruct(structName: String): List<Pair<String, String>> {
        val body = Regex("struct\\s+$structName\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL)
            .find(readShader("particles.wgsl"))?.groupValues?.get(1)
        assertTrue(body != null, "struct $structName not found in particles.wgsl")
        return Regex("(\\w+)\\s*:\\s*([^,]+),").findAll(body).map { match ->
            val wgslType = match.groupValues[2].trim()
            match.groupValues[1] to (WGSL_TO_GLSL_TYPES[wgslType] ?: throw AssertionError("unmapped type $wgslType"))
        }.toList()
    }

    private fun glslStruct(fileName: String, structName: String): List<Pair<String, String>> {
        val body = Regex("struct\\s+$structName\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL)
            .find(readShader(fileName))?.groupValues?.get(1)
        assertTrue(body != null, "struct $structName not found in $fileName")
        return Regex("(\\w+)\\s+(\\w+)\\s*;").findAll(body).map { match ->
            match.groupValues[2] to match.groupValues[1]
        }.toList()
    }

    @Test
    fun vertexInputStructIsIdenticalInWgslAndGlslVertex() {
        assertEquals(wgslStruct("StyleVertexInput"), glslStruct("particles.vert.glsl", "StyleVertexInput"))
    }

    @Test
    fun quadStructIsIdenticalInWgslAndGlslVertex() {
        assertEquals(wgslStruct("StyleQuad"), glslStruct("particles.vert.glsl", "StyleQuad"))
    }

    @Test
    fun fragmentInputStructIsIdenticalInWgslAndGlslFragment() {
        assertEquals(wgslStruct("StyleFragmentInput"), glslStruct("particles.frag.glsl", "StyleFragmentInput"))
    }

    @Test
    fun theThreeContractStructsKeepTheirDocumentedFields() {
        assertEquals(
            listOf("positionAngle", "dimensions", "origin", "age", "scale", "corner", "isRectangle"),
            wgslStruct("StyleVertexInput").map { it.first }
        )
        assertEquals(listOf("center", "offset", "uv"), wgslStruct("StyleQuad").map { it.first })
        assertEquals(
            listOf("color", "quadCoordinate", "shapeKind", "uv", "age", "pixelRadius"),
            wgslStruct("StyleFragmentInput").map { it.first }
        )
    }

}
