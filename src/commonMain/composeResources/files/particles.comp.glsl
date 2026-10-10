#version 310 es

layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

layout(std430, binding = 0) readonly buffer SourceBuffer {
    vec4 sourceData[];
};
layout(std430, binding = 1) writeonly buffer DestinationBuffer {
    vec4 destinationData[];
};
layout(std430, binding = 2) readonly buffer SpawnBuffer {
    vec4 spawnData[];
};
layout(std430, binding = 3) buffer CollisionSignalBuffer {
    uint projectileHitCount;
    uint projectileHitXBits;
    uint projectileHitYBits;
    uint projectileHitSizeBits;
};

uniform float uDeltaTimeSeconds;
uniform float uGravity;
uniform float uTickRate;
uniform float uSimulationSpeed;
uniform float uGravityBoost;
uniform float uLifetimeDecay;
uniform float uViewportX;
uniform float uViewportY;
uniform float uViewportWidth;
uniform float uViewportHeight;
uniform float uPlayerX;
uniform float uPlayerY;
uniform float uPlayerWidth;
uniform float uPlayerHeight;
uniform int uMaxParticles;

const float blackHoleCoreVisibilityDelay = 60.0;

struct AttractionResult {
    float consumedBySomeone;
    float consumedMassTotal;
    float consumedAttractionTotal;
    float consumedAreaTotal;
};

bool isAttractionCapableKind(float kind) {
    return kind > 7.5 && kind <= 10.5;
}

bool isAttractionVisible(float kind, float age) {
    if (kind > 8.5 && kind <= 9.5) {
        return age >= blackHoleCoreVisibilityDelay;
    }
    return true;
}

float attractionMassFor(float kind, vec4 typeInfo, vec4 kindDataA) {
    if (kind > 8.5 && kind <= 9.5) {
        return max(typeInfo.z, 0.0001);
    }
    return max(kindDataA.x, 0.0001);
}

float attractionValueFor(float kind, vec4 typeInfo, vec4 kindDataA) {
    if (kind > 8.5 && kind <= 9.5) {
        return max(typeInfo.w, 0.0);
    }
    return max(kindDataA.y, 0.0);
}

AttractionResult applyGenericAttraction(
    uint selfIndex,
    uint maxParticleCount,
    float elapsedTicks,
    float selfMass,
    float selfAttraction,
    float selfRadius,
    float selfMaxVelocity,
    inout vec4 positionVelocity
) {
    AttractionResult result;
    result.consumedBySomeone = 0.0;
    result.consumedMassTotal = 0.0;
    result.consumedAttractionTotal = 0.0;
    result.consumedAreaTotal = 0.0;
    for (uint otherIndex = 0u; otherIndex < maxParticleCount; otherIndex = otherIndex + 1u) {
        if (otherIndex == selfIndex) {
            continue;
        }
        uint otherBase = otherIndex * 6u;
        vec4 otherLifecycle = sourceData[otherBase + 1u];
        if (otherLifecycle.w <= 0.5) {
            continue;
        }
        vec4 otherTypeInfo = sourceData[otherBase + 3u];
        float otherKind = otherTypeInfo.x;
        if (!isAttractionCapableKind(otherKind)) {
            continue;
        }
        if (!isAttractionVisible(otherKind, otherLifecycle.x)) {
            continue;
        }
        vec4 otherKindDataA = sourceData[otherBase + 4u];
        float otherMass = attractionMassFor(otherKind, otherTypeInfo, otherKindDataA);
        float otherAttraction = attractionValueFor(otherKind, otherTypeInfo, otherKindDataA);
        if (abs(selfAttraction - otherAttraction) <= 0.0001) {
            continue;
        }
        vec4 otherPositionVelocity = sourceData[otherBase + 0u];
        float otherRadius = max(otherLifecycle.z * 0.5, 1.0);
        float deltaX = otherPositionVelocity.x - positionVelocity.x;
        float deltaY = otherPositionVelocity.y - positionVelocity.y;
        float distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
        if (distance <= 0.0) {
            continue;
        }
        float combinedRadius = selfRadius + otherRadius;
        if (distance <= combinedRadius) {
            if (otherMass > selfMass) {
                result.consumedBySomeone = 1.0;
            } else if (selfMass > otherMass) {
                result.consumedMassTotal = result.consumedMassTotal + otherMass;
                result.consumedAttractionTotal = result.consumedAttractionTotal + otherAttraction;
                result.consumedAreaTotal = result.consumedAreaTotal + (otherRadius * otherRadius);
            }
        } else if (selfAttraction < otherAttraction) {
            if (abs(deltaX) <= otherAttraction && abs(deltaY) <= otherAttraction) {
                float pullMagnitude = otherAttraction * 0.5;
                positionVelocity.z = positionVelocity.z + ((deltaX / distance) * pullMagnitude * elapsedTicks);
                positionVelocity.w = positionVelocity.w + ((deltaY / distance) * pullMagnitude * elapsedTicks);
                if (selfMaxVelocity > 0.0) {
                    float pulledSpeed = sqrt((positionVelocity.z * positionVelocity.z) + (positionVelocity.w * positionVelocity.w));
                    if (pulledSpeed > selfMaxVelocity) {
                        float pulledSpeedScale = selfMaxVelocity / pulledSpeed;
                        positionVelocity.z = positionVelocity.z * pulledSpeedScale;
                        positionVelocity.w = positionVelocity.w * pulledSpeedScale;
                    }
                }
            }
        }
    }
    return result;
}

void main() {
    uint index = gl_GlobalInvocationID.x;
    uint maxParticleCount = uint(uMaxParticles);
    if (index >= maxParticleCount) {
        return;
    }
    uint base = index * 6u;
    vec4 positionVelocity = sourceData[base + 0u];
    vec4 lifecycle = sourceData[base + 1u];
    vec4 colorRgba = sourceData[base + 2u];
    vec4 typeInfo = sourceData[base + 3u];
    vec4 kindDataA = sourceData[base + 4u];
    vec4 kindDataB = sourceData[base + 5u];
    vec4 spawnPositionVelocity = spawnData[base + 0u];
    vec4 spawnLifecycle = spawnData[base + 1u];
    vec4 spawnColorRgba = spawnData[base + 2u];
    vec4 spawnTypeInfo = spawnData[base + 3u];
    vec4 spawnKindDataA = spawnData[base + 4u];
    vec4 spawnKindDataB = spawnData[base + 5u];
    if (spawnLifecycle.w > 0.5) {
        float spawnKind = spawnTypeInfo.x;
        bool wasGobblerOrBlackHoleExplosion = (typeInfo.x > 7.5 && typeInfo.x <= 8.5) || (typeInfo.x > 9.5 && typeInfo.x <= 10.5);
        bool wasStressTest = typeInfo.x > 10.5 && typeInfo.x <= 11.5;
        bool wasConsumed = (wasGobblerOrBlackHoleExplosion && typeInfo.w > 0.5) ||
                (wasStressTest && typeInfo.w >= 4.0);
        bool isFreshSpawnInstance = spawnLifecycle.x < 1.0;
        bool isProjectileSpawn = (spawnKind > 1.5 && spawnKind <= 2.5) || (spawnKind > 11.5 && spawnKind <= 12.5);
        bool isBubbleSpawn = spawnKind > 12.5 && spawnKind <= 13.5;
        bool preserveGpuState = (spawnKind > 1.5 && lifecycle.w > 0.5 && !(isProjectileSpawn && isFreshSpawnInstance) && !isBubbleSpawn) ||
                (wasConsumed && !isFreshSpawnInstance);
        if (!preserveGpuState) {
            positionVelocity = spawnPositionVelocity;
            lifecycle = spawnLifecycle;
            colorRgba = spawnColorRgba;
            typeInfo = spawnTypeInfo;
            kindDataA = spawnKindDataA;
            kindDataB = spawnKindDataB;
        }
    }
    if (lifecycle.w > 0.5) {
        float particleKind = typeInfo.x;
        float stepSeconds = uDeltaTimeSeconds * uSimulationSpeed;
        float stepLifetime = uDeltaTimeSeconds * uTickRate * uLifetimeDecay;
        float elapsedTicks = clamp(uDeltaTimeSeconds * uTickRate, 0.5, 2.0);
        if (particleKind > 4.5 && particleKind <= 5.5) {
            const float fireworkBurstYAcceleration = 0.05;
            const float fireworkBurstMass = 0.02;
            const float fireworkBurstDrag = 0.02;
            const float fireworkBurstMaxVelocity = 10.0;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                positionVelocity.w = positionVelocity.w + (fireworkBurstYAcceleration * elapsedTicks);
                float gravityScale = min(1.0, fireworkBurstMass);
                positionVelocity.w = positionVelocity.w + (uGravity * gravityScale * elapsedTicks);
                float dragFactor = max(0.0, 1.0 - (fireworkBurstDrag * elapsedTicks));
                positionVelocity.z = positionVelocity.z * dragFactor;
                positionVelocity.w = positionVelocity.w * dragFactor;
                positionVelocity.z = clamp(positionVelocity.z, -fireworkBurstMaxVelocity, fireworkBurstMaxVelocity);
                positionVelocity.w = clamp(positionVelocity.w, -fireworkBurstMaxVelocity, fireworkBurstMaxVelocity);
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
            }
        } else if (particleKind > 3.5 && particleKind <= 4.5) {
            const float fireworkTailMass = 0.02;
            lifecycle.x = lifecycle.x + elapsedTicks;
            float tailDelay = max(typeInfo.w, 0.0);
            float tailTotalLifetime = lifecycle.y + tailDelay;
            if (lifecycle.x >= tailTotalLifetime) {
                lifecycle.w = 0.0;
            } else if (lifecycle.x >= tailDelay) {
                float gravityScale = min(1.0, fireworkTailMass);
                positionVelocity.w = positionVelocity.w + (uGravity * gravityScale * elapsedTicks);
                float maxYVelocity = max(typeInfo.z, 1.0);
                positionVelocity.w = clamp(positionVelocity.w, -maxYVelocity, maxYVelocity);
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
            }
        } else if (particleKind > 2.5 && particleKind <= 3.5) {
            const float itemReturnThrust = 0.16;
            const float itemReturnMaxSpeed = 5.25;
            const float itemReturnVelocityHeadroom = 0.2;
            const float itemReturnMass = 0.15;
            const float itemReturnDrag = 0.001;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                float unitX = typeInfo.z;
                float unitY = typeInfo.w;
                float maxVelocityX = (abs(unitX) * itemReturnMaxSpeed) + itemReturnVelocityHeadroom;
                float maxVelocityY = (abs(unitY) * itemReturnMaxSpeed) + itemReturnVelocityHeadroom;
                positionVelocity.z = positionVelocity.z + (unitX * itemReturnThrust * elapsedTicks);
                positionVelocity.w = positionVelocity.w + (unitY * itemReturnThrust * elapsedTicks);
                float gravityScale = min(1.0, itemReturnMass);
                positionVelocity.w = positionVelocity.w + (uGravity * gravityScale * elapsedTicks);
                float dragFactor = max(0.0, 1.0 - (itemReturnDrag * elapsedTicks));
                positionVelocity.z = positionVelocity.z * dragFactor;
                positionVelocity.w = positionVelocity.w * dragFactor;
                positionVelocity.z = clamp(positionVelocity.z, -maxVelocityX, maxVelocityX);
                positionVelocity.w = clamp(positionVelocity.w, -maxVelocityY, maxVelocityY);
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
            }
        } else if ((particleKind > 1.5 && particleKind <= 2.5) || (particleKind > 11.5 && particleKind <= 12.5)) {
            const float projectileThrust = 0.16;
            const float projectileMaxSpeed = 5.25;
            const float projectileVelocityHeadroom = 0.2;
            const float projectileMass = 0.15;
            const float projectileDrag = 0.001;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                float unitX = typeInfo.z;
                float unitY = typeInfo.w;
                float maxVelocityX = (abs(unitX) * projectileMaxSpeed) + projectileVelocityHeadroom;
                float maxVelocityY = (abs(unitY) * projectileMaxSpeed) + projectileVelocityHeadroom;
                positionVelocity.z = positionVelocity.z + (unitX * projectileThrust * elapsedTicks);
                positionVelocity.w = positionVelocity.w + (unitY * projectileThrust * elapsedTicks);
                float gravityScale = min(1.0, projectileMass);
                positionVelocity.w = positionVelocity.w + (uGravity * gravityScale * elapsedTicks);
                float dragFactor = max(0.0, 1.0 - (projectileDrag * elapsedTicks));
                positionVelocity.z = positionVelocity.z * dragFactor;
                positionVelocity.w = positionVelocity.w * dragFactor;
                positionVelocity.z = clamp(positionVelocity.z, -maxVelocityX, maxVelocityX);
                positionVelocity.w = clamp(positionVelocity.w, -maxVelocityY, maxVelocityY);
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
                float playerMinX = uPlayerX;
                float playerMinY = uPlayerY;
                float playerMaxX = playerMinX + max(uPlayerWidth, 1.0);
                float playerMaxY = playerMinY + max(uPlayerHeight, 1.0);
                float nearestX = clamp(positionVelocity.x, playerMinX, playerMaxX);
                float nearestY = clamp(positionVelocity.y, playerMinY, playerMaxY);
                float deltaX = positionVelocity.x - nearestX;
                float deltaY = positionVelocity.y - nearestY;
                float projectileRadius = max(lifecycle.z * 0.5, 1.0);
                if ((deltaX * deltaX) + (deltaY * deltaY) <= (projectileRadius * projectileRadius)) {
                    lifecycle.w = 0.0;
                    uint previousHitCount = atomicAdd(projectileHitCount, 1u);
                    if (previousHitCount == 0u) {
                        atomicExchange(projectileHitXBits, floatBitsToUint(positionVelocity.x));
                        atomicExchange(projectileHitYBits, floatBitsToUint(positionVelocity.y));
                        atomicExchange(projectileHitSizeBits, floatBitsToUint(max(lifecycle.z, 1.0)));
                    }
                }
            }
        } else if (particleKind > 5.5 && particleKind <= 6.5) {
            const float collisionMassScale = 0.04;
            const float collisionDrag = 0.01;
            const float collisionMaxVelocity = 10.0;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                float radius = lifecycle.z * 0.5;
                float mass = radius * collisionMassScale;
                float gravityScale = min(1.0, mass);
                positionVelocity.w = positionVelocity.w + (uGravity * gravityScale * elapsedTicks);
                float dragFactor = max(0.0, 1.0 - (collisionDrag * elapsedTicks));
                positionVelocity.z = positionVelocity.z * dragFactor;
                positionVelocity.w = positionVelocity.w * dragFactor;
                positionVelocity.z = clamp(positionVelocity.z, -collisionMaxVelocity, collisionMaxVelocity);
                positionVelocity.w = clamp(positionVelocity.w, -collisionMaxVelocity, collisionMaxVelocity);
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
            }
        } else if (particleKind > 6.5 && particleKind <= 7.5) {
            const float collidingBitsMass = 0.12;
            const float collidingBitsRestitution = 0.6;
            const float collidingBitsMaxVelocity = 10.0;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                float gravityScale = min(1.0, collidingBitsMass);
                positionVelocity.w = positionVelocity.w + (uGravity * gravityScale * elapsedTicks);
                positionVelocity.z = clamp(positionVelocity.z, -collidingBitsMaxVelocity, collidingBitsMaxVelocity);
                positionVelocity.w = clamp(positionVelocity.w, -collidingBitsMaxVelocity, collidingBitsMaxVelocity);
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
                float halfSize = max(lifecycle.z * 0.5, 1.0);
                float massDamping = 1.0 / (1.0 + collidingBitsMass);
                float boundaryRestitutionFactor = collidingBitsRestitution * massDamping;
                float minX = uViewportX + halfSize;
                float maxX = uViewportX + uViewportWidth - halfSize;
                float minY = uViewportY + halfSize;
                float maxY = uViewportY + uViewportHeight - halfSize;
                if (positionVelocity.x < minX) {
                    positionVelocity.x = minX + (minX - positionVelocity.x);
                    positionVelocity.z = -positionVelocity.z * boundaryRestitutionFactor;
                } else if (positionVelocity.x > maxX) {
                    positionVelocity.x = maxX - (positionVelocity.x - maxX);
                    positionVelocity.z = -positionVelocity.z * boundaryRestitutionFactor;
                }
                if (positionVelocity.y < minY) {
                    positionVelocity.y = minY + (minY - positionVelocity.y);
                    positionVelocity.w = -positionVelocity.w * boundaryRestitutionFactor;
                } else if (positionVelocity.y > maxY) {
                    positionVelocity.y = maxY - (positionVelocity.y - maxY);
                    positionVelocity.w = -positionVelocity.w * boundaryRestitutionFactor;
                }
                float myRadius = halfSize;
                for (uint otherIndex = 0u; otherIndex < maxParticleCount; otherIndex = otherIndex + 1u) {
                    if (otherIndex == index) {
                        continue;
                    }
                    uint otherBase = otherIndex * 6u;
                    vec4 otherLifecycle = sourceData[otherBase + 1u];
                    if (otherLifecycle.w <= 0.5) {
                        continue;
                    }
                    vec4 otherTypeInfo = sourceData[otherBase + 3u];
                    if (otherTypeInfo.x <= 6.5 || otherTypeInfo.x > 7.5) {
                        continue;
                    }
                    vec4 otherPositionVelocity = sourceData[otherBase + 0u];
                    float otherRadius = max(otherLifecycle.z * 0.5, 1.0);
                    float deltaX = otherPositionVelocity.x - positionVelocity.x;
                    float deltaY = otherPositionVelocity.y - positionVelocity.y;
                    float distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
                    float minimumDistance = myRadius + otherRadius;
                    if (distance > 0.0 && distance < minimumDistance) {
                        float normalX = deltaX / distance;
                        float normalY = deltaY / distance;
                        float overlap = minimumDistance - distance;
                        positionVelocity.x = positionVelocity.x - (normalX * overlap * 0.5);
                        positionVelocity.y = positionVelocity.y - (normalY * overlap * 0.5);
                        float relativeVelocityX = positionVelocity.z - otherPositionVelocity.z;
                        float relativeVelocityY = positionVelocity.w - otherPositionVelocity.w;
                        float dotProduct = (normalX * relativeVelocityX) + (normalY * relativeVelocityY);
                        positionVelocity.z = positionVelocity.z - (dotProduct * normalX * (1.0 + collidingBitsRestitution));
                        positionVelocity.w = positionVelocity.w - (dotProduct * normalY * (1.0 + collidingBitsRestitution));
                    }
                }
            }
        } else if (particleKind > 7.5 && particleKind <= 8.5) {
            const float gobblerDrag = 0.002;
            const float gobblerMaxVelocity = 6.0;
            const float gobblerRestitution = 1.0;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                float dragFactor = max(0.0, 1.0 - (gobblerDrag * elapsedTicks));
                positionVelocity.z = positionVelocity.z * dragFactor;
                positionVelocity.w = positionVelocity.w * dragFactor;
                positionVelocity.z = clamp(positionVelocity.z, -gobblerMaxVelocity, gobblerMaxVelocity);
                positionVelocity.w = clamp(positionVelocity.w, -gobblerMaxVelocity, gobblerMaxVelocity);
                float radius = max(lifecycle.z * 0.5, 1.0);
                float mass = attractionMassFor(particleKind, typeInfo, kindDataA);
                float attraction = attractionValueFor(particleKind, typeInfo, kindDataA);
                AttractionResult attractionResult = applyGenericAttraction(
                        index, maxParticleCount, elapsedTicks, mass, attraction, radius, gobblerMaxVelocity, positionVelocity
                );
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
                float massDamping = 1.0 / (1.0 + mass);
                float boundaryRestitutionFactor = gobblerRestitution * massDamping;
                float minX = uViewportX + radius;
                float maxX = uViewportX + uViewportWidth - radius;
                float minY = uViewportY + radius;
                float maxY = uViewportY + uViewportHeight - radius;
                if (positionVelocity.x < minX) {
                    positionVelocity.x = minX + (minX - positionVelocity.x);
                    positionVelocity.z = -positionVelocity.z * boundaryRestitutionFactor;
                } else if (positionVelocity.x > maxX) {
                    positionVelocity.x = maxX - (positionVelocity.x - maxX);
                    positionVelocity.z = -positionVelocity.z * boundaryRestitutionFactor;
                }
                if (positionVelocity.y < minY) {
                    positionVelocity.y = minY + (minY - positionVelocity.y);
                    positionVelocity.w = -positionVelocity.w * boundaryRestitutionFactor;
                } else if (positionVelocity.y > maxY) {
                    positionVelocity.y = maxY - (positionVelocity.y - maxY);
                    positionVelocity.w = -positionVelocity.w * boundaryRestitutionFactor;
                }
                if (attractionResult.consumedBySomeone > 0.5) {
                    lifecycle.w = 0.0;
                    typeInfo.w = 1.0;
                } else if (attractionResult.consumedAreaTotal > 0.0) {
                    radius = sqrt((radius * radius) + attractionResult.consumedAreaTotal);
                    lifecycle.z = radius * 2.0;
                    kindDataA.x = mass + attractionResult.consumedMassTotal;
                    kindDataA.y = attraction + attractionResult.consumedAttractionTotal;
                }
            }
        } else if (particleKind > 8.5 && particleKind <= 9.5) {
            const float blackHoleCoreMaxVelocity = 10.0;
            const float blackHoleCoreRestitution = 0.0;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                positionVelocity.z = clamp(positionVelocity.z, -blackHoleCoreMaxVelocity, blackHoleCoreMaxVelocity);
                positionVelocity.w = clamp(positionVelocity.w, -blackHoleCoreMaxVelocity, blackHoleCoreMaxVelocity);
                float radius = max(lifecycle.z * 0.5, 1.0);
                float mass = attractionMassFor(particleKind, typeInfo, kindDataA);
                float attraction = attractionValueFor(particleKind, typeInfo, kindDataA);
                AttractionResult attractionResult;
                attractionResult.consumedBySomeone = 0.0;
                attractionResult.consumedMassTotal = 0.0;
                attractionResult.consumedAttractionTotal = 0.0;
                attractionResult.consumedAreaTotal = 0.0;
                if (isAttractionVisible(particleKind, lifecycle.x)) {
                    attractionResult = applyGenericAttraction(
                            index, maxParticleCount, elapsedTicks, mass, attraction, radius, blackHoleCoreMaxVelocity, positionVelocity
                    );
                }
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
                float massDamping = 1.0 / (1.0 + mass);
                float boundaryRestitutionFactor = blackHoleCoreRestitution * massDamping;
                float minX = uViewportX + radius;
                float maxX = uViewportX + uViewportWidth - radius;
                float minY = uViewportY + radius;
                float maxY = uViewportY + uViewportHeight - radius;
                if (positionVelocity.x < minX) {
                    positionVelocity.x = minX + (minX - positionVelocity.x);
                    positionVelocity.z = -positionVelocity.z * boundaryRestitutionFactor;
                } else if (positionVelocity.x > maxX) {
                    positionVelocity.x = maxX - (positionVelocity.x - maxX);
                    positionVelocity.z = -positionVelocity.z * boundaryRestitutionFactor;
                }
                if (positionVelocity.y < minY) {
                    positionVelocity.y = minY + (minY - positionVelocity.y);
                    positionVelocity.w = -positionVelocity.w * boundaryRestitutionFactor;
                } else if (positionVelocity.y > maxY) {
                    positionVelocity.y = maxY - (positionVelocity.y - maxY);
                    positionVelocity.w = -positionVelocity.w * boundaryRestitutionFactor;
                }
                if (attractionResult.consumedBySomeone > 0.5) {
                    lifecycle.w = 0.0;
                } else if (attractionResult.consumedAreaTotal > 0.0) {
                    radius = sqrt((radius * radius) + attractionResult.consumedAreaTotal);
                    lifecycle.z = radius * 2.0;
                    typeInfo.z = mass + attractionResult.consumedMassTotal;
                    typeInfo.w = attraction + attractionResult.consumedAttractionTotal;
                }
            }
        } else if (particleKind > 9.5 && particleKind <= 10.5) {
            const float blackHoleExplosionDrag = 0.02;
            const float blackHoleExplosionMaxVelocity = 10.0;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                float dragFactor = max(0.0, 1.0 - (blackHoleExplosionDrag * elapsedTicks));
                positionVelocity.z = positionVelocity.z * dragFactor;
                positionVelocity.w = positionVelocity.w * dragFactor;
                positionVelocity.z = clamp(positionVelocity.z, -blackHoleExplosionMaxVelocity, blackHoleExplosionMaxVelocity);
                positionVelocity.w = clamp(positionVelocity.w, -blackHoleExplosionMaxVelocity, blackHoleExplosionMaxVelocity);
                float radius = max(lifecycle.z * 0.5, 1.0);
                float mass = attractionMassFor(particleKind, typeInfo, kindDataA);
                float attraction = attractionValueFor(particleKind, typeInfo, kindDataA);
                AttractionResult attractionResult = applyGenericAttraction(
                        index, maxParticleCount, elapsedTicks, mass, attraction, radius, blackHoleExplosionMaxVelocity, positionVelocity
                );
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
                if (attractionResult.consumedBySomeone > 0.5) {
                    lifecycle.w = 0.0;
                    typeInfo.w = 1.0;
                } else if (attractionResult.consumedAreaTotal > 0.0) {
                    float newRadius = sqrt((radius * radius) + attractionResult.consumedAreaTotal);
                    lifecycle.z = newRadius * 2.0;
                    kindDataA.x = mass + attractionResult.consumedMassTotal;
                    kindDataA.y = attraction + attractionResult.consumedAttractionTotal;
                }
            }
        } else if (particleKind > 0.5 && particleKind <= 1.5) {
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
            }
        } else if (particleKind > 12.5 && particleKind <= 13.5) {
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
            }
        } else if (particleKind > 10.5 && particleKind <= 11.5) {
            const float stressTestAttractionPerMass = 10.0;
            float myDelay = kindDataB.x;
            lifecycle.x = lifecycle.x + elapsedTicks;
            if (lifecycle.x >= lifecycle.y + myDelay) {
                lifecycle.w = 0.0;
            } else if (lifecycle.x < myDelay) {
                // still within spawn delay: frozen, not yet eligible to attract/merge/be attracted to
            } else {
                float myMass = abs(typeInfo.z);
                float gravityScale = min(1.0, myMass);
                positionVelocity.w = positionVelocity.w + (uGravity * gravityScale * elapsedTicks);
                positionVelocity.z = positionVelocity.z + (kindDataA.x * elapsedTicks);
                positionVelocity.w = positionVelocity.w + (kindDataA.y * elapsedTicks);
                float dragFactor = max(0.0, 1.0 - (kindDataA.z * elapsedTicks));
                positionVelocity.z = positionVelocity.z * dragFactor;
                positionVelocity.w = positionVelocity.w * dragFactor;
                float restitutionEncoding = typeInfo.w - ((typeInfo.w >= 4.0) ? 4.0 : 0.0);
                bool viewportBound = restitutionEncoding >= 2.0;
                float myRestitution = restitutionEncoding - (viewportBound ? 2.0 : 0.0);
                bool canCollide = typeInfo.z >= 0.0;
                float myRadius = max(lifecycle.z * 0.5, 1.0);
                float myAttraction = myMass * stressTestAttractionPerMass;
                bool consumedBySomeone = false;
                float consumedMassTotal = 0.0;
                float consumedAreaTotal = 0.0;
                for (uint otherIndex = 0u; otherIndex < maxParticleCount; otherIndex = otherIndex + 1u) {
                    if (otherIndex == index) {
                        continue;
                    }
                    uint otherBase = otherIndex * 6u;
                    vec4 otherLifecycle = sourceData[otherBase + 1u];
                    if (otherLifecycle.w <= 0.5) {
                        continue;
                    }
                    vec4 otherTypeInfo = sourceData[otherBase + 3u];
                    if (otherTypeInfo.x <= 10.5 || otherTypeInfo.x > 11.5) {
                        continue;
                    }
                    vec4 otherKindDataB = sourceData[otherBase + 5u];
                    if (otherLifecycle.x < otherKindDataB.x) {
                        continue;
                    }
                    bool otherCanCollide = otherTypeInfo.z >= 0.0;
                    float otherMass = abs(otherTypeInfo.z);
                    float otherAttraction = otherMass * stressTestAttractionPerMass;
                    vec4 otherPositionVelocity = sourceData[otherBase + 0u];
                    float otherRadius = max(otherLifecycle.z * 0.5, 1.0);
                    float deltaX = otherPositionVelocity.x - positionVelocity.x;
                    float deltaY = otherPositionVelocity.y - positionVelocity.y;
                    float distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
                    if (distance <= 0.0) {
                        continue;
                    }
                    float combinedRadius = myRadius + otherRadius;
                    bool attractionsDiffer = abs(myAttraction - otherAttraction) > 0.0001;
                    bool suppressedByAttraction = false;
                    if (attractionsDiffer) {
                        float attractionRange = max(myAttraction, otherAttraction);
                        if (abs(deltaX) <= attractionRange && abs(deltaY) <= attractionRange) {
                            suppressedByAttraction = true;
                        }
                    }
                    if (myAttraction > 0.0 && otherAttraction > 0.0 && attractionsDiffer) {
                        if (distance < combinedRadius) {
                            if (otherMass > myMass) {
                                consumedBySomeone = true;
                            } else if (myMass > otherMass) {
                                consumedMassTotal = consumedMassTotal + otherMass;
                                consumedAreaTotal = consumedAreaTotal + (otherRadius * otherRadius);
                            }
                        } else if (myAttraction < otherAttraction) {
                            if (abs(deltaX) <= otherAttraction && abs(deltaY) <= otherAttraction) {
                                float pullMagnitude = otherAttraction * 0.5;
                                positionVelocity.z = positionVelocity.z + ((deltaX / distance) * pullMagnitude * elapsedTicks);
                                positionVelocity.w = positionVelocity.w + ((deltaY / distance) * pullMagnitude * elapsedTicks);
                            }
                        }
                    }
                    if (canCollide && otherCanCollide && !suppressedByAttraction && distance < combinedRadius) {
                        float totalMass = myMass + otherMass;
                        if (totalMass > 0.0) {
                            float normalX = deltaX / distance;
                            float normalY = deltaY / distance;
                            float overlap = combinedRadius - distance;
                            positionVelocity.x = positionVelocity.x - (normalX * overlap * (otherMass / totalMass));
                            positionVelocity.y = positionVelocity.y - (normalY * overlap * (otherMass / totalMass));
                            float relativeVelocityX = positionVelocity.z - otherPositionVelocity.z;
                            float relativeVelocityY = positionVelocity.w - otherPositionVelocity.w;
                            float impulse = 2.0 * ((normalX * relativeVelocityX) + (normalY * relativeVelocityY)) / totalMass;
                            float otherRestitutionEncoding = otherTypeInfo.w - ((otherTypeInfo.w >= 4.0) ? 4.0 : 0.0);
                            bool otherViewportBound = otherRestitutionEncoding >= 2.0;
                            float otherRestitution = otherRestitutionEncoding - (otherViewportBound ? 2.0 : 0.0);
                            float restitution = min(myRestitution, otherRestitution);
                            positionVelocity.z = positionVelocity.z - (impulse * otherMass * normalX * (1.0 + restitution));
                            positionVelocity.w = positionVelocity.w - (impulse * otherMass * normalY * (1.0 + restitution));
                        }
                    }
                }
                float maxVelocity = kindDataA.w;
                if (maxVelocity > 0.0) {
                    float speed = sqrt((positionVelocity.z * positionVelocity.z) + (positionVelocity.w * positionVelocity.w));
                    if (speed > maxVelocity) {
                        float speedScale = maxVelocity / speed;
                        positionVelocity.z = positionVelocity.z * speedScale;
                        positionVelocity.w = positionVelocity.w * speedScale;
                    }
                }
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
                if (viewportBound) {
                    float halfSize = max(lifecycle.z * 0.5, 1.0);
                    float massDamping = 1.0 / (1.0 + myMass);
                    float boundaryRestitutionFactor = myRestitution * massDamping;
                    float minX = uViewportX + halfSize;
                    float maxX = uViewportX + uViewportWidth - halfSize;
                    float minY = uViewportY + halfSize;
                    float maxY = uViewportY + uViewportHeight - halfSize;
                    if (positionVelocity.x < minX) {
                        positionVelocity.x = minX + (minX - positionVelocity.x);
                        positionVelocity.z = -positionVelocity.z * boundaryRestitutionFactor;
                    } else if (positionVelocity.x > maxX) {
                        positionVelocity.x = maxX - (positionVelocity.x - maxX);
                        positionVelocity.z = -positionVelocity.z * boundaryRestitutionFactor;
                    }
                    if (positionVelocity.y < minY) {
                        positionVelocity.y = minY + (minY - positionVelocity.y);
                        positionVelocity.w = -positionVelocity.w * boundaryRestitutionFactor;
                    } else if (positionVelocity.y > maxY) {
                        positionVelocity.y = maxY - (positionVelocity.y - maxY);
                        positionVelocity.w = -positionVelocity.w * boundaryRestitutionFactor;
                    }
                }
                if (consumedBySomeone) {
                    lifecycle.w = 0.0;
                    typeInfo.w = typeInfo.w + 4.0;
                } else if (consumedMassTotal > 0.0) {
                    float newRadius = sqrt((myRadius * myRadius) + consumedAreaTotal);
                    float newMass = myMass + consumedMassTotal;
                    lifecycle.z = newRadius * 2.0;
                    typeInfo.z = canCollide ? newMass : -newMass;
                }
            }
        } else {
            lifecycle.x = lifecycle.x + stepLifetime;
            if (lifecycle.x >= lifecycle.y) {
                lifecycle.w = 0.0;
            } else {
                positionVelocity.x = positionVelocity.x + (positionVelocity.z * stepSeconds);
                positionVelocity.y = positionVelocity.y + (positionVelocity.w * stepSeconds);
                positionVelocity.w = positionVelocity.w + (uGravity * uGravityBoost * stepSeconds);
            }
        }
    }
    destinationData[base + 0u] = positionVelocity;
    destinationData[base + 1u] = lifecycle;
    destinationData[base + 2u] = colorRgba;
    destinationData[base + 3u] = typeInfo;
    destinationData[base + 4u] = kindDataA;
    destinationData[base + 5u] = kindDataB;
}
