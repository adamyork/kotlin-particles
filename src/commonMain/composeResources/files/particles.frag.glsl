#version 310 es
precision mediump float;

in vec4 vColor;
in vec2 vLocal;
in float vShape;
in float vFrame;
in float vLifetime;
in float vKind;

out vec4 fragColor;

const float KIND_PROJECTILE = 2.0;
const float SHAPE_CIRCLE = 1.0;
const float KIND_DUST = 1.0;
const float KIND_FIREWORK_BURST = 5.0;

void main() {
    if (vShape == SHAPE_CIRCLE) {
        if (length(vLocal - vec2(0.5)) > 0.5) {
            discard;
        }
    }

    float ageProgress = clamp(vFrame / max(vLifetime, 1.0), 0.0, 1.0);
    float alphaMultiplier = 1.0;
    if (vKind != KIND_PROJECTILE) {
        if (ageProgress < 0.33) {
            alphaMultiplier = 1.0;
        } else if (ageProgress < 0.66) {
            alphaMultiplier = 0.66;
        } else {
            alphaMultiplier = 0.33;
        }
    }

    if (vKind == KIND_DUST) {
        vec3 boosted = max(vColor.rgb * 1.35, vec3(0.6));
        float boostedAlpha = min((vColor.a * alphaMultiplier) + 0.2, 1.0);
        fragColor = vec4(boosted, boostedAlpha);
        return;
    }

    if (vKind == KIND_FIREWORK_BURST) {
        alphaMultiplier = max(alphaMultiplier, 0.45);
    }

    fragColor = vec4(vColor.rgb, vColor.a * alphaMultiplier);
}
