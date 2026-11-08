package com.github.adamyork.particles.engine.shader

import com.github.adamyork.particles.engine.data.ParticleImageSource
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public object BuiltInStyleShaders {

    private val shapeWgsl = """
fn STYLE_fragment(fragmentInput: StyleFragmentInput) -> vec4<f32> {
  if (fragmentInput.shapeKind < 0.5 && dot(fragmentInput.quadCoordinate, fragmentInput.quadCoordinate) > 1.0) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return fragmentInput.color;
}
""".trimIndent()

    private val shapeGlslFragment = """
vec4 STYLE_fragment(StyleFragmentInput fragmentInput) {
    if (fragmentInput.shapeKind < 0.5 && dot(fragmentInput.quadCoordinate, fragmentInput.quadCoordinate) > 1.0) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    return fragmentInput.color;
}
""".trimIndent()

    private val imageWgsl = """
fn STYLE_halfExtent(vertexInput: StyleVertexInput, halfExtent: vec2<f32>) -> vec2<f32> {
  return vec2<f32>(STYLE_UNIFORM(0) * 0.5, STYLE_UNIFORM(1) * 0.5);
}

fn STYLE_quad(vertexInput: StyleVertexInput, halfExtent: vec2<f32>, quad: StyleQuad) -> StyleQuad {
  var result = quad;
  result.offset = vec2<f32>(vertexInput.corner.x * halfExtent.x, vertexInput.corner.y * halfExtent.y);
  let frameIndex = floor(vertexInput.age / renderUniforms.spriteTicksPerFrame) % max(vertexInput.origin.w, 1.0);
  result.uv = vec2<f32>((quad.uv.x + frameIndex) / max(STYLE_UNIFORM(2), 1.0), quad.uv.y);
  return result;
}

fn STYLE_fragment(fragmentInput: StyleFragmentInput) -> vec4<f32> {
  return textureSampleLevel(renderTexture, renderSampler, fragmentInput.uv, 0.0);
}
""".trimIndent()

    private val imageGlslVertex = """
vec2 STYLE_halfExtent(StyleVertexInput vertexInput, vec2 halfExtent) {
    return vec2(STYLE_UNIFORM(0) * 0.5, STYLE_UNIFORM(1) * 0.5);
}

StyleQuad STYLE_quad(StyleVertexInput vertexInput, vec2 halfExtent, StyleQuad quad) {
    StyleQuad result = quad;
    result.offset = vec2(vertexInput.corner.x * halfExtent.x, vertexInput.corner.y * halfExtent.y);
    float frameIndex = mod(floor(vertexInput.age / renderUniforms.spriteTicksPerFrame), max(vertexInput.origin.w, 1.0));
    result.uv = vec2((quad.uv.x + frameIndex) / max(STYLE_UNIFORM(2), 1.0), quad.uv.y);
    return result;
}
""".trimIndent()

    private val imageGlslFragment = """
vec4 STYLE_fragment(StyleFragmentInput fragmentInput) {
    return textureLod(renderTexture, fragmentInput.uv, 0.0);
}
""".trimIndent()

    public val SHAPE: GpuStyleShader = GpuStyleShader(
        renderStyle = ParticleRenderStyle.SHAPE,
        wgslSource = shapeWgsl,
        glslVertexSource = "",
        glslFragmentSource = shapeGlslFragment
    )

    public val DEFAULTS: List<GpuStyleShader> = listOf(SHAPE)

    public fun image(imageSource: ParticleImageSource, renderStyle: ParticleRenderStyle = ParticleRenderStyle.IMAGE): GpuStyleShader =
        GpuStyleShader(
            renderStyle = renderStyle,
            wgslSource = imageWgsl,
            glslVertexSource = imageGlslVertex,
            glslFragmentSource = imageGlslFragment,
            uniformSource = ImageStyleUniformSource(renderStyle, imageSource),
            imageSource = imageSource
        )

}
