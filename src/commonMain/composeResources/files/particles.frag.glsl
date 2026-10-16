#version 310 es
precision highp float;
precision highp int;

layout(std140, binding = 1) uniform RenderUniforms {
    float viewportX;
    float viewportY;
    float canvasWidth;
    float canvasHeight;
    float renderScale;
    float spriteFrameWidth;
    float spriteFrameHeight;
    float spriteSheetFrameCount;
    float spriteTicksPerFrame;
    float dripTrailRadiusRatio;
    float dripNeckLengthRatio;
    float dripGlossIntensity;
    float dripGlossStripeOffsetRatio;
    float dripGlossStripeWidthRatio;
    float dripGlossSpecularRadiusRatio;
    float dripGlossSpecularOffsetRatio;
    float blobLobeCount;
    float blobAmplitudeRatio;
    float blobBaseFrequency;
    float blobFrequencyStep;
    float blobPhaseStep;
    float reservedA;
    float reservedB;
    float reservedC;
} renderUniforms;

layout(binding = 0) uniform sampler2D renderTexture;

uniform int uDustPass;

in vec4 vColor;
in vec2 vQuadCoordinate;
flat in float vRenderKind;
flat in float vShapeKind;
in vec2 vUv;
flat in float vAge;
flat in float vPixelRadius;

out vec4 fragmentColor;

const float TWO_PI = 6.28318530717959;
const float BUBBLE_FILL_TOP_LEFT_ALPHA = 0.4;
const float BUBBLE_FILL_BOTTOM_RIGHT_ALPHA = 0.051;
const float BUBBLE_RIM_ALPHA = 0.8;
const float BUBBLE_RIM_STROKE_WIDTH_PIXELS = 2.0;
const float BUBBLE_HIGHLIGHT_ALPHA = 0.9;
const float BUBBLE_HIGHLIGHT_RADIUS_RATIO = 0.28;
const float BUBBLE_HIGHLIGHT_OFFSET_RATIO = 0.38;
const uint MAXIMUM_BLOB_LOBES = 16u;

float blobBoundaryScale(float angle, float age) {
    uint lobeCount = min(uint(renderUniforms.blobLobeCount), MAXIMUM_BLOB_LOBES);
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
        float frequency = renderUniforms.blobBaseFrequency + float(lobeIndex) * renderUniforms.blobFrequencyStep;
        float phase = float(lobeIndex) * renderUniforms.blobPhaseStep;
        lobeRadii[tapIndex] = 1.0 + renderUniforms.blobAmplitudeRatio * sin(age * frequency + phase);
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

vec4 bubbleFragmentColor(vec2 quadCoordinate, float pixelRadius) {
    float distanceFromCenter = length(quadCoordinate);
    if (distanceFromCenter > 1.0) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    float diagonal = (quadCoordinate.x + quadCoordinate.y) * 0.5;
    float gradientProgress = clamp((diagonal + 1.0) * 0.5, 0.0, 1.0);
    float fillAlpha = mix(BUBBLE_FILL_TOP_LEFT_ALPHA, BUBBLE_FILL_BOTTOM_RIGHT_ALPHA, gradientProgress);
    vec4 outputColor = vec4(1.0, 1.0, 1.0, fillAlpha);
    float rimStrokeWidthNormalized = BUBBLE_RIM_STROKE_WIDTH_PIXELS / max(pixelRadius, 1.0);
    float rimInnerEdge = 1.0 - rimStrokeWidthNormalized;
    if (distanceFromCenter >= rimInnerEdge) {
        outputColor = vec4(
            mix(outputColor.rgb, vec3(1.0, 1.0, 1.0), BUBBLE_RIM_ALPHA),
            outputColor.a + BUBBLE_RIM_ALPHA * (1.0 - outputColor.a)
        );
    }
    vec2 highlightCenter = vec2(-BUBBLE_HIGHLIGHT_OFFSET_RATIO, -BUBBLE_HIGHLIGHT_OFFSET_RATIO);
    float highlightDistance = length(quadCoordinate - highlightCenter) / BUBBLE_HIGHLIGHT_RADIUS_RATIO;
    if (highlightDistance < 1.0) {
        float highlightSourceAlpha = BUBBLE_HIGHLIGHT_ALPHA * (1.0 - highlightDistance);
        outputColor = vec4(
            mix(outputColor.rgb, vec3(1.0, 1.0, 1.0), highlightSourceAlpha),
            outputColor.a + highlightSourceAlpha * (1.0 - outputColor.a)
        );
    }
    return outputColor;
}

vec4 dripFragmentColor(vec4 color, vec2 quadCoordinate, vec2 uv) {
    float beadRadius = uv.y;
    float trailRadius = beadRadius * renderUniforms.dripTrailRadiusRatio;
    float streakLength = max(2.0 * (uv.x - uv.y), 0.0);
    float neckSetting = max(beadRadius * renderUniforms.dripNeckLengthRatio, 0.0001);
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
        float stripeWidth = max(trailRadius * renderUniforms.dripGlossStripeWidthRatio, 0.0001);
        stripeGloss = clamp(
            1.0 - abs(perpendicularPosition - trailRadius * renderUniforms.dripGlossStripeOffsetRatio) / stripeWidth,
            0.0,
            1.0
        );
    }
    float specularOffset = beadRadius * renderUniforms.dripGlossSpecularOffsetRatio;
    vec2 specularCenter = vec2(streakLength - specularOffset, specularOffset);
    float specularRadius = max(beadRadius * renderUniforms.dripGlossSpecularRadiusRatio, 0.0001);
    float specularGloss = clamp(
        1.0 - length(vec2(axisPosition, perpendicularPosition) - specularCenter) / specularRadius,
        0.0,
        1.0
    );
    float glossAmount = clamp(max(stripeGloss, specularGloss) * renderUniforms.dripGlossIntensity, 0.0, 1.0);
    return vec4(mix(color.rgb, vec3(1.0, 1.0, 1.0), glossAmount), color.a);
}

vec4 shapesFragmentColor() {
    if (vRenderKind > 0.5 && vRenderKind < 1.5) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    if (vRenderKind > 1.5 && vRenderKind < 3.5) {
        return textureLod(renderTexture, vUv, 0.0);
    }
    if (vRenderKind > 3.5 && vRenderKind < 4.5) {
        float boundaryScale = blobBoundaryScale(atan(vQuadCoordinate.y, vQuadCoordinate.x), vAge);
        if (length(vQuadCoordinate) * (1.0 + renderUniforms.blobAmplitudeRatio) > boundaryScale) {
            return vec4(0.0, 0.0, 0.0, 0.0);
        }
        return vColor;
    }
    if (vRenderKind > 4.5 && vRenderKind < 5.5) {
        return bubbleFragmentColor(vQuadCoordinate, vPixelRadius);
    }
    if (vRenderKind > 5.5) {
        return dripFragmentColor(vColor, vQuadCoordinate, vUv);
    }
    if (vShapeKind < 0.5 && dot(vQuadCoordinate, vQuadCoordinate) > 1.0) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    return vColor;
}

vec4 dustFragmentColor() {
    if (vRenderKind < 0.5 || vRenderKind > 1.5) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    if (vShapeKind < 0.5 && dot(vQuadCoordinate, vQuadCoordinate) > 1.0) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    return vec4(vColor.rgb * vColor.a, vColor.a);
}

void main() {
    if (uDustPass == 1) {
        fragmentColor = dustFragmentColor();
    } else {
        fragmentColor = shapesFragmentColor();
    }
}
