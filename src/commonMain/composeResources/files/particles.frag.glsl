#version 310 es
precision highp float;
precision highp int;

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

layout(binding = 0) uniform sampler2D renderTexture;

uniform int uActiveStyle;

in vec4 vColor;
in vec2 vQuadCoordinate;
flat in float vRenderKind;
flat in float vShapeKind;
in vec2 vUv;
flat in float vAge;
flat in float vPixelRadius;

out vec4 fragmentColor;

const float TWO_PI = 6.28318530717959;

struct StyleFragmentInput {
    vec4 color;
    vec2 quadCoordinate;
    float shapeKind;
    vec2 uv;
    float age;
    float pixelRadius;
};

/*{{STYLE_FUNCTIONS}}*/

void main() {
    StyleFragmentInput fragmentInput = StyleFragmentInput(vColor, vQuadCoordinate, vShapeKind, vUv, vAge, vPixelRadius);
/*{{STYLE_FRAGMENT_DISPATCH}}*/
    fragmentColor = vec4(0.0, 0.0, 0.0, 0.0);
}
