#version 310 es
precision mediump float;

in vec4 vColor;
in vec2 vQuadCoordinate;
in float vShapeFlag;
in vec2 vUv;
in float vParticleKind;
in float vAge;

uniform sampler2D uMapItemTexture;
uniform bool uDustPass;

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
    if (shouldDiscardCircle(vShapeFlag, vQuadCoordinate)) {
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }
    fragColor = vColor;
}
