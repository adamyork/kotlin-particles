#version 310 es
precision mediump float;

in vec4 vColor;
in vec2 vQuadCoordinate;
in float vShapeFlag;
in vec2 vUv;
in float vParticleKind;
in float vAge;
in float vPixelRadius;

uniform sampler2D uMapItemTexture;
uniform bool uDustPass;

const float dripTrailRadiusRatio = {{DRIPS_TRAIL_RADIUS_RATIO}};
const float dripNeckLengthRatio = {{DRIPS_NECK_LENGTH_RATIO}};
const float dripGlossIntensity = {{DRIPS_GLOSS_INTENSITY}};
const float dripGlossStripeOffsetRatio = {{DRIPS_GLOSS_STRIPE_OFFSET_RATIO}};
const float dripGlossStripeWidthRatio = {{DRIPS_GLOSS_STRIPE_WIDTH_RATIO}};
const float dripGlossSpecularRadiusRatio = {{DRIPS_GLOSS_SPECULAR_RADIUS_RATIO}};
const float dripGlossSpecularOffsetRatio = {{DRIPS_GLOSS_SPECULAR_OFFSET_RATIO}};

out vec4 fragColor;

const float blobProjectileQuadSizeMultiplier = 1.4;

bool shouldDiscardCircle(float shapeFlag, vec2 quadCoordinate) {
    return shapeFlag > 0.5 && dot(quadCoordinate, quadCoordinate) > 1.0;
}

float blobProjectileBoundaryScale(float angle, float age) {
    float wobbleA = sin((angle * 2.0) + (age * 0.05));
    float wobbleB = sin((angle * 3.0) + (age * 0.035) + 2.1);
    float wobbleC = sin((angle * 5.0) + (age * 0.07) + 4.2);
    return 1.0 + (0.12 * wobbleA) + (0.08 * wobbleB) + (0.06 * wobbleC);
}

bool shouldDiscardBlobProjectile(vec2 quadCoordinate, float age) {
    float pixelDistance = length(quadCoordinate) * blobProjectileQuadSizeMultiplier;
    float pixelAngle = atan(quadCoordinate.y, quadCoordinate.x);
    float boundaryScale = blobProjectileBoundaryScale(pixelAngle, age);
    return pixelDistance > boundaryScale;
}

const float bubbleFillTopLeftAlpha = 0.4;
const float bubbleFillBottomRightAlpha = 0.051;
const float bubbleRimAlpha = 0.8;
const float bubbleRimStrokeWidthPixels = 2.0;
const float bubbleHighlightAlpha = 0.9;
const float bubbleHighlightRadiusRatio = 0.28;
const float bubbleHighlightOffsetRatio = 0.38;

vec4 bubbleFragmentColor(vec2 quadCoordinate, float pixelRadius) {
    float distanceFromCenter = length(quadCoordinate);
    if (distanceFromCenter > 1.0) {
        return vec4(0.0, 0.0, 0.0, 0.0);
    }
    float diagonal = (quadCoordinate.x + quadCoordinate.y) * 0.5;
    float gradientProgress = clamp((diagonal + 1.0) * 0.5, 0.0, 1.0);
    float fillAlpha = mix(bubbleFillTopLeftAlpha, bubbleFillBottomRightAlpha, gradientProgress);
    vec4 outColor = vec4(1.0, 1.0, 1.0, fillAlpha);
    float rimStrokeWidthNormalized = bubbleRimStrokeWidthPixels / max(pixelRadius, 1.0);
    float rimInnerEdge = 1.0 - rimStrokeWidthNormalized;
    if (distanceFromCenter >= rimInnerEdge) {
        outColor = vec4(
            mix(outColor.rgb, vec3(1.0, 1.0, 1.0), bubbleRimAlpha),
            outColor.a + (bubbleRimAlpha * (1.0 - outColor.a))
        );
    }
    vec2 highlightCenter = vec2(-bubbleHighlightOffsetRatio, -bubbleHighlightOffsetRatio);
    float highlightDistance = length(quadCoordinate - highlightCenter) / bubbleHighlightRadiusRatio;
    if (highlightDistance < 1.0) {
        float highlightSourceAlpha = bubbleHighlightAlpha * (1.0 - highlightDistance);
        outColor = vec4(
            mix(outColor.rgb, vec3(1.0, 1.0, 1.0), highlightSourceAlpha),
            outColor.a + (highlightSourceAlpha * (1.0 - outColor.a))
        );
    }
    return outColor;
}

void main() {
    bool isDust = vParticleKind > 0.5 && vParticleKind <= 1.5;

    if (uDustPass) {
        if (!isDust) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            return;
        }
        if (shouldDiscardCircle(vShapeFlag, vQuadCoordinate)) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            return;
        }
        fragColor = vec4(vColor.rgb * vColor.a, vColor.a);
        return;
    }

    if (isDust) {
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }
    if (vParticleKind > 2.5 && vParticleKind <= 3.5) {
        vec4 sampled = texture(uMapItemTexture, vUv);
        fragColor = sampled * vColor;
        return;
    }
    if (vParticleKind > 11.5 && vParticleKind <= 12.5) {
        if (shouldDiscardBlobProjectile(vQuadCoordinate, vAge)) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            return;
        }
        fragColor = vColor;
        return;
    }
    if (vParticleKind > 12.5 && vParticleKind <= 13.5) {
        fragColor = bubbleFragmentColor(vQuadCoordinate, vPixelRadius);
        return;
    }
    if (vParticleKind > 14.5 && vParticleKind <= 15.5) {
        float beadRadius = vUv.y;
        float trailRadius = beadRadius * dripTrailRadiusRatio;
        float streakLength = max(2.0 * (vUv.x - vUv.y), 0.0);
        float neckSetting = max(beadRadius * dripNeckLengthRatio, 0.0001);
        float neckLength = min(streakLength, neckSetting);
        float tailRadius = mix(beadRadius, trailRadius, clamp(streakLength / neckSetting, 0.0, 1.0));
        float axisPosition = (vQuadCoordinate.x * vUv.x) + (streakLength * 0.5);
        float perpendicularPosition = vQuadCoordinate.y * vUv.y;
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
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            return;
        }
        float stripeGloss = 0.0;
        if (axisPosition >= 0.0 && axisPosition <= streakLength - neckLength) {
            float stripeWidth = max(trailRadius * dripGlossStripeWidthRatio, 0.0001);
            stripeGloss = clamp(1.0 - abs(perpendicularPosition - (trailRadius * dripGlossStripeOffsetRatio)) / stripeWidth, 0.0, 1.0);
        }
        float specularOffset = beadRadius * dripGlossSpecularOffsetRatio;
        vec2 specularCenter = vec2(streakLength - specularOffset, specularOffset);
        float specularRadius = max(beadRadius * dripGlossSpecularRadiusRatio, 0.0001);
        float specularGloss = clamp(1.0 - length(vec2(axisPosition, perpendicularPosition) - specularCenter) / specularRadius, 0.0, 1.0);
        float glossAmount = clamp(max(stripeGloss, specularGloss) * dripGlossIntensity, 0.0, 1.0);
        fragColor = vec4(mix(vColor.rgb, vec3(1.0, 1.0, 1.0), glossAmount), vColor.a);
        return;
    }
    if (shouldDiscardCircle(vShapeFlag, vQuadCoordinate)) {
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }
    fragColor = vColor;
}
