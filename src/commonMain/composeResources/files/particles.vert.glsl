#version 310 es
precision highp float;
precision highp int;

layout(std430, binding = 0) readonly buffer ParticleBuffer {
    vec4 data[];
} renderParticles;

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

out vec4 vColor;
out vec2 vQuadCoordinate;
flat out float vRenderKind;
flat out float vShapeKind;
out vec2 vUv;
flat out float vAge;
flat out float vPixelRadius;

const uint VECTORS_PER_PARTICLE = 16u;

vec2 quadCorner(int vertexIndex) {
    switch (vertexIndex) {
        case 0: return vec2(-1.0, -1.0);
        case 1: return vec2(1.0, -1.0);
        case 2: return vec2(1.0, 1.0);
        case 3: return vec2(-1.0, -1.0);
        case 4: return vec2(1.0, 1.0);
        default: break;
    }
    return vec2(-1.0, 1.0);
}

void emitHiddenVertex() {
    gl_Position = vec4(-2.0, -2.0, 0.0, 1.0);
    vColor = vec4(0.0, 0.0, 0.0, 0.0);
    vQuadCoordinate = vec2(0.0, 0.0);
    vRenderKind = 0.0;
    vShapeKind = 0.0;
    vUv = vec2(0.0, 0.0);
    vAge = 0.0;
    vPixelRadius = 0.0;
}

void main() {
    uint base = uint(gl_InstanceID) * VECTORS_PER_PARTICLE;
    vec4 positionAngle = renderParticles.data[base + 0u];
    vec4 lifecycle = renderParticles.data[base + 3u];
    vec4 dimensions = renderParticles.data[base + 4u];
    vec4 kindData = renderParticles.data[base + 5u];
    vec4 color = renderParticles.data[base + 8u];
    vec4 origin = renderParticles.data[base + 11u];
    float age = lifecycle.x;
    float delay = lifecycle.y;
    if (lifecycle.w < 0.5 || age < delay) {
        emitHiddenVertex();
        return;
    }
    float renderScale = renderUniforms.renderScale;
    float renderKind = kindData.z;
    bool isRectangleShape = dimensions.x > 0.5;
    bool isSprite = renderKind > 1.5 && renderKind < 3.5;
    bool isBlob = renderKind > 3.5 && renderKind < 4.5;
    bool isDrip = renderKind > 5.5;
    vec2 halfExtent = vec2(dimensions.y, dimensions.y);
    if (isRectangleShape) {
        halfExtent = vec2(dimensions.y * 0.5, dimensions.z * 0.5);
    }
    if (isSprite) {
        halfExtent = vec2(renderUniforms.spriteFrameWidth * 0.5, renderUniforms.spriteFrameHeight * 0.5);
    }
    if (isBlob) {
        halfExtent = halfExtent * (1.0 + renderUniforms.blobAmplitudeRatio);
    }
    halfExtent = max(halfExtent * renderScale, vec2(0.5, 0.5));
    vec2 corner = quadCorner(gl_VertexID);
    vec2 quadCenter = positionAngle.xy;
    vec2 quadOffset = vec2(corner.x * halfExtent.x, corner.y * halfExtent.y);
    if (isRectangleShape && !isSprite) {
        float cosine = cos(positionAngle.w);
        float sine = sin(positionAngle.w);
        quadOffset = vec2(
            quadOffset.x * cosine - quadOffset.y * sine,
            quadOffset.x * sine + quadOffset.y * cosine
        );
    }
    vec2 uv = vec2((corner.x + 1.0) * 0.5, (corner.y + 1.0) * 0.5);
    if (isSprite) {
        float frameIndex = 0.0;
        if (renderKind > 2.5) {
            frameIndex = mod(floor(age / renderUniforms.spriteTicksPerFrame), max(origin.w, 1.0));
        }
        uv = vec2((uv.x + frameIndex) / max(renderUniforms.spriteSheetFrameCount, 1.0), uv.y);
    }
    if (isDrip) {
        float beadRadius = dimensions.y;
        vec2 tail = origin.xy;
        vec2 head = positionAngle.xy;
        vec2 axis = (head - tail) * renderScale;
        float axisLength = length(axis);
        vec2 direction = vec2(0.0, 1.0);
        if (axisLength > 0.0001) {
            direction = axis / axisLength;
        }
        float beadPixels = max(beadRadius * renderScale, 0.5);
        float halfLength = axisLength * 0.5 + beadPixels;
        quadCenter = (head + tail) * 0.5;
        quadOffset = direction * (corner.x * halfLength) + vec2(-direction.y, direction.x) * (corner.y * beadPixels);
        uv = vec2(halfLength, beadPixels);
    }
    float localX = (quadCenter.x - renderUniforms.viewportX) * renderScale + quadOffset.x;
    float localY = (quadCenter.y - renderUniforms.viewportY) * renderScale + quadOffset.y;
    gl_Position = vec4(
        (localX / renderUniforms.canvasWidth) * 2.0 - 1.0,
        -((localY / renderUniforms.canvasHeight) * 2.0 - 1.0),
        0.0,
        1.0
    );
    vColor = color;
    vQuadCoordinate = corner;
    vRenderKind = renderKind;
    vShapeKind = dimensions.x;
    vUv = uv;
    vAge = age;
    vPixelRadius = max(dimensions.y * renderScale, 1.0);
}
