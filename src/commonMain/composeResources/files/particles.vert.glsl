#version 310 es

layout(location = 0) in vec2 aUnused;

layout(std430, binding = 0) readonly buffer ParticleBuffer {
    vec4 particleData[];
};

uniform float uViewportX;
uniform float uViewportY;
uniform float uViewportWidth;
uniform float uViewportHeight;
uniform float uSizeScale;
uniform float uMapItemSpriteWidth;
uniform float uMapItemSpriteHeight;

out vec4 vColor;
out vec2 vQuadCoordinate;
out float vShapeFlag;
out vec2 vUv;
out float vParticleKind;
out float vAge;

const float blobProjectileQuadSizeMultiplier = 1.4;
const float itemReturnSpriteFrameCount = 8.0;
const float itemReturnSpriteTicksPerFrame = 5.0;

vec2 quadCorner(int vertexIndex) {
    switch (vertexIndex) {
        case 0: return vec2(-1.0, -1.0);
        case 1: return vec2(1.0, -1.0);
        case 2: return vec2(1.0, 1.0);
        case 3: return vec2(-1.0, -1.0);
        case 4: return vec2(1.0, 1.0);
        default: return vec2(-1.0, 1.0);
    }
}

void main() {
    uint base = uint(gl_InstanceID) * 6u;
    vec4 positionVelocity = particleData[base + 0u];
    vec4 lifecycle = particleData[base + 1u];
    vec4 colorRgba = particleData[base + 2u];
    vec4 typeInfo = particleData[base + 3u];
    vec4 kindDataA = particleData[base + 4u];

    if (lifecycle.w <= 0.5) {
        gl_Position = vec4(-2.0, -2.0, 0.0, 1.0);
        vColor = vec4(0.0, 0.0, 0.0, 0.0);
        vQuadCoordinate = vec2(0.0, 0.0);
        vShapeFlag = 0.0;
        vUv = vec2(0.0, 0.0);
        vParticleKind = 0.0;
        vAge = 0.0;
        return;
    }

    float particleKind = typeInfo.x;
    bool isMapItemReturn = particleKind > 2.5 && particleKind <= 3.5;
    bool isDust = particleKind > 0.5 && particleKind <= 1.5;
    bool isBlobProjectile = particleKind > 11.5 && particleKind <= 12.5;
    bool usesUnscaledSize = isMapItemReturn || isDust;
    float widthScale = usesUnscaledSize ? 1.0 : uSizeScale;
    float baseWidth = isMapItemReturn ? uMapItemSpriteWidth : lifecycle.z;
    float baseHeight = isMapItemReturn ? uMapItemSpriteHeight : lifecycle.z;
    float blobSizeMultiplier = isBlobProjectile ? blobProjectileQuadSizeMultiplier : 1.0;
    float halfWidth = max(baseWidth * widthScale * 0.5 * blobSizeMultiplier, 1.0);
    float halfHeight = max(baseHeight * widthScale * 0.5 * blobSizeMultiplier, 1.0);
    vec2 corner = quadCorner(gl_VertexID);
    float localX = (positionVelocity.x - uViewportX) + (corner.x * halfWidth);
    float localY = (positionVelocity.y - uViewportY) + (corner.y * halfHeight);
    float x = (localX / uViewportWidth) * 2.0 - 1.0;
    float y = (localY / uViewportHeight) * 2.0 - 1.0;

    float lifetime = max(lifecycle.y, 1.0);
    float ageProgress = clamp(lifecycle.x / lifetime, 0.0, 1.0);
    bool isFireworkBurst = particleKind > 4.5 && particleKind <= 5.5;
    bool isCollision = particleKind > 5.5 && particleKind <= 6.5;
    float resolvedAlpha;
    if (isDust || isCollision) {
        resolvedAlpha = mix(colorRgba.w, typeInfo.w, ageProgress);
    } else {
        resolvedAlpha = colorRgba.w;
    }

    vec3 resolvedColor = vec3(colorRgba.x, colorRgba.y, colorRgba.z);
    if (isFireworkBurst) {
        float packedEndColor = typeInfo.z;
        float endColorRed = floor(packedEndColor / 65536.0);
        float endColorGreen = floor((packedEndColor - (endColorRed * 65536.0)) / 256.0);
        float endColorBlue = packedEndColor - (endColorRed * 65536.0) - (endColorGreen * 256.0);
        vec3 endColor = vec3(endColorRed / 255.0, endColorGreen / 255.0, endColorBlue / 255.0);
        resolvedColor = mix(resolvedColor, endColor, ageProgress);
    }

    gl_Position = vec4(x, -y, 0.0, 1.0);
    vColor = vec4(resolvedColor, resolvedAlpha);
    vQuadCoordinate = corner;
    vShapeFlag = typeInfo.y;
    float localU = (corner.x + 1.0) * 0.5;
    float localV = (corner.y + 1.0) * 0.5;
    float itemReturnMaxFrames = max(kindDataA.x, 1.0);
    float itemReturnFrameIndex = mod(floor(lifecycle.x / itemReturnSpriteTicksPerFrame), itemReturnMaxFrames);
    float itemReturnUvX = (localU + itemReturnFrameIndex) / itemReturnSpriteFrameCount;
    vUv = vec2(isMapItemReturn ? itemReturnUvX : localU, localV);
    vParticleKind = particleKind;
    vAge = lifecycle.x;
}
