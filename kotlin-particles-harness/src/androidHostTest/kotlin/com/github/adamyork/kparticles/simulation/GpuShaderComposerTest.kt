package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.shader.GpuShaderComposer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class GpuShaderComposerTest {

    private companion object {
        const val SHADER_DIRECTORY: String = "../src/commonMain/composeResources/files"
        const val OUTPUT_DIRECTORY: String = "build/composed-shaders"
    }

    private val styleShaders = TestStyleShaders.create()

    private fun readShader(fileName: String): String = File("$SHADER_DIRECTORY/$fileName").readText()

    @Test
    fun composedWgslHasNoMarkersOrStyleTokensLeft() {
        val composed = GpuShaderComposer.composeWgsl(readShader("particles.wgsl"), styleShaders)
        File(OUTPUT_DIRECTORY).mkdirs()
        File("$OUTPUT_DIRECTORY/particles.composed.wgsl").writeText(composed)
        assertFalse(composed.contains("{{"), "unreplaced marker")
        assertFalse(Regex("\\bSTYLE_").containsMatchIn(composed), "unreplaced STYLE_ token")
        for (styleShader in styleShaders) {
            val prefix = "style_" + styleShader.renderStyle.id.lowercase() + "_"
            assertTrue(composed.contains("fn ${prefix}fragment("), "${styleShader.renderStyle.id} fragment is missing")
            assertTrue(
                composed.contains("activeStyleCode == ${styleShader.renderStyle.code} ) { return ${prefix}fragment(fragmentInput); }".replace(" )", ")")),
                "${styleShader.renderStyle.id} is not dispatched"
            )
        }
        assertTrue(composed.contains("halfExtent = style_image_halfExtent(vertexInput, halfExtent);"))
        assertTrue(composed.contains("halfExtent = style_blob_halfExtent(vertexInput, halfExtent);"))
        assertTrue(composed.contains("quad = style_drip_quad(vertexInput, halfExtent, quad);"))
        assertFalse(composed.contains("style_dust_quad("), "dust has no quad hook")
    }

    @Test
    fun composedGlslStagesHaveNoMarkersOrStyleTokensLeft() {
        val vertex = GpuShaderComposer.composeGlslVertex(readShader("particles.vert.glsl"), styleShaders)
        val fragment = GpuShaderComposer.composeGlslFragment(readShader("particles.frag.glsl"), styleShaders)
        File(OUTPUT_DIRECTORY).mkdirs()
        File("$OUTPUT_DIRECTORY/particles.composed.vert.glsl").writeText(vertex)
        File("$OUTPUT_DIRECTORY/particles.composed.frag.glsl").writeText(fragment)
        for (stage in listOf(vertex, fragment)) {
            assertFalse(stage.contains("{{"), "unreplaced marker")
            assertFalse(Regex("\\bSTYLE_").containsMatchIn(stage), "unreplaced STYLE_ token")
        }
        assertTrue(vertex.contains("quad = style_drip_quad(vertexInput, halfExtent, quad);"))
        assertTrue(fragment.contains("if (uActiveStyle == 5) { fragmentColor = style_drip_fragment(fragmentInput); return; }"))
        assertFalse(vertex.contains("style_drip_fragment"), "fragment code must not leak into the vertex stage")
    }

    @Test
    fun styleUniformsAreAssignedSequentiallyInStyleShaderOrder() {
        val offsets = GpuShaderComposer.uniformOffsets(styleShaders)
        val offsetByStyle = styleShaders.indices.associate { index -> styleShaders[index].renderStyle to offsets[index] }
        assertEquals(3, offsetByStyle.getValue(EffectStyles.BLOB))
        assertEquals(8, offsetByStyle.getValue(EffectStyles.DRIP))
        val composed = GpuShaderComposer.composeWgsl(readShader("particles.wgsl"), styleShaders)
        assertTrue(composed.contains("(1.0 + renderUniforms.styleUniform4)"), "blob amplitude must read its own slot")
        assertTrue(composed.contains("beadRadius * renderUniforms.styleUniform8"), "drip trail ratio must read slot 8")
    }

    @Test
    fun compileErrorsAreLocatedInTheStyleThatCausedThem() {
        val composed = GpuShaderComposer.composeWgsl(readShader("particles.wgsl"), styleShaders)
        val lines = composed.lines()
        val dripFragmentLine = lines.indexOfFirst { line -> line.startsWith("fn style_drip_fragment(") } + 1
        val vertexMainLine = lines.indexOfFirst { line -> line.startsWith("fn vertexMain(") } + 1
        assertTrue(dripFragmentLine > 1 && vertexMainLine > 1)
        assertEquals("DRIP", GpuShaderComposer.locateStyle(composed, dripFragmentLine + 5, styleShaders))
        assertEquals(null, GpuShaderComposer.locateStyle(composed, vertexMainLine + 2, styleShaders))
        val wgslReport = "[error] line ${dripFragmentLine + 5}:3 boom\n[warning] line ${vertexMainLine + 2}:1 other"
        val annotated = GpuShaderComposer.annotateCompilationReport(composed, styleShaders, wgslReport).lines()
        assertEquals("[error] line ${dripFragmentLine + 5}:3 boom [render style DRIP]", annotated[0])
        assertEquals("[warning] line ${vertexMainLine + 2}:1 other", annotated[1])
        val glslComposed = GpuShaderComposer.composeGlslFragment(readShader("particles.frag.glsl"), styleShaders)
        val blobLine = glslComposed.lines().indexOfFirst { line -> line.startsWith("vec4 style_blob_fragment(") } + 1
        val glslReport = "ERROR: 0:${blobLine + 2}: 'x' : undeclared identifier"
        assertTrue(GpuShaderComposer.annotateCompilationReport(glslComposed, styleShaders, glslReport).endsWith("[render style BLOB]"))
    }

}
