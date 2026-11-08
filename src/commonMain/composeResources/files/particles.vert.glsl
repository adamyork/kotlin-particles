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
    float spriteTicksPerFrame;
    float styleUniform0;
    float styleUniform1;
    float styleUniform2;
    float styleUniform3;
    float styleUniform4;
    float styleUniform5;
    float styleUniform6;
    float styleUniform7;
    float styleUniform8;
    float styleUniform9;
    float styleUniform10;
    float styleUniform11;
    float styleUniform12;
    float styleUniform13;
    float styleUniform14;
    float styleUniform15;
    float styleUniform16;
    float styleUniform17;
} renderUniforms;

uniform int uActiveStyle;
uniform int uInstanceOffset;

out vec4 vColor;
out vec2 vQuadCoordinate;
flat out float vRenderKind;
flat out float vShapeKind;
out vec2 vUv;
flat out float vAge;
flat out float vPixelRadius;

const uint VECTORS_PER_PARTICLE = 16u;

struct StyleVertexInput {
    vec4 positionAngle;
    vec4 dimensions;
    vec4 origin;
    float age;
    float scale;
    vec2 corner;
    bool isRectangle;
};

struct StyleQuad {
    vec2 center;
    vec2 offset;
    vec2 uv;
};

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

/*{{STYLE_FUNCTIONS}}*/

void main() {
    uint base = uint(gl_InstanceID + uInstanceOffset) * VECTORS_PER_PARTICLE;
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
    float renderKind = kindData.z;
    if (abs(renderKind - float(uActiveStyle)) > 0.5) {
        emitHiddenVertex();
        return;
    }
    float renderScale = renderUniforms.renderScale;
    bool isRectangleShape = dimensions.x > 0.5;
    vec2 corner = quadCorner(gl_VertexID);
    StyleVertexInput vertexInput = StyleVertexInput(positionAngle, dimensions, origin, age, renderScale, corner, isRectangleShape);
    vec2 halfExtent = vec2(dimensions.y, dimensions.y);
    if (isRectangleShape) {
        halfExtent = vec2(dimensions.y * 0.5, dimensions.z * 0.5);
    }
/*{{STYLE_HALF_EXTENT_HOOKS}}*/
    halfExtent = max(halfExtent * renderScale, vec2(0.5, 0.5));
    StyleQuad quad;
    quad.center = positionAngle.xy;
    quad.offset = vec2(corner.x * halfExtent.x, corner.y * halfExtent.y);
    if (isRectangleShape) {
        float cosine = cos(positionAngle.w);
        float sine = sin(positionAngle.w);
        quad.offset = vec2(
            quad.offset.x * cosine - quad.offset.y * sine,
            quad.offset.x * sine + quad.offset.y * cosine
        );
    }
    quad.uv = vec2((corner.x + 1.0) * 0.5, (corner.y + 1.0) * 0.5);
/*{{STYLE_QUAD_HOOKS}}*/
    float localX = (quad.center.x - renderUniforms.viewportX) * renderScale + quad.offset.x;
    float localY = (quad.center.y - renderUniforms.viewportY) * renderScale + quad.offset.y;
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
    vUv = quad.uv;
    vAge = age;
    vPixelRadius = max(dimensions.y * renderScale, 1.0);
}
