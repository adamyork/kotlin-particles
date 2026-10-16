#version 310 es
precision highp float;
precision highp int;

#define STAGE_SPAWN_INTEGRATE 0
#define STAGE_GRID_CLEAR 1
#define STAGE_GRID_COUNT 2
#define STAGE_GRID_SCAN 3
#define STAGE_GRID_SCATTER 4
#define STAGE_SELECT_GRAVITY_SOURCES 5
#define STAGE_GRAVITY 6
#define STAGE_ACCRETION_OWNER 7
#define STAGE_ACCRETION_APPLY 8
#define STAGE_CONTACT_BEGIN 9
#define STAGE_COUNT_CONTACTS_FROM_A 10
#define STAGE_SOLVE_CONTACTS_A_TO_B 11
#define STAGE_COUNT_CONTACTS_FROM_B 12
#define STAGE_SOLVE_CONTACTS_B_TO_A 13
#define STAGE_CONTACT_END 14
#define STAGE_FREE_SLOTS 15
#define STAGE_SPLIT 16
#define STAGE_WALL 17

#if STAGE == STAGE_GRID_SCAN
layout(local_size_x = 1) in;
#elif STAGE == STAGE_SELECT_GRAVITY_SOURCES
layout(local_size_x = 256) in;
#else
layout(local_size_x = 64) in;
#endif

layout(std430, binding = 0) buffer ParticleBuffer {
    vec4 data[];
} particles;

layout(std430, binding = 1) readonly buffer SpawnBuffer {
    vec4 data[];
} spawnParticles;

layout(std140, binding = 0) uniform SimulationUniforms {
    float deltaTime;
    float worldTime;
    float gravity;
    float mediumDensity;
    float boundaryZ;
    float boundaryDepth;
    float wallRestitution;
    float environmentFriction;
    float drag;
    float maxParticleSpeed;
    float minActiveVelocity;
    float spriteTicksPerFrame;
    float flowAmplitude;
    float flowFrequency;
    float flowWavelength;
    float maxParticles;
    float viewportX;
    float viewportY;
    float viewportWidth;
    float viewportHeight;
    float collisionPlaneThickness;
    float gravityPlaneThickness;
    float cellSize;
    float gridColumns;
    float gridRows;
    float gravitationalConstant;
    float gravitationalSoftening;
    float gravitySourceCount;
    float gravityMinimumAcceleration;
    float accretionMassRatio;
    float shatterMinimumFragmentRadius;
    float shatterMinimumFragmentCount;
    float shatterMaximumFragmentCount;
    float shatterMomentumPerFragment;
    float shatterSpreadSpeed;
    float shatterMaxVelocity;
    float contactPositionRelaxationExponent;
    float contactVelocityRelaxationExponent;
    float reservedC;
    float reservedD;
} simulation;

layout(std430, binding = 2) buffer KinematicsBuffer {
    vec4 data[];
} kinematics;

layout(std430, binding = 3) buffer GridBuffer {
    uint data[];
} grid;

layout(std430, binding = 4) buffer ScratchBuffer {
    uint data[];
} scratch;

const uint VECTORS_PER_PARTICLE = 16u;
const float PI = 3.14159265358979;
const float TWO_PI = 6.28318530717959;
const float MINIMUM_MASS = 0.000000001;
const float MAXIMUM_ANGULAR_VELOCITY = 0.5;
const uint SOLVER_ITERATIONS = 6u;
const float WALL_CONTACT_TOLERANCE = 0.5;

struct Particle {
    vec3 position;
    float angle;
    vec3 velocity;
    float angularVelocity;
    vec3 thrust;
    float density;
    float age;
    float delay;
    float lifetime;
    float alive;
    float shapeKind;
    float sizeA;
    float sizeB;
    float scaleRate;
    float maximumSizeA;
    float maximumSizeB;
    float renderKind;
    float uniqueId;
    float restitution;
    float hardness;
    float adhesion;
    float friction;
    float staticFriction;
    float rollingResistance;
    float materialReservedA;
    float materialReservedB;
    vec4 color;
    vec3 startColor;
    float initialAlpha;
    vec3 endColor;
    float endAlpha;
    vec2 origin;
    float animationFrame;
    float animationMaximumFrames;
    vec4 scratchA;
    vec4 scratchB;
    vec4 reservedA;
    vec4 reservedB;
};

float truncatedModulo(float dividend, float divisor) {
    return dividend - divisor * trunc(dividend / divisor);
}

Particle assembleParticle(
    vec4 vectorZero,
    vec4 vectorOne,
    vec4 vectorTwo,
    vec4 vectorThree,
    vec4 vectorFour,
    vec4 vectorFive,
    vec4 vectorSix,
    vec4 vectorSeven,
    vec4 vectorEight,
    vec4 vectorNine,
    vec4 vectorTen,
    vec4 vectorEleven,
    vec4 vectorTwelve,
    vec4 vectorThirteen,
    vec4 vectorFourteen,
    vec4 vectorFifteen
) {
    Particle particle;
    particle.position = vectorZero.xyz;
    particle.angle = vectorZero.w;
    particle.velocity = vectorOne.xyz;
    particle.angularVelocity = vectorOne.w;
    particle.thrust = vectorTwo.xyz;
    particle.density = vectorTwo.w;
    particle.age = vectorThree.x;
    particle.delay = vectorThree.y;
    particle.lifetime = vectorThree.z;
    particle.alive = vectorThree.w;
    particle.shapeKind = vectorFour.x;
    particle.sizeA = vectorFour.y;
    particle.sizeB = vectorFour.z;
    particle.scaleRate = vectorFour.w;
    particle.maximumSizeA = vectorFive.x;
    particle.maximumSizeB = vectorFive.y;
    particle.renderKind = vectorFive.z;
    particle.uniqueId = vectorFive.w;
    particle.restitution = vectorSix.x;
    particle.hardness = vectorSix.y;
    particle.adhesion = vectorSix.z;
    particle.friction = vectorSix.w;
    particle.staticFriction = vectorSeven.x;
    particle.rollingResistance = vectorSeven.y;
    particle.materialReservedA = vectorSeven.z;
    particle.materialReservedB = vectorSeven.w;
    particle.color = vectorEight;
    particle.startColor = vectorNine.xyz;
    particle.initialAlpha = vectorNine.w;
    particle.endColor = vectorTen.xyz;
    particle.endAlpha = vectorTen.w;
    particle.origin = vectorEleven.xy;
    particle.animationFrame = vectorEleven.z;
    particle.animationMaximumFrames = vectorEleven.w;
    particle.scratchA = vectorTwelve;
    particle.scratchB = vectorThirteen;
    particle.reservedA = vectorFourteen;
    particle.reservedB = vectorFifteen;
    return particle;
}

Particle loadParticle(uint index) {
    uint base = index * VECTORS_PER_PARTICLE;
    return assembleParticle(
        particles.data[base + 0u], particles.data[base + 1u], particles.data[base + 2u], particles.data[base + 3u],
        particles.data[base + 4u], particles.data[base + 5u], particles.data[base + 6u], particles.data[base + 7u],
        particles.data[base + 8u], particles.data[base + 9u], particles.data[base + 10u], particles.data[base + 11u],
        particles.data[base + 12u], particles.data[base + 13u], particles.data[base + 14u], particles.data[base + 15u]
    );
}

Particle loadSpawnParticle(uint index) {
    uint base = index * VECTORS_PER_PARTICLE;
    return assembleParticle(
        spawnParticles.data[base + 0u], spawnParticles.data[base + 1u], spawnParticles.data[base + 2u], spawnParticles.data[base + 3u],
        spawnParticles.data[base + 4u], spawnParticles.data[base + 5u], spawnParticles.data[base + 6u], spawnParticles.data[base + 7u],
        spawnParticles.data[base + 8u], spawnParticles.data[base + 9u], spawnParticles.data[base + 10u], spawnParticles.data[base + 11u],
        spawnParticles.data[base + 12u], spawnParticles.data[base + 13u], spawnParticles.data[base + 14u], spawnParticles.data[base + 15u]
    );
}

void storeParticle(uint index, Particle particle) {
    uint base = index * VECTORS_PER_PARTICLE;
    particles.data[base + 0u] = vec4(particle.position, particle.angle);
    particles.data[base + 1u] = vec4(particle.velocity, particle.angularVelocity);
    particles.data[base + 2u] = vec4(particle.thrust, particle.density);
    particles.data[base + 3u] = vec4(particle.age, particle.delay, particle.lifetime, particle.alive);
    particles.data[base + 4u] = vec4(particle.shapeKind, particle.sizeA, particle.sizeB, particle.scaleRate);
    particles.data[base + 5u] = vec4(particle.maximumSizeA, particle.maximumSizeB, particle.renderKind, particle.uniqueId);
    particles.data[base + 6u] = vec4(particle.restitution, particle.hardness, particle.adhesion, particle.friction);
    particles.data[base + 7u] = vec4(
        particle.staticFriction, particle.rollingResistance, particle.materialReservedA, particle.materialReservedB
    );
    particles.data[base + 8u] = particle.color;
    particles.data[base + 9u] = vec4(particle.startColor, particle.initialAlpha);
    particles.data[base + 10u] = vec4(particle.endColor, particle.endAlpha);
    particles.data[base + 11u] = vec4(particle.origin, particle.animationFrame, particle.animationMaximumFrames);
    particles.data[base + 12u] = particle.scratchA;
    particles.data[base + 13u] = particle.scratchB;
    particles.data[base + 14u] = particle.reservedA;
    particles.data[base + 15u] = particle.reservedB;
}

bool isRectangle(Particle particle) {
    return particle.shapeKind > 0.5;
}

float particleArea(Particle particle) {
    if (isRectangle(particle)) {
        return particle.sizeA * particle.sizeB;
    }
    return PI * particle.sizeA * particle.sizeA;
}

float particleMass(Particle particle) {
    return particle.density * particleArea(particle);
}

float inertialMass(Particle particle) {
    return max(particleMass(particle), MINIMUM_MASS);
}

float collisionRadius(Particle particle) {
    if (isRectangle(particle)) {
        return particle.sizeA * 0.5;
    }
    return particle.sizeA;
}

float momentOfInertia(Particle particle) {
    float shapeFactor = particle.sizeA * particle.sizeA * 0.5;
    if (isRectangle(particle)) {
        shapeFactor = (particle.sizeA * particle.sizeA + particle.sizeB * particle.sizeB) / 12.0;
    }
    return max(inertialMass(particle) * shapeFactor, MINIMUM_MASS);
}

float axisAlignedHalfWidth(Particle particle) {
    if (isRectangle(particle)) {
        return (abs(cos(particle.angle)) * particle.sizeA + abs(sin(particle.angle)) * particle.sizeB) * 0.5;
    }
    return particle.sizeA;
}

float axisAlignedHalfHeight(Particle particle) {
    if (isRectangle(particle)) {
        return (abs(sin(particle.angle)) * particle.sizeA + abs(cos(particle.angle)) * particle.sizeB) * 0.5;
    }
    return particle.sizeA;
}

bool isVisible(Particle particle) {
    return particle.age >= particle.delay;
}

bool isExpired(Particle particle) {
    return particle.age >= particle.lifetime + particle.delay;
}

vec3 limitSpeed(vec3 velocity, float maximumSpeed) {
    if (maximumSpeed <= 0.0) {
        return velocity;
    }
    float speedSquared = velocity.x * velocity.x + velocity.y * velocity.y;
    if (speedSquared <= maximumSpeed * maximumSpeed) {
        return velocity;
    }
    float speedScale = maximumSpeed / sqrt(speedSquared);
    return vec3(velocity.x * speedScale, velocity.y * speedScale, velocity.z);
}

float flowVelocityXAt(float positionY) {
    if (simulation.flowAmplitude == 0.0 || simulation.flowWavelength <= 0.0) {
        return 0.0;
    }
    return simulation.flowAmplitude * cos(TWO_PI * positionY / simulation.flowWavelength + simulation.worldTime * simulation.flowFrequency);
}

Particle integrateAdhesive(Particle source) {
    Particle particle = source;
    float deltaTime = simulation.deltaTime;
    float mass = inertialMass(particle);
    float buoyancyAcceleration = simulation.mediumDensity * particleArea(particle) * simulation.gravity / mass;
    float netForce = mass * (simulation.gravity - buoyancyAcceleration) - particle.adhesion;
    particle.velocity.x = 0.0;
    if (particle.velocity.y <= 0.0 && netForce <= 0.0) {
        particle.velocity.y = 0.0;
        return particle;
    }
    particle.velocity.y = max(0.0, particle.velocity.y + netForce / mass * deltaTime);
    particle.velocity = limitSpeed(particle.velocity, simulation.maxParticleSpeed);
    float travelDistance = particle.velocity.y * deltaTime;
    particle.position.y = particle.position.y + travelDistance;
    float wettedMass = 0.0;
    if (simulation.gravity > 0.0) {
        wettedMass = particle.adhesion / simulation.gravity;
    }
    float massLoss = (simulation.environmentFriction + particle.friction) * wettedMass * travelDistance / (collisionRadius(particle) * 2.0);
    float remainingMass = max(mass - massLoss, MINIMUM_MASS);
    particle.velocity.y = particle.velocity.y * (remainingMass / mass);
    particle.density = remainingMass / particleArea(particle);
    return particle;
}

Particle integrateFree(Particle source) {
    Particle particle = source;
    float deltaTime = simulation.deltaTime;
    float mass = inertialMass(particle);
    float buoyancyAcceleration = simulation.mediumDensity * particleArea(particle) * simulation.gravity / mass;
    particle.velocity.y = particle.velocity.y + (simulation.gravity - buoyancyAcceleration) * deltaTime;
    particle.velocity.x = particle.velocity.x + particle.thrust.x / mass * deltaTime;
    particle.velocity.y = particle.velocity.y + particle.thrust.y / mass * deltaTime;
    particle.velocity.z = particle.velocity.z + particle.thrust.z / mass * deltaTime;
    float dragFraction = min(1.0, max(0.0, simulation.drag) * deltaTime);
    if (dragFraction > 0.0) {
        float flowVelocityX = flowVelocityXAt(particle.position.y);
        particle.velocity.x = particle.velocity.x + (flowVelocityX - particle.velocity.x) * dragFraction;
        particle.velocity.y = particle.velocity.y * (1.0 - dragFraction);
        particle.velocity.z = particle.velocity.z * (1.0 - dragFraction);
    }
    particle.velocity = limitSpeed(particle.velocity, simulation.maxParticleSpeed);
    particle.velocity.z = clamp(particle.velocity.z, -simulation.maxParticleSpeed, simulation.maxParticleSpeed);
    particle.position = particle.position + particle.velocity * deltaTime;
    return particle;
}

Particle advanceDimensions(Particle source) {
    Particle particle = source;
    if (particle.scaleRate == 0.0) {
        return particle;
    }
    float dimensionScale = 1.0 + particle.scaleRate * simulation.deltaTime;
    if (isRectangle(particle)) {
        particle.sizeA = clamp(particle.sizeA * dimensionScale, 0.0, particle.maximumSizeA);
        particle.sizeB = clamp(particle.sizeB * dimensionScale, 0.0, particle.maximumSizeB);
    } else {
        particle.sizeA = clamp(particle.sizeA * dimensionScale, 0.0, particle.maximumSizeA);
        particle.sizeB = particle.sizeA;
    }
    return particle;
}

Particle integrateParticle(Particle source) {
    Particle particle = source;
    particle.age = particle.age + simulation.deltaTime;
    if (particle.age < particle.delay) {
        return particle;
    }
    float activeAge = particle.age - particle.delay;
    float progress = clamp(activeAge / particle.lifetime, 0.0, 1.0);
    if (particle.adhesion > 0.0) {
        particle = integrateAdhesive(particle);
    } else {
        particle = integrateFree(particle);
    }
    if (particle.angularVelocity != 0.0) {
        particle.angle = truncatedModulo(particle.angle + particle.angularVelocity * simulation.deltaTime, TWO_PI);
    }
    particle = advanceDimensions(particle);
    particle.color = vec4(
        mix(particle.startColor, particle.endColor, progress),
        particle.initialAlpha + (particle.endAlpha - particle.initialAlpha) * progress
    );
    if (particle.animationMaximumFrames > 0.5) {
        particle.animationFrame = mod(floor(particle.age / simulation.spriteTicksPerFrame), particle.animationMaximumFrames);
    }
    return particle;
}

void spawnIntegrateMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    float spawnFlag = spawnParticles.data[index * VECTORS_PER_PARTICLE + 3u].w;
    Particle particle;
    if (spawnFlag > 0.5) {
        particle = loadSpawnParticle(index);
    } else {
        particle = loadParticle(index);
        if (particle.alive < 0.5) {
            return;
        }
        if (isExpired(particle)) {
            particle.alive = 0.0;
            storeParticle(index, particle);
            return;
        }
    }
    particle = integrateParticle(particle);
    storeParticle(index, particle);
}

float relativeWallSpeed(Particle particle, vec2 arm, vec2 direction) {
    vec2 pointVelocity = vec2(
        particle.velocity.x - particle.angularVelocity * arm.y,
        particle.velocity.y + particle.angularVelocity * arm.x
    );
    return -dot(pointVelocity, direction);
}

Particle solveWallContact(
    Particle source,
    vec2 normal,
    float restitution,
    float friction,
    float staticFriction
) {
    Particle particle = source;
    float radius = collisionRadius(particle);
    vec2 points[2] = vec2[2](vec2(0.0, 0.0), vec2(0.0, 0.0));
    uint pointCount = 0u;
    if (isRectangle(particle)) {
        float cosine = cos(particle.angle);
        float sine = sin(particle.angle);
        float halfWidth = particle.sizeA * 0.5;
        float halfHeight = particle.sizeB * 0.5;
        vec2 offsets[4] = vec2[4](
            vec2(0.0, 0.0), vec2(0.0, 0.0), vec2(0.0, 0.0), vec2(0.0, 0.0)
        );
        float maximumSupport = -3.0e38;
        for (uint cornerIndex = 0u; cornerIndex < 4u; cornerIndex = cornerIndex + 1u) {
            float signX = 1.0;
            if ((cornerIndex & 1u) == 0u) {
                signX = -1.0;
            }
            float signY = 1.0;
            if ((cornerIndex & 2u) == 0u) {
                signY = -1.0;
            }
            offsets[cornerIndex] = vec2(
                signX * halfWidth * cosine - signY * halfHeight * sine,
                signX * halfWidth * sine + signY * halfHeight * cosine
            );
            maximumSupport = max(maximumSupport, dot(offsets[cornerIndex], normal));
        }
        for (uint cornerIndex = 0u; cornerIndex < 4u; cornerIndex = cornerIndex + 1u) {
            if (dot(offsets[cornerIndex], normal) >= maximumSupport - WALL_CONTACT_TOLERANCE && pointCount < 2u) {
                points[pointCount] = particle.position.xy + offsets[cornerIndex];
                pointCount = pointCount + 1u;
            }
        }
    } else {
        points[0] = particle.position.xy + normal * radius;
        pointCount = 1u;
    }
    float inverseMass = 1.0 / inertialMass(particle);
    float inverseInertia = 1.0 / momentOfInertia(particle);
    vec2 tangent = vec2(-normal.y, normal.x);
    vec2 arms[2] = vec2[2](vec2(0.0, 0.0), vec2(0.0, 0.0));
    float normalMasses[2] = float[2](0.0, 0.0);
    float tangentMasses[2] = float[2](0.0, 0.0);
    float normalImpulses[2] = float[2](0.0, 0.0);
    float tangentImpulses[2] = float[2](0.0, 0.0);
    float restitutionTargets[2] = float[2](0.0, 0.0);
    for (uint pointIndex = 0u; pointIndex < pointCount; pointIndex = pointIndex + 1u) {
        vec2 arm = points[pointIndex] - particle.position.xy;
        arms[pointIndex] = arm;
        float normalCross = arm.x * normal.y - arm.y * normal.x;
        float tangentCross = arm.x * tangent.y - arm.y * tangent.x;
        normalMasses[pointIndex] = 1.0 / (inverseMass + normalCross * normalCross * inverseInertia);
        tangentMasses[pointIndex] = 1.0 / (inverseMass + tangentCross * tangentCross * inverseInertia);
        float approachSpeed = relativeWallSpeed(particle, arm, normal);
        if (approachSpeed < -simulation.minActiveVelocity) {
            restitutionTargets[pointIndex] = -restitution * approachSpeed;
        }
    }
    for (uint iteration = 0u; iteration < SOLVER_ITERATIONS; iteration = iteration + 1u) {
        for (uint pointIndex = 0u; pointIndex < pointCount; pointIndex = pointIndex + 1u) {
            vec2 arm = arms[pointIndex];
            float normalSpeed = relativeWallSpeed(particle, arm, normal);
            float accumulatedNormal = normalImpulses[pointIndex];
            float updatedNormal = max(accumulatedNormal + normalMasses[pointIndex] * (restitutionTargets[pointIndex] - normalSpeed), 0.0);
            float appliedNormal = updatedNormal - accumulatedNormal;
            normalImpulses[pointIndex] = updatedNormal;
            vec2 normalImpulse = normal * appliedNormal;
            particle.velocity.x = particle.velocity.x - normalImpulse.x * inverseMass;
            particle.velocity.y = particle.velocity.y - normalImpulse.y * inverseMass;
            particle.angularVelocity = particle.angularVelocity - inverseInertia * (arm.x * normalImpulse.y - arm.y * normalImpulse.x);
            float tangentSpeed = relativeWallSpeed(particle, arm, tangent);
            float accumulatedTangent = tangentImpulses[pointIndex];
            float updatedTangent = accumulatedTangent - tangentMasses[pointIndex] * tangentSpeed;
            if (abs(updatedTangent) > staticFriction * updatedNormal) {
                updatedTangent = sign(updatedTangent) * friction * updatedNormal;
            }
            float appliedTangent = updatedTangent - accumulatedTangent;
            tangentImpulses[pointIndex] = updatedTangent;
            vec2 tangentImpulse = tangent * appliedTangent;
            particle.velocity.x = particle.velocity.x - tangentImpulse.x * inverseMass;
            particle.velocity.y = particle.velocity.y - tangentImpulse.y * inverseMass;
            particle.angularVelocity = particle.angularVelocity - inverseInertia * (arm.x * tangentImpulse.y - arm.y * tangentImpulse.x);
        }
    }
    float totalNormalImpulse = 0.0;
    for (uint pointIndex = 0u; pointIndex < pointCount; pointIndex = pointIndex + 1u) {
        totalNormalImpulse = totalNormalImpulse + normalImpulses[pointIndex];
    }
    particle.angularVelocity = clamp(particle.angularVelocity, -MAXIMUM_ANGULAR_VELOCITY, MAXIMUM_ANGULAR_VELOCITY);
    float rollingDecay = particle.rollingResistance * totalNormalImpulse * radius / momentOfInertia(particle);
    if (abs(particle.angularVelocity) <= rollingDecay) {
        particle.angularVelocity = 0.0;
    } else {
        particle.angularVelocity = particle.angularVelocity - sign(particle.angularVelocity) * rollingDecay;
    }
    return particle;
}

Particle applyWalls(Particle source) {
    Particle particle = source;
    float halfWidth = axisAlignedHalfWidth(particle);
    float halfHeight = axisAlignedHalfHeight(particle);
    float restitution = clamp(particle.restitution, 0.0, 1.0) * simulation.wallRestitution;
    float friction = simulation.environmentFriction + particle.friction;
    float staticFriction = max(friction, simulation.environmentFriction + particle.staticFriction);
    float minimumX = simulation.viewportX + halfWidth;
    float maximumX = simulation.viewportX + simulation.viewportWidth - halfWidth;
    float minimumY = simulation.viewportY + halfHeight;
    float maximumY = simulation.viewportY + simulation.viewportHeight - halfHeight;
    bool hitLeftWall = particle.position.x < minimumX;
    bool hitRightWall = particle.position.x > maximumX;
    if (hitLeftWall || hitRightWall) {
        if (hitLeftWall) {
            particle.position.x = minimumX;
            particle = solveWallContact(particle, vec2(-1.0, 0.0), restitution, friction, staticFriction);
        } else {
            particle.position.x = maximumX;
            particle = solveWallContact(particle, vec2(1.0, 0.0), restitution, friction, staticFriction);
        }
    }
    bool hitTopWall = particle.position.y < minimumY;
    bool hitBottomWall = particle.position.y > maximumY;
    if (hitTopWall || hitBottomWall) {
        if (hitTopWall) {
            particle.position.y = minimumY;
            particle = solveWallContact(particle, vec2(0.0, -1.0), restitution, friction, staticFriction);
        } else {
            particle.position.y = maximumY;
            particle = solveWallContact(particle, vec2(0.0, 1.0), restitution, friction, staticFriction);
        }
    }
    return particle;
}

void wallMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    Particle particle = loadParticle(index);
    if (particle.alive < 0.5 || !isVisible(particle) || isExpired(particle)) {
        return;
    }
    if (particle.position.z <= simulation.boundaryZ || particle.position.z > simulation.boundaryZ + simulation.boundaryDepth) {
        return;
    }
    particle = applyWalls(particle);
    storeParticle(index, particle);
}

const uint SCRATCH_LARGE_COUNT = 0u;
const uint SCRATCH_FREE_COUNT = 1u;
const uint SCRATCH_FREE_CURSOR = 2u;
const uint SCRATCH_SOURCE_COUNT = 3u;
const uint SCRATCH_SPLIT_IDENTIFIER_COUNTER = 5u;
const uint SCRATCH_SOURCES = 16u;
const uint SCRATCH_LARGE_LIST = 64u;
const uint MAXIMUM_GRAVITY_SOURCES = 16u;
const uint STATE_SET = 2u;
const uint ACCRETION_OWNER_MODE = 2u;
const uint ACCRETION_ABSORB_MODE = 3u;
const float SPLIT_IDENTIFIER_BASE = 2000000.0;
const float NO_SPLIT_REQUEST = -1.0;
const uint CONTACT_SWEEPS_COUNT_MODE = 0u;
const uint CONTACT_SWEEPS_SOLVE_MODE = 1u;
const float CONTAINMENT_TOLERANCE = 0.5;
const float MANIFOLD_TANGENT_EPSILON = 0.001;
const uint MAXIMUM_CANDIDATES = 8u;
const float DENSE_CONTACT_COUNT = 8.0;

uint freeListBase() {
    return SCRATCH_LARGE_LIST + uint(simulation.maxParticles);
}

uint massListBase() {
    return SCRATCH_LARGE_LIST + uint(simulation.maxParticles) * 2u;
}

uint gridCellCount() {
    return uint(simulation.gridColumns * simulation.gridRows);
}

uint gridStartsBase() {
    return gridCellCount();
}

uint gridCursorsBase() {
    return gridCellCount() * 2u + 1u;
}

uint gridSortedBase() {
    return gridCellCount() * 3u + 1u;
}

int gridCellIndexFor(vec2 position) {
    int column = int((position.x - simulation.viewportX) / simulation.cellSize);
    int row = int((position.y - simulation.viewportY) / simulation.cellSize);
    if (position.x < simulation.viewportX || position.y < simulation.viewportY) {
        return -1;
    }
    if (column < 0 || column >= int(simulation.gridColumns) || row < 0 || row >= int(simulation.gridRows)) {
        return -1;
    }
    return row * int(simulation.gridColumns) + column;
}

bool takesPartInCollisions(vec4 lifecycle) {
    return lifecycle.w > 0.5 && lifecycle.x >= lifecycle.y && lifecycle.x < lifecycle.z + lifecycle.y;
}

void gridClearMain() {
    uint index = gl_GlobalInvocationID.x;
    uint cellCount = gridCellCount();
    for (uint cellIndex = index; cellIndex < cellCount; cellIndex = cellIndex + uint(simulation.maxParticles)) {
        grid.data[cellIndex] = 0u;
    }
    if (index == 0u) {
        scratch.data[SCRATCH_LARGE_COUNT] = 0u;
        scratch.data[SCRATCH_FREE_COUNT] = 0u;
        scratch.data[SCRATCH_FREE_CURSOR] = 0u;
    }
}

void gridCountMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    uint base = index * VECTORS_PER_PARTICLE;
    vec4 lifecycle = particles.data[base + 3u];
    int cellIndex = -1;
    float category = 0.0;
    float massValue = 0.0;
    if (takesPartInCollisions(lifecycle)) {
        vec4 positionVector = particles.data[base + 0u];
        vec4 dimensions = particles.data[base + 4u];
        float shapeArea = PI * dimensions.y * dimensions.y;
        if (dimensions.x > 0.5) {
            shapeArea = dimensions.y * dimensions.z;
        }
        massValue = particles.data[base + 2u].w * shapeArea;
        cellIndex = gridCellIndexFor(positionVector.xy);
        if (cellIndex >= 0) {
            float boundingRadius = dimensions.y;
            if (dimensions.x > 0.5) {
                boundingRadius = length(vec2(dimensions.y, dimensions.z)) * 0.5;
            }
            if (boundingRadius * 2.0 > simulation.cellSize) {
                category = 2.0;
                uint largeSlot = atomicAdd(scratch.data[SCRATCH_LARGE_COUNT], 1u);
                scratch.data[SCRATCH_LARGE_LIST + largeSlot] = index;
                cellIndex = -1;
            } else {
                category = 1.0;
                atomicAdd(grid.data[uint(cellIndex)], 1u);
            }
        }
    }
    particles.data[base + 12u] = vec4(float(cellIndex), category, 0.0, 0.0);
    scratch.data[massListBase() + index] = floatBitsToUint(massValue);
}

void gridScanMain() {
    uint cellCount = gridCellCount();
    uint runningTotal = 0u;
    for (uint cellIndex = 0u; cellIndex < cellCount; cellIndex = cellIndex + 1u) {
        grid.data[gridStartsBase() + cellIndex] = runningTotal;
        grid.data[gridCursorsBase() + cellIndex] = runningTotal;
        runningTotal = runningTotal + grid.data[cellIndex];
    }
    grid.data[gridStartsBase() + cellCount] = runningTotal;
}

void gridScatterMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    vec4 cellInformation = particles.data[index * VECTORS_PER_PARTICLE + 12u];
    if (cellInformation.y > 0.5 && cellInformation.y < 1.5) {
        uint sortedPosition = atomicAdd(grid.data[gridCursorsBase() + uint(cellInformation.x)], 1u);
        grid.data[gridSortedBase() + sortedPosition] = index;
    }
}

struct Body {
    uint index;
    vec2 position;
    float planeZ;
    float angle;
    vec2 velocity;
    float angularVelocity;
    bool isRectangleShape;
    float sizeA;
    float sizeB;
    float mass;
    float inertia;
    float restitution;
    float friction;
    float staticFriction;
    float rollingResistance;
    float hardness;
    float collisionRadius;
    float boundingRadius;
    float contactCount;
};

struct Contact {
    bool isValid;
    vec2 normal;
    float depth;
    uint pointCount;
    vec2 pointA;
    vec2 pointB;
};

struct PairOutcome {
    Body first;
    Body second;
};

struct Gather {
    float count;
    vec2 positionDelta;
    vec2 velocityDelta;
    float angularVelocityDelta;
    bool hadContact;
    float ownerMass;
    float ownerPlusOne;
    float absorbedMass;
    float absorbedArea;
    vec2 absorbedMomentum;
    float absorbedCount;
    float splitMomentum;
    vec2 splitNormal;
};

Gather newGather() {
    Gather gather;
    gather.count = 0.0;
    gather.positionDelta = vec2(0.0, 0.0);
    gather.velocityDelta = vec2(0.0, 0.0);
    gather.angularVelocityDelta = 0.0;
    gather.hadContact = false;
    gather.ownerMass = 0.0;
    gather.ownerPlusOne = 0.0;
    gather.absorbedMass = 0.0;
    gather.absorbedArea = 0.0;
    gather.absorbedMomentum = vec2(0.0, 0.0);
    gather.absorbedCount = 0.0;
    gather.splitMomentum = NO_SPLIT_REQUEST;
    gather.splitNormal = vec2(1.0, 0.0);
    return gather;
}

uint kinematicsBase(uint setIndex, uint index) {
    return (setIndex * uint(simulation.maxParticles) + index) * 2u;
}

Body loadBody(uint index, uint setIndex) {
    uint base = index * VECTORS_PER_PARTICLE;
    vec4 positionVector = particles.data[base + 0u];
    vec4 densityVector = particles.data[base + 2u];
    vec4 dimensions = particles.data[base + 4u];
    vec4 materialVector = particles.data[base + 6u];
    vec4 materialSecondVector = particles.data[base + 7u];
    vec4 motion = vec4(positionVector.x, positionVector.y, particles.data[base + 1u].x, particles.data[base + 1u].y);
    vec4 orientation = vec4(positionVector.w, particles.data[base + 1u].w, 0.0, 0.0);
    if (setIndex != STATE_SET) {
        uint kinematicsOffset = kinematicsBase(setIndex, index);
        motion = kinematics.data[kinematicsOffset];
        orientation = kinematics.data[kinematicsOffset + 1u];
    }
    Body body;
    body.index = index;
    body.position = motion.xy;
    body.planeZ = positionVector.z;
    body.angle = orientation.x;
    body.velocity = motion.zw;
    body.angularVelocity = orientation.y;
    body.isRectangleShape = dimensions.x > 0.5;
    body.sizeA = dimensions.y;
    body.sizeB = dimensions.z;
    float area = PI * dimensions.y * dimensions.y;
    float shapeFactor = dimensions.y * dimensions.y * 0.5;
    body.collisionRadius = dimensions.y;
    body.boundingRadius = dimensions.y;
    if (body.isRectangleShape) {
        area = dimensions.y * dimensions.z;
        shapeFactor = (dimensions.y * dimensions.y + dimensions.z * dimensions.z) / 12.0;
        body.collisionRadius = dimensions.y * 0.5;
        body.boundingRadius = length(vec2(dimensions.y, dimensions.z)) * 0.5;
    }
    body.mass = max(densityVector.w * area, MINIMUM_MASS);
    body.inertia = max(body.mass * shapeFactor, MINIMUM_MASS);
    body.restitution = materialVector.x;
    body.hardness = materialVector.y;
    body.friction = materialVector.w;
    body.staticFriction = materialSecondVector.x;
    body.rollingResistance = materialSecondVector.y;
    body.contactCount = orientation.z;
    return body;
}

Contact emptyContact() {
    Contact contact;
    contact.isValid = false;
    contact.normal = vec2(0.0, 0.0);
    contact.depth = 0.0;
    contact.pointCount = 0u;
    contact.pointA = vec2(0.0, 0.0);
    contact.pointB = vec2(0.0, 0.0);
    return contact;
}

Contact detectCircleCircle(Body first, Body second) {
    Contact contact = emptyContact();
    vec2 delta = second.position - first.position;
    float centerDistance = length(delta);
    if (centerDistance <= 0.0) {
        return contact;
    }
    float minimumDistance = first.collisionRadius + second.collisionRadius;
    if (centerDistance >= minimumDistance) {
        return contact;
    }
    float overlap = minimumDistance - centerDistance;
    contact.normal = delta / centerDistance;
    contact.depth = overlap;
    contact.pointCount = 1u;
    contact.pointA = first.position + contact.normal * (first.collisionRadius - overlap * 0.5);
    contact.isValid = true;
    return contact;
}

Contact detectCircleBox(Body circle, Body box, bool isBoxFirst) {
    Contact contact = emptyContact();
    float radius = circle.collisionRadius;
    vec2 halfSize = vec2(box.sizeA * 0.5, box.sizeB * 0.5);
    float cosine = cos(box.angle);
    float sine = sin(box.angle);
    vec2 offset = circle.position - box.position;
    vec2 localPosition = vec2(offset.x * cosine + offset.y * sine, -offset.x * sine + offset.y * cosine);
    vec2 closest = clamp(localPosition, -halfSize, halfSize);
    vec2 delta = localPosition - closest;
    float distanceSquared = dot(delta, delta);
    vec2 localNormal = vec2(0.0, 0.0);
    vec2 localPoint = closest;
    float depth = 0.0;
    if (distanceSquared > 0.0) {
        float closestDistance = sqrt(distanceSquared);
        if (closestDistance >= radius) {
            return contact;
        }
        localNormal = delta / closestDistance;
        depth = radius - closestDistance;
    } else {
        float penetrationX = halfSize.x - abs(localPosition.x);
        float penetrationY = halfSize.y - abs(localPosition.y);
        if (penetrationX < penetrationY) {
            localNormal = vec2(localPosition.x >= 0.0 ? 1.0 : -1.0, 0.0);
            localPoint.x = localNormal.x * halfSize.x;
            depth = radius + penetrationX;
        } else {
            localNormal = vec2(0.0, localPosition.y >= 0.0 ? 1.0 : -1.0);
            localPoint.y = localNormal.y * halfSize.y;
            depth = radius + penetrationY;
        }
    }
    vec2 boxToCircle = vec2(
        localNormal.x * cosine - localNormal.y * sine,
        localNormal.x * sine + localNormal.y * cosine
    );
    contact.normal = boxToCircle * (isBoxFirst ? 1.0 : -1.0);
    contact.depth = depth;
    contact.pointCount = 1u;
    contact.pointA = box.position + vec2(
        localPoint.x * cosine - localPoint.y * sine,
        localPoint.x * sine + localPoint.y * cosine
    );
    contact.isValid = true;
    return contact;
}

uint collectContainedCorners(
    Body cornerBody,
    Body containerBody,
    inout vec2 candidates[8],
    uint startCount
) {
    uint count = startCount;
    float cornerCosine = cos(cornerBody.angle);
    float cornerSine = sin(cornerBody.angle);
    float containerCosine = cos(containerBody.angle);
    float containerSine = sin(containerBody.angle);
    float cornerHalfWidth = cornerBody.sizeA * 0.5;
    float cornerHalfHeight = cornerBody.sizeB * 0.5;
    float containerHalfWidth = containerBody.sizeA * 0.5 + CONTAINMENT_TOLERANCE;
    float containerHalfHeight = containerBody.sizeB * 0.5 + CONTAINMENT_TOLERANCE;
    for (uint cornerIndex = 0u; cornerIndex < 4u; cornerIndex = cornerIndex + 1u) {
        if (count >= MAXIMUM_CANDIDATES) {
            return count;
        }
        float signX = (cornerIndex & 1u) == 0u ? -1.0 : 1.0;
        float signY = (cornerIndex & 2u) == 0u ? -1.0 : 1.0;
        vec2 cornerOffset = vec2(
            signX * cornerHalfWidth * cornerCosine - signY * cornerHalfHeight * cornerSine,
            signX * cornerHalfWidth * cornerSine + signY * cornerHalfHeight * cornerCosine
        );
        vec2 worldPoint = cornerBody.position + cornerOffset;
        vec2 offset = worldPoint - containerBody.position;
        vec2 localPosition = vec2(
            offset.x * containerCosine + offset.y * containerSine,
            -offset.x * containerSine + offset.y * containerCosine
        );
        if (abs(localPosition.x) <= containerHalfWidth && abs(localPosition.y) <= containerHalfHeight) {
            candidates[count] = worldPoint;
            count = count + 1u;
        }
    }
    return count;
}

vec2 supportPointOffset(Body body, vec2 direction) {
    float cosine = cos(body.angle);
    float sine = sin(body.angle);
    vec2 localDirection = vec2(direction.x * cosine + direction.y * sine, -direction.x * sine + direction.y * cosine);
    vec2 localPosition = vec2(
        localDirection.x >= 0.0 ? body.sizeA * 0.5 : -body.sizeA * 0.5,
        localDirection.y >= 0.0 ? body.sizeB * 0.5 : -body.sizeB * 0.5
    );
    return vec2(localPosition.x * cosine - localPosition.y * sine, localPosition.x * sine + localPosition.y * cosine);
}

Contact detectBoxBox(Body first, Body second) {
    Contact contact = emptyContact();
    vec2 firstHalf = vec2(first.sizeA * 0.5, first.sizeB * 0.5);
    vec2 secondHalf = vec2(second.sizeA * 0.5, second.sizeB * 0.5);
    float firstCosine = cos(first.angle);
    float firstSine = sin(first.angle);
    float secondCosine = cos(second.angle);
    float secondSine = sin(second.angle);
    vec2 delta = second.position - first.position;
    float minimumOverlap = 3.0e38;
    vec2 normal = vec2(0.0, 0.0);
    for (uint axisIndex = 0u; axisIndex < 4u; axisIndex = axisIndex + 1u) {
        vec2 axis = vec2(firstCosine, firstSine);
        if (axisIndex == 1u) {
            axis = vec2(-firstSine, firstCosine);
        } else if (axisIndex == 2u) {
            axis = vec2(secondCosine, secondSine);
        } else if (axisIndex == 3u) {
            axis = vec2(-secondSine, secondCosine);
        }
        float firstProjection = firstHalf.x * abs(axis.x * firstCosine + axis.y * firstSine) +
            firstHalf.y * abs(-axis.x * firstSine + axis.y * firstCosine);
        float secondProjection = secondHalf.x * abs(axis.x * secondCosine + axis.y * secondSine) +
            secondHalf.y * abs(-axis.x * secondSine + axis.y * secondCosine);
        float separation = dot(delta, axis);
        float overlap = firstProjection + secondProjection - abs(separation);
        if (overlap <= 0.0) {
            return contact;
        }
        if (overlap < minimumOverlap) {
            minimumOverlap = overlap;
            normal = separation >= 0.0 ? axis : -axis;
        }
    }
    contact.normal = normal;
    contact.depth = minimumOverlap;
    vec2 candidates[8] = vec2[8](
        vec2(0.0, 0.0), vec2(0.0, 0.0), vec2(0.0, 0.0), vec2(0.0, 0.0),
        vec2(0.0, 0.0), vec2(0.0, 0.0), vec2(0.0, 0.0), vec2(0.0, 0.0)
    );
    uint candidateCount = collectContainedCorners(first, second, candidates, 0u);
    candidateCount = collectContainedCorners(second, first, candidates, candidateCount);
    if (candidateCount == 0u) {
        vec2 firstPoint = first.position + supportPointOffset(first, normal);
        vec2 secondPoint = second.position + supportPointOffset(second, -normal);
        contact.pointCount = 1u;
        contact.pointA = (firstPoint + secondPoint) * 0.5;
        contact.isValid = true;
        return contact;
    }
    vec2 tangent = vec2(-normal.y, normal.x);
    uint minimumIndex = 0u;
    uint maximumIndex = 0u;
    float minimumTangent = 3.0e38;
    float maximumTangent = -3.0e38;
    vec2 candidateSum = vec2(0.0, 0.0);
    for (uint candidateIndex = 0u; candidateIndex < candidateCount; candidateIndex = candidateIndex + 1u) {
        float tangentCoordinate = dot(candidates[candidateIndex], tangent);
        if (tangentCoordinate < minimumTangent) {
            minimumTangent = tangentCoordinate;
            minimumIndex = candidateIndex;
        }
        if (tangentCoordinate > maximumTangent) {
            maximumTangent = tangentCoordinate;
            maximumIndex = candidateIndex;
        }
        candidateSum = candidateSum + candidates[candidateIndex];
    }
    if (maximumTangent - minimumTangent < MANIFOLD_TANGENT_EPSILON) {
        contact.pointCount = 1u;
        contact.pointA = candidateSum / float(candidateCount);
    } else {
        contact.pointCount = 2u;
        contact.pointA = candidates[minimumIndex];
        contact.pointB = candidates[maximumIndex];
    }
    contact.isValid = true;
    return contact;
}

Contact detectContact(Body first, Body second) {
    if (!first.isRectangleShape && !second.isRectangleShape) {
        return detectCircleCircle(first, second);
    }
    if (!first.isRectangleShape && second.isRectangleShape) {
        return detectCircleBox(first, second, false);
    }
    if (first.isRectangleShape && !second.isRectangleShape) {
        return detectCircleBox(second, first, true);
    }
    return detectBoxBox(first, second);
}

float rollingDecayFor(Body body, float normalImpulse) {
    float decay = body.rollingResistance * normalImpulse * body.collisionRadius / body.inertia;
    if (abs(body.angularVelocity) <= decay) {
        return 0.0;
    }
    return body.angularVelocity - sign(body.angularVelocity) * decay;
}

PairOutcome solveCircleCircle(Body firstSource, Body secondSource, Contact contact) {
    Body first = firstSource;
    Body second = secondSource;
    PairOutcome outcome;
    vec2 normal = contact.normal;
    float totalMass = first.mass + second.mass;
    first.position = first.position - normal * contact.depth * (second.mass / totalMass);
    second.position = second.position + normal * contact.depth * (first.mass / totalMass);
    vec2 relativeVelocity = first.velocity - second.velocity;
    float relativeNormalSpeed = dot(normal, relativeVelocity);
    float impulse = 2.0 * relativeNormalSpeed / totalMass;
    float restitution = min(first.restitution, second.restitution);
    first.velocity = first.velocity - normal * (impulse * second.mass * (1.0 + restitution));
    second.velocity = second.velocity + normal * (impulse * first.mass * (1.0 + restitution));
    float friction = simulation.environmentFriction + sqrt(first.friction * second.friction);
    float staticFriction = max(
        friction,
        simulation.environmentFriction + sqrt(first.staticFriction * second.staticFriction)
    );
    if (friction <= 0.0) {
        outcome.first = first;
        outcome.second = second;
        return outcome;
    }
    vec2 tangent = vec2(-normal.y, normal.x);
    bool isSpinning = first.angularVelocity != 0.0 || second.angularVelocity != 0.0;
    float firstSpinArm = 0.0;
    float secondSpinArm = 0.0;
    if (isSpinning) {
        firstSpinArm = first.collisionRadius;
        secondSpinArm = second.collisionRadius;
    }
    float slipSpeed = dot(tangent, relativeVelocity) + first.angularVelocity * firstSpinArm + second.angularVelocity * secondSpinArm;
    float effectiveInverseMass = 1.0 / first.mass + 1.0 / second.mass +
        firstSpinArm * firstSpinArm / first.inertia + secondSpinArm * secondSpinArm / second.inertia;
    float reducedMass = first.mass * second.mass / totalMass;
    float normalImpulseMagnitude = abs(2.0 * reducedMass * relativeNormalSpeed * (1.0 + restitution));
    float requiredImpulse = slipSpeed / effectiveInverseMass;
    float frictionImpulse = sign(requiredImpulse) * friction * normalImpulseMagnitude;
    if (abs(requiredImpulse) <= staticFriction * normalImpulseMagnitude) {
        frictionImpulse = requiredImpulse;
    }
    first.velocity = first.velocity - tangent * (frictionImpulse / first.mass);
    second.velocity = second.velocity + tangent * (frictionImpulse / second.mass);
    first.angularVelocity = clamp(
        first.angularVelocity - frictionImpulse * firstSpinArm / first.inertia,
        -MAXIMUM_ANGULAR_VELOCITY,
        MAXIMUM_ANGULAR_VELOCITY
    );
    second.angularVelocity = clamp(
        second.angularVelocity - frictionImpulse * secondSpinArm / second.inertia,
        -MAXIMUM_ANGULAR_VELOCITY,
        MAXIMUM_ANGULAR_VELOCITY
    );
    first.angularVelocity = rollingDecayFor(first, normalImpulseMagnitude);
    second.angularVelocity = rollingDecayFor(second, normalImpulseMagnitude);
    outcome.first = first;
    outcome.second = second;
    return outcome;
}

float pointSpeedAlong(Body body, vec2 arm, vec2 direction) {
    vec2 pointVelocity = vec2(
        body.velocity.x - body.angularVelocity * arm.y,
        body.velocity.y + body.angularVelocity * arm.x
    );
    return dot(pointVelocity, direction);
}

PairOutcome solveBoxContact(Body firstSource, Body secondSource, Contact contact) {
    Body first = firstSource;
    Body second = secondSource;
    PairOutcome outcome;
    vec2 normal = contact.normal;
    vec2 tangent = vec2(-normal.y, normal.x);
    float totalMass = first.mass + second.mass;
    first.position = first.position - normal * contact.depth * (second.mass / totalMass);
    second.position = second.position + normal * contact.depth * (first.mass / totalMass);
    float restitution = 1.0 + 2.0 * min(first.restitution, second.restitution);
    float friction = simulation.environmentFriction + sqrt(first.friction * second.friction);
    float staticFriction = max(
        friction,
        simulation.environmentFriction + sqrt(first.staticFriction * second.staticFriction)
    );
    float firstInverseMass = 1.0 / first.mass;
    float firstInverseInertia = 1.0 / first.inertia;
    float secondInverseMass = 1.0 / second.mass;
    float secondInverseInertia = 1.0 / second.inertia;
    vec2 points[2] = vec2[2](contact.pointA, contact.pointB);
    vec2 firstArms[2] = vec2[2](vec2(0.0, 0.0), vec2(0.0, 0.0));
    vec2 secondArms[2] = vec2[2](vec2(0.0, 0.0), vec2(0.0, 0.0));
    float normalMasses[2] = float[2](0.0, 0.0);
    float tangentMasses[2] = float[2](0.0, 0.0);
    float normalImpulses[2] = float[2](0.0, 0.0);
    float tangentImpulses[2] = float[2](0.0, 0.0);
    float restitutionTargets[2] = float[2](0.0, 0.0);
    for (uint pointIndex = 0u; pointIndex < contact.pointCount; pointIndex = pointIndex + 1u) {
        vec2 firstArm = points[pointIndex] - first.position;
        vec2 secondArm = points[pointIndex] - second.position;
        firstArms[pointIndex] = firstArm;
        secondArms[pointIndex] = secondArm;
        float firstNormalCross = firstArm.x * normal.y - firstArm.y * normal.x;
        float secondNormalCross = secondArm.x * normal.y - secondArm.y * normal.x;
        float firstTangentCross = firstArm.x * tangent.y - firstArm.y * tangent.x;
        float secondTangentCross = secondArm.x * tangent.y - secondArm.y * tangent.x;
        normalMasses[pointIndex] = 1.0 / (firstInverseMass + secondInverseMass +
            firstNormalCross * firstNormalCross * firstInverseInertia +
            secondNormalCross * secondNormalCross * secondInverseInertia);
        tangentMasses[pointIndex] = 1.0 / (firstInverseMass + secondInverseMass +
            firstTangentCross * firstTangentCross * firstInverseInertia +
            secondTangentCross * secondTangentCross * secondInverseInertia);
        float approachSpeed = pointSpeedAlong(second, secondArm, normal) - pointSpeedAlong(first, firstArm, normal);
        if (approachSpeed < -simulation.minActiveVelocity) {
            restitutionTargets[pointIndex] = -restitution * approachSpeed;
        }
    }
    for (uint iteration = 0u; iteration < SOLVER_ITERATIONS; iteration = iteration + 1u) {
        for (uint pointIndex = 0u; pointIndex < contact.pointCount; pointIndex = pointIndex + 1u) {
            vec2 firstArm = firstArms[pointIndex];
            vec2 secondArm = secondArms[pointIndex];
            float normalSpeed = pointSpeedAlong(second, secondArm, normal) - pointSpeedAlong(first, firstArm, normal);
            float accumulatedNormal = normalImpulses[pointIndex];
            float updatedNormal = max(accumulatedNormal + normalMasses[pointIndex] * (restitutionTargets[pointIndex] - normalSpeed), 0.0);
            float appliedNormal = updatedNormal - accumulatedNormal;
            normalImpulses[pointIndex] = updatedNormal;
            vec2 normalImpulse = normal * appliedNormal;
            first.velocity = first.velocity - normalImpulse * firstInverseMass;
            first.angularVelocity = first.angularVelocity - firstInverseInertia * (firstArm.x * normalImpulse.y - firstArm.y * normalImpulse.x);
            second.velocity = second.velocity + normalImpulse * secondInverseMass;
            second.angularVelocity = second.angularVelocity + secondInverseInertia * (secondArm.x * normalImpulse.y - secondArm.y * normalImpulse.x);
            float tangentSpeed = pointSpeedAlong(second, secondArm, tangent) - pointSpeedAlong(first, firstArm, tangent);
            float accumulatedTangent = tangentImpulses[pointIndex];
            float updatedTangent = accumulatedTangent - tangentMasses[pointIndex] * tangentSpeed;
            if (abs(updatedTangent) > staticFriction * updatedNormal) {
                updatedTangent = sign(updatedTangent) * friction * updatedNormal;
            }
            float appliedTangent = updatedTangent - accumulatedTangent;
            tangentImpulses[pointIndex] = updatedTangent;
            vec2 tangentImpulse = tangent * appliedTangent;
            first.velocity = first.velocity - tangentImpulse * firstInverseMass;
            first.angularVelocity = first.angularVelocity - firstInverseInertia * (firstArm.x * tangentImpulse.y - firstArm.y * tangentImpulse.x);
            second.velocity = second.velocity + tangentImpulse * secondInverseMass;
            second.angularVelocity = second.angularVelocity + secondInverseInertia * (secondArm.x * tangentImpulse.y - secondArm.y * tangentImpulse.x);
        }
    }
    float totalNormalImpulse = 0.0;
    for (uint pointIndex = 0u; pointIndex < contact.pointCount; pointIndex = pointIndex + 1u) {
        totalNormalImpulse = totalNormalImpulse + normalImpulses[pointIndex];
    }
    first.angularVelocity = clamp(first.angularVelocity, -MAXIMUM_ANGULAR_VELOCITY, MAXIMUM_ANGULAR_VELOCITY);
    second.angularVelocity = clamp(second.angularVelocity, -MAXIMUM_ANGULAR_VELOCITY, MAXIMUM_ANGULAR_VELOCITY);
    first.angularVelocity = rollingDecayFor(first, totalNormalImpulse);
    second.angularVelocity = rollingDecayFor(second, totalNormalImpulse);
    outcome.first = first;
    outcome.second = second;
    return outcome;
}

bool isResting(Body body) {
    float angularSurfaceSpeed = body.angularVelocity * body.collisionRadius;
    float speedSquared = dot(body.velocity, body.velocity) + angularSurfaceSpeed * angularSurfaceSpeed;
    return speedSquared < simulation.minActiveVelocity * simulation.minActiveVelocity;
}

float bodyArea(Body body) {
    if (body.isRectangleShape) {
        return body.sizeA * body.sizeB;
    }
    return PI * body.sizeA * body.sizeA;
}

bool consumesBody(Body consumer, Body consumed) {
    if (abs(consumer.planeZ - consumed.planeZ) >= simulation.gravityPlaneThickness) {
        return false;
    }
    if (consumer.mass < consumed.mass || (consumer.mass == consumed.mass && consumer.index > consumed.index)) {
        return false;
    }
    return consumer.mass >= consumed.mass * simulation.accretionMassRatio;
}

Gather processPair(
    Gather gatherSource,
    Body selfBody,
    uint otherIndex,
    uint readSet,
    uint mode,
    uint sweepIndex
) {
    Gather gather = gatherSource;
    if (otherIndex == selfBody.index) {
        return gather;
    }
    uint otherBase = otherIndex * VECTORS_PER_PARTICLE;
    if (!takesPartInCollisions(particles.data[otherBase + 3u])) {
        return gather;
    }
    if (mode == ACCRETION_ABSORB_MODE) {
        if (uint(particles.data[otherBase + 13u].x) != selfBody.index + 1u) {
            return gather;
        }
        Body victim = loadBody(otherIndex, STATE_SET);
        gather.absorbedMass = gather.absorbedMass + victim.mass;
        gather.absorbedArea = gather.absorbedArea + bodyArea(victim);
        gather.absorbedMomentum = gather.absorbedMomentum + victim.velocity * victim.mass;
        gather.absorbedCount = gather.absorbedCount + 1.0;
        return gather;
    }
    Body other = loadBody(otherIndex, readSet);
    if (abs(selfBody.planeZ - other.planeZ) >= simulation.collisionPlaneThickness) {
        return gather;
    }
    bool selfIsFirst = selfBody.index < otherIndex;
    Body first = selfBody;
    Body second = other;
    if (!selfIsFirst) {
        first = other;
        second = selfBody;
    }
    if (mode == ACCRETION_OWNER_MODE) {
        if (!consumesBody(other, selfBody)) {
            return gather;
        }
        Contact ownerContact = detectContact(first, second);
        if (!ownerContact.isValid) {
            return gather;
        }
        if (other.mass > gather.ownerMass || (other.mass == gather.ownerMass && float(otherIndex + 1u) < gather.ownerPlusOne)) {
            gather.ownerMass = other.mass;
            gather.ownerPlusOne = float(otherIndex + 1u);
        }
        return gather;
    }
    Contact contact = detectContact(first, second);
    if (!contact.isValid) {
        return gather;
    }
    if (mode == CONTACT_SWEEPS_COUNT_MODE) {
        gather.count = gather.count + 1.0;
        return gather;
    }
    if (consumesBody(first, second) || consumesBody(second, first)) {
        return gather;
    }
    if (isResting(first) && isResting(second)) {
        return gather;
    }
    bool bothCircles = !first.isRectangleShape && !second.isRectangleShape;
    if (sweepIndex > 0u && bothCircles) {
        return gather;
    }
    if (sweepIndex == 0u && selfBody.hardness < other.hardness) {
        float impactMomentum = other.mass * length(other.velocity);
        if (impactMomentum > gather.splitMomentum) {
            gather.splitMomentum = impactMomentum;
            gather.splitNormal = contact.normal;
        }
    }
    float contactLoad = max(1.0, max(selfBody.contactCount, other.contactCount));
    PairOutcome outcome;
    if (bothCircles) {
        outcome = solveCircleCircle(first, second, contact);
        bool isSeparating = dot(contact.normal, first.velocity - second.velocity) <= 0.0;
        if (isSeparating && contactLoad >= DENSE_CONTACT_COUNT) {
            outcome.first.velocity = first.velocity;
            outcome.first.angularVelocity = first.angularVelocity;
            outcome.second.velocity = second.velocity;
            outcome.second.angularVelocity = second.angularVelocity;
        }
    } else {
        outcome = solveBoxContact(first, second, contact);
    }
    float positionRelaxation = pow(1.0 / contactLoad, simulation.contactPositionRelaxationExponent);
    float velocityRelaxation = pow(1.0 / contactLoad, simulation.contactVelocityRelaxationExponent);
    if (selfIsFirst) {
        gather.positionDelta = gather.positionDelta + (outcome.first.position - first.position) * positionRelaxation;
        gather.velocityDelta = gather.velocityDelta + (outcome.first.velocity - first.velocity) * velocityRelaxation;
        gather.angularVelocityDelta = gather.angularVelocityDelta +
            (outcome.first.angularVelocity - first.angularVelocity) * velocityRelaxation;
    } else {
        gather.positionDelta = gather.positionDelta + (outcome.second.position - second.position) * positionRelaxation;
        gather.velocityDelta = gather.velocityDelta + (outcome.second.velocity - second.velocity) * velocityRelaxation;
        gather.angularVelocityDelta = gather.angularVelocityDelta +
            (outcome.second.angularVelocity - second.angularVelocity) * velocityRelaxation;
    }
    gather.hadContact = true;
    return gather;
}

Gather gatherContacts(uint index, uint readSet, uint mode, uint sweepIndex) {
    Gather gather = newGather();
    vec4 cellInformation = particles.data[index * VECTORS_PER_PARTICLE + 12u];
    if (cellInformation.y < 0.5) {
        return gather;
    }
    Body selfBody = loadBody(index, readSet);
    int columns = int(simulation.gridColumns);
    int rows = int(simulation.gridRows);
    int firstColumn = 0;
    int lastColumn = -1;
    int firstRow = 0;
    int lastRow = -1;
    if (cellInformation.y < 1.5) {
        int cellIndex = int(cellInformation.x);
        int column = cellIndex % columns;
        int row = cellIndex / columns;
        firstColumn = max(column - 1, 0);
        lastColumn = min(column + 1, columns - 1);
        firstRow = max(row - 1, 0);
        lastRow = min(row + 1, rows - 1);
    } else {
        float reach = selfBody.boundingRadius + simulation.cellSize * 0.5;
        firstColumn = max(int((selfBody.position.x - reach - simulation.viewportX) / simulation.cellSize), 0);
        lastColumn = min(int((selfBody.position.x + reach - simulation.viewportX) / simulation.cellSize), columns - 1);
        firstRow = max(int((selfBody.position.y - reach - simulation.viewportY) / simulation.cellSize), 0);
        lastRow = min(int((selfBody.position.y + reach - simulation.viewportY) / simulation.cellSize), rows - 1);
    }
    for (int row = firstRow; row <= lastRow; row = row + 1) {
        for (int column = firstColumn; column <= lastColumn; column = column + 1) {
            uint neighborCell = uint(row * columns + column);
            uint cellStart = grid.data[gridStartsBase() + neighborCell];
            uint cellEnd = grid.data[gridStartsBase() + neighborCell + 1u];
            for (uint sortedIndex = cellStart; sortedIndex < cellEnd; sortedIndex = sortedIndex + 1u) {
                uint otherIndex = grid.data[gridSortedBase() + sortedIndex];
                gather = processPair(gather, selfBody, otherIndex, readSet, mode, sweepIndex);
            }
        }
    }
    uint largeCount = min(scratch.data[SCRATCH_LARGE_COUNT], uint(simulation.maxParticles));
    for (uint largeIndex = 0u; largeIndex < largeCount; largeIndex = largeIndex + 1u) {
        uint otherIndex = scratch.data[SCRATCH_LARGE_LIST + largeIndex];
        gather = processPair(gather, selfBody, otherIndex, readSet, mode, sweepIndex);
    }
    return gather;
}

void countContacts(uint index, uint readSet) {
    uint base = index * VECTORS_PER_PARTICLE;
    if (!takesPartInCollisions(particles.data[base + 3u])) {
        return;
    }
    Gather gather = gatherContacts(index, readSet, CONTACT_SWEEPS_COUNT_MODE, 0u);
    uint offset = kinematicsBase(readSet, index) + 1u;
    vec4 orientation = kinematics.data[offset];
    orientation.z = gather.count;
    kinematics.data[offset] = orientation;
}

void solveContacts(uint index, uint readSet, uint writeSet, uint sweepIndex) {
    uint base = index * VECTORS_PER_PARTICLE;
    if (!takesPartInCollisions(particles.data[base + 3u])) {
        return;
    }
    Gather gather = gatherContacts(index, readSet, CONTACT_SWEEPS_SOLVE_MODE, sweepIndex);
    uint readOffset = kinematicsBase(readSet, index);
    uint writeOffset = kinematicsBase(writeSet, index);
    vec4 motion = kinematics.data[readOffset];
    vec4 orientation = kinematics.data[readOffset + 1u];
    float angularVelocity = orientation.y + gather.angularVelocityDelta;
    if (gather.hadContact) {
        angularVelocity = clamp(angularVelocity, -MAXIMUM_ANGULAR_VELOCITY, MAXIMUM_ANGULAR_VELOCITY);
    }
    kinematics.data[writeOffset] = vec4(
        motion.x + gather.positionDelta.x,
        motion.y + gather.positionDelta.y,
        motion.z + gather.velocityDelta.x,
        motion.w + gather.velocityDelta.y
    );
    kinematics.data[writeOffset + 1u] = vec4(orientation.x, angularVelocity, 0.0, 0.0);
    if (sweepIndex == 0u) {
        particles.data[base + 13u] = vec4(0.0, gather.splitNormal.x, gather.splitNormal.y, gather.splitMomentum);
    }
}

void contactBeginMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    uint base = index * VECTORS_PER_PARTICLE;
    if (!takesPartInCollisions(particles.data[base + 3u])) {
        return;
    }
    vec4 positionVector = particles.data[base + 0u];
    vec4 velocityVector = particles.data[base + 1u];
    uint offset = kinematicsBase(0u, index);
    particles.data[base + 13u] = vec4(0.0, 0.0, 0.0, NO_SPLIT_REQUEST);
    kinematics.data[offset] = vec4(positionVector.x, positionVector.y, velocityVector.x, velocityVector.y);
    kinematics.data[offset + 1u] = vec4(positionVector.w, velocityVector.w, 0.0, 0.0);
}

void countContactsFromAMain() {
    if (gl_GlobalInvocationID.x < uint(simulation.maxParticles)) {
        countContacts(gl_GlobalInvocationID.x, 0u);
    }
}

void countContactsFromBMain() {
    if (gl_GlobalInvocationID.x < uint(simulation.maxParticles)) {
        countContacts(gl_GlobalInvocationID.x, 1u);
    }
}

void solveContactsAToBMain() {
    if (gl_GlobalInvocationID.x < uint(simulation.maxParticles)) {
        solveContacts(gl_GlobalInvocationID.x, 0u, 1u, 0u);
    }
}

void solveContactsBToAMain() {
    if (gl_GlobalInvocationID.x < uint(simulation.maxParticles)) {
        solveContacts(gl_GlobalInvocationID.x, 1u, 0u, 1u);
    }
}

void contactEndMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    uint base = index * VECTORS_PER_PARTICLE;
    if (!takesPartInCollisions(particles.data[base + 3u])) {
        return;
    }
    uint offset = kinematicsBase(0u, index);
    vec4 motion = kinematics.data[offset];
    vec4 orientation = kinematics.data[offset + 1u];
    vec4 positionVector = particles.data[base + 0u];
    vec4 velocityVector = particles.data[base + 1u];
    positionVector.x = motion.x;
    positionVector.y = motion.y;
    velocityVector.x = motion.z;
    velocityVector.y = motion.w;
    velocityVector.w = orientation.y;
    particles.data[base + 0u] = positionVector;
    particles.data[base + 1u] = velocityVector;
}

#if STAGE == STAGE_SELECT_GRAVITY_SOURCES
shared float reductionMass[256];
shared uint reductionIndex[256];
shared uint chosenSources[16];
shared uint chosenCount;

void synchronizeWorkgroup() {
    memoryBarrierShared();
    barrier();
}

void selectGravitySourcesMain() {
    uint localIndex = gl_LocalInvocationIndex;
    uint slotCount = uint(simulation.maxParticles);
    uint wantedCount = min(uint(max(simulation.gravitySourceCount, 0.0)), MAXIMUM_GRAVITY_SOURCES);
    if (localIndex == 0u) {
        chosenCount = 0u;
    }
    synchronizeWorkgroup();
    for (uint selectionRound = 0u; selectionRound < wantedCount; selectionRound = selectionRound + 1u) {
        float bestMass = 0.0;
        uint bestIndex = 0xFFFFFFFFu;
        for (uint slot = localIndex; slot < slotCount; slot = slot + 256u) {
            float slotMass = uintBitsToFloat(scratch.data[massListBase() + slot]);
            if (slotMass < MINIMUM_MASS) {
                continue;
            }
            bool alreadyChosen = false;
            for (uint chosenIndex = 0u; chosenIndex < chosenCount; chosenIndex = chosenIndex + 1u) {
                if (chosenSources[chosenIndex] == slot) {
                    alreadyChosen = true;
                }
            }
            if (alreadyChosen) {
                continue;
            }
            if (slotMass > bestMass || (slotMass == bestMass && slot < bestIndex)) {
                bestMass = slotMass;
                bestIndex = slot;
            }
        }
        reductionMass[localIndex] = bestMass;
        reductionIndex[localIndex] = bestIndex;
        synchronizeWorkgroup();
        for (uint stride = 128u; stride > 0u; stride = stride >> 1u) {
            if (localIndex < stride) {
                float candidateMass = reductionMass[localIndex + stride];
                uint candidateIndex = reductionIndex[localIndex + stride];
                if (candidateMass > reductionMass[localIndex] ||
                    (candidateMass == reductionMass[localIndex] && candidateIndex < reductionIndex[localIndex])) {
                    reductionMass[localIndex] = candidateMass;
                    reductionIndex[localIndex] = candidateIndex;
                }
            }
            synchronizeWorkgroup();
        }
        if (localIndex == 0u) {
            if (reductionIndex[0] != 0xFFFFFFFFu) {
                chosenSources[chosenCount] = reductionIndex[0];
                chosenCount = chosenCount + 1u;
            }
        }
        synchronizeWorkgroup();
    }
    if (localIndex == 0u) {
        scratch.data[SCRATCH_SOURCE_COUNT] = chosenCount;
        for (uint sourceIndex = 0u; sourceIndex < chosenCount; sourceIndex = sourceIndex + 1u) {
            scratch.data[SCRATCH_SOURCES + sourceIndex] = chosenSources[sourceIndex];
        }
    }
}
#endif

float massOfSlot(uint slot) {
    return uintBitsToFloat(scratch.data[massListBase() + slot]);
}

bool isGravitySourceSlot(uint slot, uint sourceCount) {
    for (uint sourceIndex = 0u; sourceIndex < sourceCount; sourceIndex = sourceIndex + 1u) {
        if (scratch.data[SCRATCH_SOURCES + sourceIndex] == slot) {
            return true;
        }
    }
    return false;
}

vec2 gravityAccelerationFrom(
    vec2 delta,
    float pullingMass,
    float softeningSquared
) {
    float softenedDistanceSquared = dot(delta, delta) + softeningSquared;
    float inverseDistanceCubed = 1.0 / (softenedDistanceSquared * sqrt(softenedDistanceSquared));
    return delta * (simulation.gravitationalConstant * pullingMass * inverseDistanceCubed * simulation.deltaTime);
}

void gravityMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles) || simulation.gravitationalConstant <= 0.0) {
        return;
    }
    uint base = index * VECTORS_PER_PARTICLE;
    if (!takesPartInCollisions(particles.data[base + 3u])) {
        return;
    }
    uint sourceCount = scratch.data[SCRATCH_SOURCE_COUNT];
    if (sourceCount == 0u) {
        return;
    }
    vec4 positionVector = particles.data[base + 0u];
    vec4 velocityVector = particles.data[base + 1u];
    vec2 selfPosition = positionVector.xy;
    float softeningSquared = simulation.gravitationalSoftening * simulation.gravitationalSoftening;
    float minimumAcceleration = max(simulation.gravityMinimumAcceleration, 1.0e-30);
    bool isSource = isGravitySourceSlot(index, sourceCount);
    vec2 acceleration = vec2(0.0, 0.0);
    for (uint sourceIndex = 0u; sourceIndex < sourceCount; sourceIndex = sourceIndex + 1u) {
        uint sourceSlot = scratch.data[SCRATCH_SOURCES + sourceIndex];
        if (sourceSlot == index) {
            continue;
        }
        vec4 sourceVector = particles.data[sourceSlot * VECTORS_PER_PARTICLE + 0u];
        if (abs(sourceVector.z - positionVector.z) >= simulation.gravityPlaneThickness) {
            continue;
        }
        vec2 delta = sourceVector.xy - selfPosition;
        float sourceMass = massOfSlot(sourceSlot);
        if (!isSource) {
            float sourceRange = sqrt(simulation.gravitationalConstant * sourceMass / minimumAcceleration);
            if (dot(delta, delta) > sourceRange * sourceRange) {
                continue;
            }
        }
        acceleration = acceleration + gravityAccelerationFrom(delta, sourceMass, softeningSquared);
    }
    if (isSource) {
        float selfMass = massOfSlot(index);
        float range = sqrt(simulation.gravitationalConstant * selfMass / minimumAcceleration);
        float rangeSquared = range * range;
        int columns = int(simulation.gridColumns);
        int rows = int(simulation.gridRows);
        int firstColumn = max(int((selfPosition.x - range - simulation.viewportX) / simulation.cellSize), 0);
        int lastColumn = min(int((selfPosition.x + range - simulation.viewportX) / simulation.cellSize), columns - 1);
        int firstRow = max(int((selfPosition.y - range - simulation.viewportY) / simulation.cellSize), 0);
        int lastRow = min(int((selfPosition.y + range - simulation.viewportY) / simulation.cellSize), rows - 1);
        for (int row = firstRow; row <= lastRow; row = row + 1) {
            for (int column = firstColumn; column <= lastColumn; column = column + 1) {
                uint cell = uint(row * columns + column);
                uint cellStart = grid.data[gridStartsBase() + cell];
                uint cellEnd = grid.data[gridStartsBase() + cell + 1u];
                for (uint sortedIndex = cellStart; sortedIndex < cellEnd; sortedIndex = sortedIndex + 1u) {
                    uint otherSlot = grid.data[gridSortedBase() + sortedIndex];
                    if (otherSlot == index || isGravitySourceSlot(otherSlot, sourceCount)) {
                        continue;
                    }
                    vec4 otherVector = particles.data[otherSlot * VECTORS_PER_PARTICLE + 0u];
                    vec2 delta = otherVector.xy - selfPosition;
                    if (abs(otherVector.z - positionVector.z) >= simulation.gravityPlaneThickness || dot(delta, delta) > rangeSquared) {
                        continue;
                    }
                    acceleration = acceleration + gravityAccelerationFrom(delta, massOfSlot(otherSlot), softeningSquared);
                }
            }
        }
        uint largeCount = min(scratch.data[SCRATCH_LARGE_COUNT], uint(simulation.maxParticles));
        for (uint largeIndex = 0u; largeIndex < largeCount; largeIndex = largeIndex + 1u) {
            uint otherSlot = scratch.data[SCRATCH_LARGE_LIST + largeIndex];
            if (otherSlot == index || isGravitySourceSlot(otherSlot, sourceCount)) {
                continue;
            }
            vec4 otherVector = particles.data[otherSlot * VECTORS_PER_PARTICLE + 0u];
            vec2 delta = otherVector.xy - selfPosition;
            if (abs(otherVector.z - positionVector.z) >= simulation.gravityPlaneThickness || dot(delta, delta) > rangeSquared) {
                continue;
            }
            acceleration = acceleration + gravityAccelerationFrom(delta, massOfSlot(otherSlot), softeningSquared);
        }
    }
    vec3 pulledVelocity = limitSpeed(
        vec3(velocityVector.x + acceleration.x, velocityVector.y + acceleration.y, velocityVector.z),
        simulation.maxParticleSpeed
    );
    velocityVector.x = pulledVelocity.x;
    velocityVector.y = pulledVelocity.y;
    particles.data[base + 1u] = velocityVector;
}

void accretionOwnerMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    uint base = index * VECTORS_PER_PARTICLE;
    if (!takesPartInCollisions(particles.data[base + 3u])) {
        return;
    }
    Gather gather = gatherContacts(index, STATE_SET, ACCRETION_OWNER_MODE, 0u);
    particles.data[base + 13u] = vec4(gather.ownerPlusOne, 0.0, 0.0, NO_SPLIT_REQUEST);
}

float netGravityOf(float density) {
    return simulation.gravity * (1.0 - simulation.mediumDensity / max(density, MINIMUM_MASS));
}

void accretionApplyMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    uint base = index * VECTORS_PER_PARTICLE;
    vec4 lifecycle = particles.data[base + 3u];
    if (!takesPartInCollisions(lifecycle)) {
        return;
    }
    float ownerPlusOne = particles.data[base + 13u].x;
    if (ownerPlusOne > 0.5) {
        uint ownerSlot = uint(ownerPlusOne) - 1u;
        if (particles.data[ownerSlot * VECTORS_PER_PARTICLE + 13u].x < 0.5) {
            particles.data[base + 3u] = vec4(lifecycle.z + lifecycle.y, lifecycle.y, lifecycle.z, lifecycle.w);
        }
        return;
    }
    Gather gather = gatherContacts(index, STATE_SET, ACCRETION_ABSORB_MODE, 0u);
    if (gather.absorbedCount < 0.5) {
        return;
    }
    Particle particle = loadParticle(index);
    float consumingMass = particleMass(particle);
    float previousNetWeight = consumingMass * netGravityOf(particle.density);
    float holdFraction = 0.0;
    if (previousNetWeight != 0.0) {
        holdFraction = -particle.thrust.y / previousNetWeight;
    }
    float combinedMass = consumingMass + gather.absorbedMass;
    vec2 combinedVelocity = (particle.velocity.xy * consumingMass + gather.absorbedMomentum) / combinedMass;
    particle.velocity.x = combinedVelocity.x;
    particle.velocity.y = combinedVelocity.y;
    float combinedArea = particleArea(particle) + gather.absorbedArea;
    if (isRectangle(particle)) {
        float sideLength = sqrt(combinedArea);
        particle.sizeA = sideLength;
        particle.sizeB = sideLength;
    } else {
        particle.sizeA = sqrt(combinedArea / PI);
        particle.sizeB = particle.sizeA;
    }
    particle.density = combinedMass / combinedArea;
    float thrustScale = combinedMass / consumingMass;
    particle.thrust.x = particle.thrust.x * thrustScale;
    particle.thrust.z = particle.thrust.z * thrustScale;
    particle.thrust.y = -holdFraction * combinedMass * netGravityOf(particle.density);
    particles.data[base + 1u] = vec4(particle.velocity, particle.angularVelocity);
    particles.data[base + 2u] = vec4(particle.thrust, particle.density);
    particles.data[base + 4u] = vec4(particle.shapeKind, particle.sizeA, particle.sizeB, particle.scaleRate);
}

void freeSlotsMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    if (particles.data[index * VECTORS_PER_PARTICLE + 3u].w < 0.5) {
        uint freePosition = atomicAdd(scratch.data[SCRATCH_FREE_COUNT], 1u);
        scratch.data[freeListBase() + freePosition] = index;
    }
}

float wallContactPlaneZ(float identifier) {
    float planeSpacing = simulation.gravityPlaneThickness * 2.0;
    float planeCount = max(1.0, floor(simulation.collisionPlaneThickness / planeSpacing) - 1.0);
    return (floor(truncatedModulo(identifier, planeCount)) + 1.0) * planeSpacing;
}

void splitMain() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= uint(simulation.maxParticles)) {
        return;
    }
    uint base = index * VECTORS_PER_PARTICLE;
    if (!takesPartInCollisions(particles.data[base + 3u])) {
        return;
    }
    vec4 splitRequest = particles.data[base + 13u];
    if (splitRequest.w < 0.0) {
        return;
    }
    Particle particle = loadParticle(index);
    float requestedCount = clamp(
        floor(splitRequest.w / simulation.shatterMomentumPerFragment),
        simulation.shatterMinimumFragmentCount,
        simulation.shatterMaximumFragmentCount
    );
    float currentRadius = collisionRadius(particle);
    float fittingCount = floor(currentRadius / simulation.shatterMinimumFragmentRadius);
    float wantedCount = min(requestedCount, fittingCount);
    if (wantedCount < simulation.shatterMinimumFragmentCount) {
        return;
    }
    uint wantedChildren = uint(wantedCount) - 1u;
    uint claimStart = atomicAdd(scratch.data[SCRATCH_FREE_CURSOR], wantedChildren);
    uint freeCount = scratch.data[SCRATCH_FREE_COUNT];
    uint grantedChildren = 0u;
    if (freeCount > claimStart) {
        grantedChildren = min(freeCount - claimStart, wantedChildren);
    }
    float fragmentCount = float(grantedChildren + 1u);
    if (fragmentCount < simulation.shatterMinimumFragmentCount) {
        return;
    }
    vec2 normal = splitRequest.yz;
    vec2 tangent = vec2(-normal.y, normal.x);
    vec2 sourcePosition = particle.position.xy;
    vec2 sourceVelocity = particle.velocity.xy;
    float centerOffset = (fragmentCount - 1.0) * 0.5;
    float fragmentMassRatio = 1.0 / (fragmentCount * fragmentCount);
    float fragmentDiameter = currentRadius / fragmentCount * 2.0;
    float parentMass = particleMass(particle);
    float fragmentMass = parentMass * fragmentMassRatio;
    float parentNetWeight = parentMass * netGravityOf(particle.density);
    float holdFraction = 0.0;
    if (parentNetWeight != 0.0) {
        holdFraction = -particle.thrust.y / parentNetWeight;
    }
    float spreadVelocity = simulation.shatterSpreadSpeed * 2.0;
    float childThrustY = -holdFraction * fragmentMass * netGravityOf(particle.density);
    uint freeBase = freeListBase();
    for (uint childIndex = 1u; childIndex <= grantedChildren; childIndex = childIndex + 1u) {
        float childOffset = float(childIndex) - centerOffset;
        Particle child = particle;
        child.position.x = sourcePosition.x + tangent.x * fragmentDiameter * childOffset;
        child.position.y = sourcePosition.y + tangent.y * fragmentDiameter * childOffset;
        child.velocity.x = sourceVelocity.x + tangent.x * spreadVelocity * childOffset;
        child.velocity.y = sourceVelocity.y + tangent.y * spreadVelocity * childOffset;
        child.sizeA = particle.sizeA / fragmentCount;
        child.sizeB = particle.sizeB / fragmentCount;
        child.thrust = vec3(particle.thrust.x * fragmentMassRatio, childThrustY, particle.thrust.z * fragmentMassRatio);
        child.uniqueId = SPLIT_IDENTIFIER_BASE + float(atomicAdd(scratch.data[SCRATCH_SPLIT_IDENTIFIER_COUNTER], 1u));
        if (particle.materialReservedA > 0.5) {
            child.position.z = wallContactPlaneZ(child.uniqueId);
        }
        child.alive = 1.0;
        child.scratchA = vec4(0.0, 0.0, 0.0, 0.0);
        child.scratchB = vec4(0.0, 0.0, 0.0, NO_SPLIT_REQUEST);
        storeParticle(scratch.data[freeBase + claimStart + childIndex - 1u], child);
    }
    particle.sizeA = particle.sizeA / fragmentCount;
    particle.sizeB = particle.sizeB / fragmentCount;
    particle.position.x = sourcePosition.x - tangent.x * fragmentDiameter * centerOffset;
    particle.position.y = sourcePosition.y - tangent.y * fragmentDiameter * centerOffset;
    particle.velocity.x = sourceVelocity.x - tangent.x * spreadVelocity * centerOffset;
    particle.velocity.y = sourceVelocity.y - tangent.y * spreadVelocity * centerOffset;
    particle.thrust.x = particle.thrust.x * fragmentMassRatio;
    particle.thrust.y = childThrustY;
    particle.thrust.z = particle.thrust.z * fragmentMassRatio;
    particle.scratchB = vec4(0.0, 0.0, 0.0, NO_SPLIT_REQUEST);
    storeParticle(index, particle);
}

void main() {
#if STAGE == STAGE_SPAWN_INTEGRATE
    spawnIntegrateMain();
#elif STAGE == STAGE_GRID_CLEAR
    gridClearMain();
#elif STAGE == STAGE_GRID_COUNT
    gridCountMain();
#elif STAGE == STAGE_GRID_SCAN
    gridScanMain();
#elif STAGE == STAGE_GRID_SCATTER
    gridScatterMain();
#elif STAGE == STAGE_SELECT_GRAVITY_SOURCES
    selectGravitySourcesMain();
#elif STAGE == STAGE_GRAVITY
    gravityMain();
#elif STAGE == STAGE_ACCRETION_OWNER
    accretionOwnerMain();
#elif STAGE == STAGE_ACCRETION_APPLY
    accretionApplyMain();
#elif STAGE == STAGE_CONTACT_BEGIN
    contactBeginMain();
#elif STAGE == STAGE_COUNT_CONTACTS_FROM_A
    countContactsFromAMain();
#elif STAGE == STAGE_SOLVE_CONTACTS_A_TO_B
    solveContactsAToBMain();
#elif STAGE == STAGE_COUNT_CONTACTS_FROM_B
    countContactsFromBMain();
#elif STAGE == STAGE_SOLVE_CONTACTS_B_TO_A
    solveContactsBToAMain();
#elif STAGE == STAGE_CONTACT_END
    contactEndMain();
#elif STAGE == STAGE_FREE_SLOTS
    freeSlotsMain();
#elif STAGE == STAGE_SPLIT
    splitMain();
#elif STAGE == STAGE_WALL
    wallMain();
#endif
}
