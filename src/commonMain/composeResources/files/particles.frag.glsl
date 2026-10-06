#version 310 es
precision mediump float;

in vec4 vColor;
in vec2 vQuadCoordinate;
in float vShapeFlag;
in vec2 vUv;
in float vParticleKind;

uniform sampler2D uMapItemTexture;
uniform bool uDustPass;

out vec4 fragColor;

bool shouldDiscardCircle(float shapeFlag, vec2 quadCoordinate) {
    return shapeFlag > 0.5 && dot(quadCoordinate, quadCoordinate) > 1.0;
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
    if (shouldDiscardCircle(vShapeFlag, vQuadCoordinate)) {
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }
    fragColor = vColor;
}
