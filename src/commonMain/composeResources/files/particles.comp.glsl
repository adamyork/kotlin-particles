#version 310 es
layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

layout(std430, binding = 0) readonly buffer SrcBuffer {
    vec4 srcData[];
};
layout(std430, binding = 1) writeonly buffer DstBuffer {
    vec4 dstData[];
};
layout(std430, binding = 2) readonly buffer SpawnBuffer {
    vec4 spawnData[];
};

uniform float uDeltaTime;
uniform float uGravity;
uniform float uBurstFrameGrowthMultiplier;
uniform float uBurstSpeedCoefficient;
uniform float uProjectileSpeed;
uniform float uMapItemReturnSpeed;
uniform float uMapItemReturnMinTravelDist;
uniform vec4 uPlayerRect;
uniform vec4 uViewPortRect;
uniform int uMaxParticles;

const float KIND_PROJECTILE = 2.0;
const float KIND_MAP_ITEM_RETURN = 3.0;
const float KIND_DUST = 1.0;
const float KIND_FIREWORK_TAIL = 4.0;
const float KIND_FIREWORK_BURST = 5.0;

void writeParticle(int base, vec4 p0, vec4 p1, vec4 p2, vec4 p3) {
    dstData[base + 0] = p0;
    dstData[base + 1] = p1;
    dstData[base + 2] = p2;
    dstData[base + 3] = p3;
}

void main() {
    uint particleIndex = gl_GlobalInvocationID.x;
    if (int(particleIndex) >= uMaxParticles) {
        return;
    }
    int base = int(particleIndex) * 4;

    vec4 spawn0 = spawnData[base + 0];
    vec4 spawn1 = spawnData[base + 1];
    vec4 spawn2 = spawnData[base + 2];
    vec4 spawn3 = spawnData[base + 3];

    if (spawn1.w > 0.5) {
        writeParticle(base, spawn0, spawn1, spawn2, spawn3);
        return;
    }

    vec4 p0 = srcData[base + 0];
    vec4 p1 = srcData[base + 1];
    vec4 p2 = srcData[base + 2];
    vec4 p3 = srcData[base + 3];

    float alive = p1.w;
    if (alive <= 0.5) {
        writeParticle(base, vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));
        return;
    }

    float dtScale = max(uDeltaTime, 0.0001) * 60.0;
    float kind = p3.x;

    if (kind == KIND_PROJECTILE) {
        p1.x += 1.0;
        if (p1.x >= p1.y) {
            writeParticle(base, vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));
            return;
        }
        vec2 direction = normalize(vec2(p0.z, p0.w));
        p0.xy += direction * uProjectileSpeed * dtScale;
    } else if (kind == KIND_MAP_ITEM_RETURN) {
        p1.x += 1.0;
        if (p1.x >= p1.y) {
            writeParticle(base, vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));
            return;
        }
        vec2 target = vec2(p0.z, p0.w);
        vec2 toTarget = target - p0.xy;
        float dist = length(toTarget);
        if (dist > uMapItemReturnMinTravelDist) {
            vec2 direction = toTarget / max(dist, 0.0001);
            p0.xy += direction * uMapItemReturnSpeed * dtScale;
        }
    } else if (kind == KIND_DUST) {
        p1.x += 1.0;
        if (p1.x >= p1.y) {
            writeParticle(base, vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));
            return;
        }
        // Keep dust anchored and make it bloom slightly so it remains visible.
        p1.z = min(p1.z + (0.5 * dtScale), 40.0);
    } else if (kind == KIND_FIREWORK_TAIL) {
        float frameStep = max(1.0, floor(dtScale + 0.5));
        p1.x += frameStep;
        float launchDelayFrames = max(p3.w, 0.0);
        if (p1.x >= launchDelayFrames) {
            float activeFrame = max(p1.x - launchDelayFrames, 0.0);
            float activeLifetime = max(p1.y - launchDelayFrames, 1.0);
            float lifeProgress = clamp(activeFrame / activeLifetime, 0.0, 1.0);
            float easeOutVelocityScale = pow(1.0 - lifeProgress, 2.0);
            float lift = max(floor((18.0 * easeOutVelocityScale * dtScale) + 0.5), 0.0);
            p0.y -= lift;
            float maxTailSizePx = clamp(p3.z, 6.0, 32.0);
            float growthFactor = 1.0 + (0.05 * dtScale);
            float grownSize = ceil(p1.z * growthFactor);
            p1.z = min(max(p1.z + 1.0, grownSize), maxTailSizePx);

            float minX = uViewPortRect.x;
            float maxX = max((uViewPortRect.x + uViewPortRect.z) - p1.z, minX);
            float maxY = max((uViewPortRect.y + uViewPortRect.w) - p1.z, uViewPortRect.y);
            float normalizedSeed = fract((float(particleIndex) * 0.131) + (p3.z * 0.017));
            float apexCenterY = uViewPortRect.y + (uViewPortRect.w * 0.48);
            float apexVariancePx = uViewPortRect.w * 0.14;
            float targetMinY = apexCenterY + ((normalizedSeed * 2.0 - 1.0) * apexVariancePx);
            float minY = clamp(targetMinY, uViewPortRect.y, maxY);
            p0.x = clamp(p0.x, minX, maxX);
            p0.y = clamp(p0.y, minY, maxY);
            bool hasReachedApex = p0.y <= (minY + 0.5);
            bool hasSettledAscent = lift <= 1.0 && activeFrame >= (activeLifetime * 0.35);
            if (hasReachedApex || hasSettledAscent) {
                float lingerFrames = 14.0 + floor(mod((float(particleIndex) * 13.0) + p3.z + p3.w, 13.0));
                float expirationStartFrame = max(p1.y - lingerFrames, 0.0);
                p1.x = max(p1.x, expirationStartFrame);
            }
        }
        if (p1.x >= p1.y) {
            writeParticle(base, vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));
            return;
        }
    } else if (kind == KIND_FIREWORK_BURST) {
        float frameStep = max(1.0, floor((uBurstFrameGrowthMultiplier * dtScale * uBurstSpeedCoefficient) + 0.5));
        p1.x += frameStep;
        float launchDelayFrames = max(p3.w, 0.0);
        if (p1.x >= launchDelayFrames) {
            float activeFrame = max(p1.x - launchDelayFrames, 0.0);
            float activeLifetime = max(p1.y - launchDelayFrames, 1.0);
            float particleId = float(particleIndex);
            float speedVariance = 0.65 + (mod((particleId * 31.0) + p3.z, 70.0) / 100.0);
            float travelFrames = max(6.0, floor((((activeLifetime * 0.65) / max(speedVariance, 0.0001)) + 0.5)));
            if (activeFrame <= travelFrames) {
                float launchProgress = clamp(activeFrame / travelFrames, 0.0, 1.0);
                float easedProgress = 1.0 - pow(1.0 - launchProgress, 3.0);
                float maxTravelDistance = max(p3.z, 8.0);
                float distance = maxTravelDistance * easedProgress;
                float angle = mod(particleId * 137.0, 360.0);
                float angleRadians = radians(angle);
                vec2 origin = vec2(p0.z, p0.w);
                vec2 offset = vec2(cos(angleRadians), sin(angleRadians)) * distance;
                p0.xy = origin + offset;
            } else if (p1.x <= p1.y) {
                p0.y += uGravity * dtScale * uBurstSpeedCoefficient;
            }
        }

        float viewMinX = uViewPortRect.x;
        float viewMaxX = uViewPortRect.x + uViewPortRect.z;
        float viewMinY = uViewPortRect.y;
        float viewMaxY = uViewPortRect.y + uViewPortRect.w;
        float particleSize = max(p1.z, 1.0);
        bool isVisible =
            (p0.x + particleSize) >= viewMinX &&
            p0.x <= viewMaxX &&
            (p0.y + particleSize) >= viewMinY &&
            p0.y <= viewMaxY;
        if (p1.x >= p1.y || !isVisible) {
            writeParticle(base, vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));
            return;
        }
    } else {
        p1.x += 1.0;
        if (p1.x >= p1.y) {
            writeParticle(base, vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));
            return;
        }
        p0.w += uGravity * dtScale;
        p0.xy += p0.zw * dtScale;
    }

    writeParticle(base, p0, p1, p2, p3);
}
