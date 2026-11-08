package com.github.adamyork.kparticles.platform.effects.style

import com.github.adamyork.particles.engine.shader.GpuBlendMode
import com.github.adamyork.particles.engine.shader.GpuStyleShader

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
object EffectStyleShaders {

    private val dustWgsl = """
fn STYLE_fragment(fragmentInput: StyleFragmentInput) -> vec4<f32> {
  if (fragmentInput.shapeKind < 0.5 && dot(fragmentInput.quadCoordinate, fragmentInput.quadCoordinate) > 1.0) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return vec4<f32>(fragmentInput.color.rgb * fragmentInput.color.a, fragmentInput.color.a);
}
""".trimIndent()

    private val dustGlslFragment = """
vec4 STYLE_fragment(StyleFragmentInput fragmentInput) {
    if (fragmentInput.shapeKind < 0.5 && dot(fragmentInput.quadCoordinate, fragmentInput.quadCoordinate) > 1.0) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    return vec4(fragmentInput.color.rgb * fragmentInput.color.a, fragmentInput.color.a);
}
""".trimIndent()

    private val bubbleWgsl = """
const STYLE_FILL_TOP_LEFT_ALPHA = 0.4;
const STYLE_FILL_BOTTOM_RIGHT_ALPHA = 0.051;
const STYLE_RIM_ALPHA = 0.8;
const STYLE_RIM_STROKE_WIDTH_PIXELS = 2.0;
const STYLE_HIGHLIGHT_ALPHA = 0.9;
const STYLE_HIGHLIGHT_RADIUS_RATIO = 0.28;
const STYLE_HIGHLIGHT_OFFSET_RATIO = 0.38;

fn STYLE_fragment(fragmentInput: StyleFragmentInput) -> vec4<f32> {
  let quadCoordinate = fragmentInput.quadCoordinate;
  let distanceFromCenter = length(quadCoordinate);
  if (distanceFromCenter > 1.0) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  let diagonal = (quadCoordinate.x + quadCoordinate.y) * 0.5;
  let gradientProgress = clamp((diagonal + 1.0) * 0.5, 0.0, 1.0);
  let fillAlpha = mix(STYLE_FILL_TOP_LEFT_ALPHA, STYLE_FILL_BOTTOM_RIGHT_ALPHA, gradientProgress);
  var outputColor = vec4<f32>(1.0, 1.0, 1.0, fillAlpha);
  let rimStrokeWidthNormalized = STYLE_RIM_STROKE_WIDTH_PIXELS / max(fragmentInput.pixelRadius, 1.0);
  let rimInnerEdge = 1.0 - rimStrokeWidthNormalized;
  if (distanceFromCenter >= rimInnerEdge) {
    outputColor = vec4<f32>(
      mix(outputColor.rgb, vec3<f32>(1.0, 1.0, 1.0), STYLE_RIM_ALPHA),
      outputColor.a + STYLE_RIM_ALPHA * (1.0 - outputColor.a)
    );
  }
  let highlightCenter = vec2<f32>(-STYLE_HIGHLIGHT_OFFSET_RATIO, -STYLE_HIGHLIGHT_OFFSET_RATIO);
  let highlightDistance = length(quadCoordinate - highlightCenter) / STYLE_HIGHLIGHT_RADIUS_RATIO;
  if (highlightDistance < 1.0) {
    let highlightSourceAlpha = STYLE_HIGHLIGHT_ALPHA * (1.0 - highlightDistance);
    outputColor = vec4<f32>(
      mix(outputColor.rgb, vec3<f32>(1.0, 1.0, 1.0), highlightSourceAlpha),
      outputColor.a + highlightSourceAlpha * (1.0 - outputColor.a)
    );
  }
  return outputColor;
}
""".trimIndent()

    private val bubbleGlslFragment = """
const float STYLE_FILL_TOP_LEFT_ALPHA = 0.4;
const float STYLE_FILL_BOTTOM_RIGHT_ALPHA = 0.051;
const float STYLE_RIM_ALPHA = 0.8;
const float STYLE_RIM_STROKE_WIDTH_PIXELS = 2.0;
const float STYLE_HIGHLIGHT_ALPHA = 0.9;
const float STYLE_HIGHLIGHT_RADIUS_RATIO = 0.28;
const float STYLE_HIGHLIGHT_OFFSET_RATIO = 0.38;

vec4 STYLE_fragment(StyleFragmentInput fragmentInput) {
    vec2 quadCoordinate = fragmentInput.quadCoordinate;
    float distanceFromCenter = length(quadCoordinate);
    if (distanceFromCenter > 1.0) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    float diagonal = (quadCoordinate.x + quadCoordinate.y) * 0.5;
    float gradientProgress = clamp((diagonal + 1.0) * 0.5, 0.0, 1.0);
    float fillAlpha = mix(STYLE_FILL_TOP_LEFT_ALPHA, STYLE_FILL_BOTTOM_RIGHT_ALPHA, gradientProgress);
    vec4 outputColor = vec4(1.0, 1.0, 1.0, fillAlpha);
    float rimStrokeWidthNormalized = STYLE_RIM_STROKE_WIDTH_PIXELS / max(fragmentInput.pixelRadius, 1.0);
    float rimInnerEdge = 1.0 - rimStrokeWidthNormalized;
    if (distanceFromCenter >= rimInnerEdge) {
        outputColor = vec4(
            mix(outputColor.rgb, vec3(1.0, 1.0, 1.0), STYLE_RIM_ALPHA),
            outputColor.a + STYLE_RIM_ALPHA * (1.0 - outputColor.a)
        );
    }
    vec2 highlightCenter = vec2(-STYLE_HIGHLIGHT_OFFSET_RATIO, -STYLE_HIGHLIGHT_OFFSET_RATIO);
    float highlightDistance = length(quadCoordinate - highlightCenter) / STYLE_HIGHLIGHT_RADIUS_RATIO;
    if (highlightDistance < 1.0) {
        float highlightSourceAlpha = STYLE_HIGHLIGHT_ALPHA * (1.0 - highlightDistance);
        outputColor = vec4(
            mix(outputColor.rgb, vec3(1.0, 1.0, 1.0), highlightSourceAlpha),
            outputColor.a + highlightSourceAlpha * (1.0 - outputColor.a)
        );
    }
    return outputColor;
}
""".trimIndent()

    private val blobWgsl = """
const STYLE_MAXIMUM_LOBES = 16u;

fn STYLE_halfExtent(vertexInput: StyleVertexInput, halfExtent: vec2<f32>) -> vec2<f32> {
  return halfExtent * (1.0 + STYLE_UNIFORM(1));
}

fn STYLE_boundaryScale(angle: f32, age: f32) -> f32 {
  let lobeCount = min(u32(STYLE_UNIFORM(0)), STYLE_MAXIMUM_LOBES);
  let lobeStep = TWO_PI / f32(lobeCount);
  var normalizedAngle = angle;
  if (normalizedAngle < 0.0) {
    normalizedAngle = normalizedAngle + TWO_PI;
  }
  let position = normalizedAngle / lobeStep;
  let segmentIndex = u32(floor(position)) % lobeCount;
  let fraction = position - floor(position);
  var lobeRadii = array<f32, 4>(1.0, 1.0, 1.0, 1.0);
  for (var tapIndex = 0u; tapIndex < 4u; tapIndex = tapIndex + 1u) {
    let lobeIndex = (segmentIndex + lobeCount + tapIndex - 1u) % lobeCount;
    let frequency = STYLE_UNIFORM(2) + f32(lobeIndex) * STYLE_UNIFORM(3);
    let phase = f32(lobeIndex) * STYLE_UNIFORM(4);
    lobeRadii[tapIndex] = 1.0 + STYLE_UNIFORM(1) * sin(age * frequency + phase);
  }
  let startTangent = (lobeRadii[2] - lobeRadii[0]) * 0.5;
  let endTangent = (lobeRadii[3] - lobeRadii[1]) * 0.5;
  let squared = fraction * fraction;
  let cubed = squared * fraction;
  return (2.0 * cubed - 3.0 * squared + 1.0) * lobeRadii[1] +
    (cubed - 2.0 * squared + fraction) * startTangent +
    (-2.0 * cubed + 3.0 * squared) * lobeRadii[2] +
    (cubed - squared) * endTangent;
}

fn STYLE_fragment(fragmentInput: StyleFragmentInput) -> vec4<f32> {
  let quadCoordinate = fragmentInput.quadCoordinate;
  let boundaryScale = STYLE_boundaryScale(atan2(quadCoordinate.y, quadCoordinate.x), fragmentInput.age);
  if (length(quadCoordinate) * (1.0 + STYLE_UNIFORM(1)) > boundaryScale) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return fragmentInput.color;
}
""".trimIndent()

    private val blobGlslVertex = """
vec2 STYLE_halfExtent(StyleVertexInput vertexInput, vec2 halfExtent) {
    return halfExtent * (1.0 + STYLE_UNIFORM(1));
}
""".trimIndent()

    private val blobGlslFragment = """
const uint STYLE_MAXIMUM_LOBES = 16u;

float STYLE_boundaryScale(float angle, float age) {
    uint lobeCount = min(uint(STYLE_UNIFORM(0)), STYLE_MAXIMUM_LOBES);
    float lobeStep = TWO_PI / float(lobeCount);
    float normalizedAngle = angle;
    if (normalizedAngle < 0.0) {
        normalizedAngle = normalizedAngle + TWO_PI;
    }
    float lobePosition = normalizedAngle / lobeStep;
    uint segmentIndex = uint(floor(lobePosition)) % lobeCount;
    float fraction = lobePosition - floor(lobePosition);
    float lobeRadii[4] = float[4](1.0, 1.0, 1.0, 1.0);
    for (uint tapIndex = 0u; tapIndex < 4u; tapIndex = tapIndex + 1u) {
        uint lobeIndex = (segmentIndex + lobeCount + tapIndex - 1u) % lobeCount;
        float frequency = STYLE_UNIFORM(2) + float(lobeIndex) * STYLE_UNIFORM(3);
        float phase = float(lobeIndex) * STYLE_UNIFORM(4);
        lobeRadii[tapIndex] = 1.0 + STYLE_UNIFORM(1) * sin(age * frequency + phase);
    }
    float startTangent = (lobeRadii[2] - lobeRadii[0]) * 0.5;
    float endTangent = (lobeRadii[3] - lobeRadii[1]) * 0.5;
    float squared = fraction * fraction;
    float cubed = squared * fraction;
    return (2.0 * cubed - 3.0 * squared + 1.0) * lobeRadii[1] +
        (cubed - 2.0 * squared + fraction) * startTangent +
        (-2.0 * cubed + 3.0 * squared) * lobeRadii[2] +
        (cubed - squared) * endTangent;
}

vec4 STYLE_fragment(StyleFragmentInput fragmentInput) {
    vec2 quadCoordinate = fragmentInput.quadCoordinate;
    float boundaryScale = STYLE_boundaryScale(atan(quadCoordinate.y, quadCoordinate.x), fragmentInput.age);
    if (length(quadCoordinate) * (1.0 + STYLE_UNIFORM(1)) > boundaryScale) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    return fragmentInput.color;
}
""".trimIndent()

    private val dripWgsl = """
fn STYLE_quad(vertexInput: StyleVertexInput, halfExtent: vec2<f32>, quad: StyleQuad) -> StyleQuad {
  var result = quad;
  let beadRadius = vertexInput.dimensions.y;
  let tail = vertexInput.origin.xy;
  let head = vertexInput.positionAngle.xy;
  let axis = (head - tail) * vertexInput.scale;
  let axisLength = length(axis);
  var direction = vec2<f32>(0.0, 1.0);
  if (axisLength > 0.0001) {
    direction = axis / axisLength;
  }
  let beadPixels = max(beadRadius * vertexInput.scale, 0.5);
  let halfLength = axisLength * 0.5 + beadPixels;
  result.center = (head + tail) * 0.5;
  result.offset = direction * (vertexInput.corner.x * halfLength) + vec2<f32>(-direction.y, direction.x) * (vertexInput.corner.y * beadPixels);
  result.uv = vec2<f32>(halfLength, beadPixels);
  return result;
}

fn STYLE_fragment(fragmentInput: StyleFragmentInput) -> vec4<f32> {
  let color = fragmentInput.color;
  let quadCoordinate = fragmentInput.quadCoordinate;
  let uv = fragmentInput.uv;
  let beadRadius = uv.y;
  let trailRadius = beadRadius * STYLE_UNIFORM(0);
  let streakLength = max(2.0 * (uv.x - uv.y), 0.0);
  let neckSetting = max(beadRadius * STYLE_UNIFORM(1), 0.0001);
  let neckLength = min(streakLength, neckSetting);
  let tailRadius = mix(beadRadius, trailRadius, clamp(streakLength / neckSetting, 0.0, 1.0));
  let axisPosition = quadCoordinate.x * uv.x + streakLength * 0.5;
  let perpendicularPosition = quadCoordinate.y * uv.y;
  var isInside = false;
  if (axisPosition < 0.0) {
    isInside = length(vec2<f32>(axisPosition, perpendicularPosition)) <= tailRadius;
  } else if (axisPosition > streakLength) {
    isInside = length(vec2<f32>(axisPosition - streakLength, perpendicularPosition)) <= beadRadius;
  } else {
    let neckProgress = clamp((axisPosition - (streakLength - neckLength)) / max(neckLength, 0.0001), 0.0, 1.0);
    isInside = abs(perpendicularPosition) <= mix(tailRadius, beadRadius, neckProgress);
  }
  if (!isInside) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  var stripeGloss = 0.0;
  if (axisPosition >= 0.0 && axisPosition <= streakLength - neckLength) {
    let stripeWidth = max(trailRadius * STYLE_UNIFORM(4), 0.0001);
    stripeGloss = clamp(
      1.0 - abs(perpendicularPosition - trailRadius * STYLE_UNIFORM(3)) / stripeWidth,
      0.0,
      1.0
    );
  }
  let specularOffset = beadRadius * STYLE_UNIFORM(6);
  let specularCenter = vec2<f32>(streakLength - specularOffset, specularOffset);
  let specularRadius = max(beadRadius * STYLE_UNIFORM(5), 0.0001);
  let specularGloss = clamp(
    1.0 - length(vec2<f32>(axisPosition, perpendicularPosition) - specularCenter) / specularRadius,
    0.0,
    1.0
  );
  let glossAmount = clamp(max(stripeGloss, specularGloss) * STYLE_UNIFORM(2), 0.0, 1.0);
  return vec4<f32>(mix(color.rgb, vec3<f32>(1.0, 1.0, 1.0), glossAmount), color.a);
}
""".trimIndent()

    private val dripGlslVertex = """
StyleQuad STYLE_quad(StyleVertexInput vertexInput, vec2 halfExtent, StyleQuad quad) {
    StyleQuad result = quad;
    float beadRadius = vertexInput.dimensions.y;
    vec2 tail = vertexInput.origin.xy;
    vec2 head = vertexInput.positionAngle.xy;
    vec2 axis = (head - tail) * vertexInput.scale;
    float axisLength = length(axis);
    vec2 direction = vec2(0.0, 1.0);
    if (axisLength > 0.0001) {
        direction = axis / axisLength;
    }
    float beadPixels = max(beadRadius * vertexInput.scale, 0.5);
    float halfLength = axisLength * 0.5 + beadPixels;
    result.center = (head + tail) * 0.5;
    result.offset = direction * (vertexInput.corner.x * halfLength) + vec2(-direction.y, direction.x) * (vertexInput.corner.y * beadPixels);
    result.uv = vec2(halfLength, beadPixels);
    return result;
}
""".trimIndent()

    private val dripGlslFragment = """
vec4 STYLE_fragment(StyleFragmentInput fragmentInput) {
    vec4 color = fragmentInput.color;
    vec2 quadCoordinate = fragmentInput.quadCoordinate;
    vec2 uv = fragmentInput.uv;
    float beadRadius = uv.y;
    float trailRadius = beadRadius * STYLE_UNIFORM(0);
    float streakLength = max(2.0 * (uv.x - uv.y), 0.0);
    float neckSetting = max(beadRadius * STYLE_UNIFORM(1), 0.0001);
    float neckLength = min(streakLength, neckSetting);
    float tailRadius = mix(beadRadius, trailRadius, clamp(streakLength / neckSetting, 0.0, 1.0));
    float axisPosition = quadCoordinate.x * uv.x + streakLength * 0.5;
    float perpendicularPosition = quadCoordinate.y * uv.y;
    bool isInside = false;
    if (axisPosition < 0.0) {
        isInside = length(vec2(axisPosition, perpendicularPosition)) <= tailRadius;
    } else if (axisPosition > streakLength) {
        isInside = length(vec2(axisPosition - streakLength, perpendicularPosition)) <= beadRadius;
    } else {
        float neckProgress = clamp((axisPosition - (streakLength - neckLength)) / max(neckLength, 0.0001), 0.0, 1.0);
        isInside = abs(perpendicularPosition) <= mix(tailRadius, beadRadius, neckProgress);
    }
    if (!isInside) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    float stripeGloss = 0.0;
    if (axisPosition >= 0.0 && axisPosition <= streakLength - neckLength) {
        float stripeWidth = max(trailRadius * STYLE_UNIFORM(4), 0.0001);
        stripeGloss = clamp(
            1.0 - abs(perpendicularPosition - trailRadius * STYLE_UNIFORM(3)) / stripeWidth,
            0.0,
            1.0
        );
    }
    float specularOffset = beadRadius * STYLE_UNIFORM(6);
    vec2 specularCenter = vec2(streakLength - specularOffset, specularOffset);
    float specularRadius = max(beadRadius * STYLE_UNIFORM(5), 0.0001);
    float specularGloss = clamp(
        1.0 - length(vec2(axisPosition, perpendicularPosition) - specularCenter) / specularRadius,
        0.0,
        1.0
    );
    float glossAmount = clamp(max(stripeGloss, specularGloss) * STYLE_UNIFORM(2), 0.0, 1.0);
    return vec4(mix(color.rgb, vec3(1.0, 1.0, 1.0), glossAmount), color.a);
}
""".trimIndent()

    fun create(dripStyleSettings: DripStyleSettings): List<GpuStyleShader> = listOf(
        GpuStyleShader(
            renderStyle = EffectStyles.DUST,
            wgslSource = dustWgsl,
            glslVertexSource = "",
            glslFragmentSource = dustGlslFragment,
            blendMode = GpuBlendMode.MAX
        ),
        GpuStyleShader(
            renderStyle = EffectStyles.BUBBLE,
            wgslSource = bubbleWgsl,
            glslVertexSource = "",
            glslFragmentSource = bubbleGlslFragment
        ),
        GpuStyleShader(
            renderStyle = EffectStyles.BLOB,
            wgslSource = blobWgsl,
            glslVertexSource = blobGlslVertex,
            glslFragmentSource = blobGlslFragment,
            uniformSource = BlobStyleUniformSource()
        ),
        GpuStyleShader(
            renderStyle = EffectStyles.DRIP,
            wgslSource = dripWgsl,
            glslVertexSource = dripGlslVertex,
            glslFragmentSource = dripGlslFragment,
            uniformSource = DripStyleUniformSource(dripStyleSettings)
        )
    )

}
