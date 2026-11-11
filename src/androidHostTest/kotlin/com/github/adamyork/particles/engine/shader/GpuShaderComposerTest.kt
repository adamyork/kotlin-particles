package com.github.adamyork.particles.engine.shader

import com.github.adamyork.particles.engine.EngineException
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class GpuShaderComposerTest {

    private companion object {
        const val SHADER_DIRECTORY: String = "src/commonMain/composeResources/files"
    }

    private val fixtureStyleShaders = listOf(BuiltInStyleShaders.SHAPE)

    @Test
    fun invalidStyleSetsAreRejected() {
        val wgsl = readShader("particles.wgsl")
        assertFailsWith<EngineException> {
            GpuShaderComposer.composeWgsl(wgsl, fixtureStyleShaders + BuiltInStyleShaders.SHAPE)
        }
        val sameCodeDifferentId = GpuStyleShader(ParticleRenderStyle("OTHER", 0f), "fn STYLE_fragment() {}", "", "")
        assertFailsWith<EngineException> { GpuShaderComposer.composeWgsl(wgsl, fixtureStyleShaders + sameCodeDifferentId) }
        val badIdentifier = GpuStyleShader(ParticleRenderStyle("bad id", 40f), "fn STYLE_fragment() {}", "", "")
        assertFailsWith<EngineException> { GpuShaderComposer.composeWgsl(wgsl, listOf(badIdentifier)) }
        val readsUndeclaredUniform = GpuStyleShader(ParticleRenderStyle("GREEDY", 41f), "fn STYLE_fragment() { STYLE_UNIFORM(0); }", "", "")
        assertFailsWith<EngineException> { GpuShaderComposer.composeWgsl(wgsl, listOf(readsUndeclaredUniform)) }
        assertFailsWith<EngineException> { GpuShaderComposer.composeWgsl("no markers here", fixtureStyleShaders) }
    }

    @Test
    fun snippetsMustPrefixEveryTopLevelName() {
        val leakyWgsl = GpuStyleShader(
            ParticleRenderStyle("LEAKY", 42f),
            "fn helper() -> f32 { return 1.0; }\nfn STYLE_fragment() {}",
            "",
            ""
        )
        assertFailsWith<EngineException> { GpuShaderComposer.composeWgsl(readShader("particles.wgsl"), listOf(leakyWgsl)) }
        val leakyGlsl = GpuStyleShader(
            ParticleRenderStyle("LEAKY", 42f),
            "fn STYLE_fragment() {}",
            "",
            "float helper() { return 1.0; }\nvec4 STYLE_fragment(StyleFragmentInput fragmentInput) { return vec4(0.0); }"
        )
        assertFailsWith<EngineException> { GpuShaderComposer.composeGlslFragment(readShader("particles.frag.glsl"), listOf(leakyGlsl)) }
    }

    @Test
    fun theAuthoringGuideExampleStyleComposes() {
        val glowShader = GpuStyleShader(
            renderStyle = ParticleRenderStyle("GLOW", 6f),
            wgslSource = """
                fn STYLE_fragment(fragmentInput: StyleFragmentInput) -> vec4<f32> {
                  let distanceFromCenter = length(fragmentInput.quadCoordinate);
                  if (distanceFromCenter > 1.0) {
                    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
                  }
                  let fade = 1.0 - distanceFromCenter;
                  return vec4<f32>(fragmentInput.color.rgb, fragmentInput.color.a * fade * fade);
                }
            """.trimIndent(),
            glslVertexSource = "",
            glslFragmentSource = """
                vec4 STYLE_fragment(StyleFragmentInput fragmentInput) {
                    float distanceFromCenter = length(fragmentInput.quadCoordinate);
                    if (distanceFromCenter > 1.0) {
                        return vec4(0.0, 0.0, 0.0, 0.0);
                    }
                    float fade = 1.0 - distanceFromCenter;
                    return vec4(fragmentInput.color.rgb, fragmentInput.color.a * fade * fade);
                }
            """.trimIndent()
        )
        val withGlow = fixtureStyleShaders + glowShader
        val wgsl = GpuShaderComposer.composeWgsl(readShader("particles.wgsl"), withGlow)
        assertTrue(wgsl.contains("fn style_glow_fragment("))
        assertTrue(wgsl.contains("activeStyleCode == 6.0"))
        val glslFragment = GpuShaderComposer.composeGlslFragment(readShader("particles.frag.glsl"), withGlow)
        assertTrue(glslFragment.contains("if (uActiveStyle == 6) { fragmentColor = style_glow_fragment(fragmentInput); return; }"))
    }

    private fun readShader(fileName: String): String = File("$SHADER_DIRECTORY/$fileName").readText()

}
