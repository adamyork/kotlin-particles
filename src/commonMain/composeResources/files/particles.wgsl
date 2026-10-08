struct ParticleBuffer {
  data: array<vec4<f32>>,
};

struct CollisionSignalBuffer {
  projectileHitCount: atomic<u32>,
  projectileHitXBits: atomic<u32>,
  projectileHitYBits: atomic<u32>,
  projectileHitSizeBits: atomic<u32>,
};

struct ComputeUniforms {
  deltaTime: f32,
  gravity: f32,
  spawnCount: f32,
  maxParticles: f32,
  tickRate: f32,
  simulationSpeed: f32,
  gravityBoost: f32,
  lifetimeDecay: f32,
  viewportX: f32,
  viewportY: f32,
  viewportWidth: f32,
  viewportHeight: f32,
  playerX: f32,
  playerY: f32,
  playerWidth: f32,
  playerHeight: f32,
};

@group(0) @binding(0) var<storage, read> computeSourceParticles: ParticleBuffer;
@group(0) @binding(1) var<storage, read_write> computeDestinationParticles: ParticleBuffer;
@group(0) @binding(2) var<storage, read> computeSpawnParticles: ParticleBuffer;
@group(0) @binding(3) var<uniform> computeUniforms: ComputeUniforms;
@group(0) @binding(4) var<storage, read_write> collisionSignal: CollisionSignalBuffer;

struct AttractionResult {
  consumedBySomeone: f32,
  consumedMassTotal: f32,
  consumedAttractionTotal: f32,
  consumedAreaTotal: f32,
};

const blackHoleCoreVisibilityDelay = 60.0;

fn isAttractionCapableKind(kind: f32) -> bool {
  return kind > 7.5 && kind <= 10.5;
}

fn isAttractionVisible(kind: f32, age: f32) -> bool {
  if (kind > 8.5 && kind <= 9.5) {
    return age >= blackHoleCoreVisibilityDelay;
  }
  return true;
}

fn attractionMassFor(kind: f32, typeInfo: vec4<f32>, kindDataA: vec4<f32>) -> f32 {
  if (kind > 8.5 && kind <= 9.5) {
    return max(typeInfo.z, 0.0001);
  }
  return max(kindDataA.x, 0.0001);
}

fn attractionValueFor(kind: f32, typeInfo: vec4<f32>, kindDataA: vec4<f32>) -> f32 {
  if (kind > 8.5 && kind <= 9.5) {
    return max(typeInfo.w, 0.0);
  }
  return max(kindDataA.y, 0.0);
}

fn applyGenericAttraction(
  selfIndex: u32,
  maxParticles: u32,
  elapsedTicks: f32,
  selfMass: f32,
  selfAttraction: f32,
  selfRadius: f32,
  selfMaxVelocity: f32,
  positionVelocity: ptr<function, vec4<f32>>
) -> AttractionResult {
  var result: AttractionResult;
  result.consumedBySomeone = 0.0;
  result.consumedMassTotal = 0.0;
  result.consumedAttractionTotal = 0.0;
  result.consumedAreaTotal = 0.0;
  for (var otherIndex = 0u; otherIndex < maxParticles; otherIndex = otherIndex + 1u) {
    if (otherIndex == selfIndex) {
      continue;
    }
    let otherBase = otherIndex * 6u;
    let otherLifecycle = computeSourceParticles.data[otherBase + 1u];
    if (otherLifecycle.w <= 0.5) {
      continue;
    }
    let otherTypeInfo = computeSourceParticles.data[otherBase + 3u];
    let otherKind = otherTypeInfo.x;
    if (!isAttractionCapableKind(otherKind)) {
      continue;
    }
    if (!isAttractionVisible(otherKind, otherLifecycle.x)) {
      continue;
    }
    let otherKindDataA = computeSourceParticles.data[otherBase + 4u];
    let otherMass = attractionMassFor(otherKind, otherTypeInfo, otherKindDataA);
    let otherAttraction = attractionValueFor(otherKind, otherTypeInfo, otherKindDataA);
    if (abs(selfAttraction - otherAttraction) <= 0.0001) {
      continue;
    }
    let otherPositionVelocity = computeSourceParticles.data[otherBase + 0u];
    let otherRadius = max(otherLifecycle.z * 0.5, 1.0);
    let deltaX = otherPositionVelocity.x - (*positionVelocity).x;
    let deltaY = otherPositionVelocity.y - (*positionVelocity).y;
    let distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
    if (distance <= 0.0) {
      continue;
    }
    let combinedRadius = selfRadius + otherRadius;
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
        let pullMagnitude = otherAttraction * 0.5;
        (*positionVelocity).z = (*positionVelocity).z + ((deltaX / distance) * pullMagnitude * elapsedTicks);
        (*positionVelocity).w = (*positionVelocity).w + ((deltaY / distance) * pullMagnitude * elapsedTicks);
        if (selfMaxVelocity > 0.0) {
          let pulledSpeed = sqrt(((*positionVelocity).z * (*positionVelocity).z) + ((*positionVelocity).w * (*positionVelocity).w));
          if (pulledSpeed > selfMaxVelocity) {
            let pulledSpeedScale = selfMaxVelocity / pulledSpeed;
            (*positionVelocity).z = (*positionVelocity).z * pulledSpeedScale;
            (*positionVelocity).w = (*positionVelocity).w * pulledSpeedScale;
          }
        }
      }
    }
  }
  return result;
}

@compute @workgroup_size(64)
fn computeMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  let maxParticles = u32(computeUniforms.maxParticles);
  if (index >= maxParticles) {
    return;
  }
  let base = index * 6u;
  var positionVelocity = computeSourceParticles.data[base + 0u];
  var lifecycle = computeSourceParticles.data[base + 1u];
  var colorRgba = computeSourceParticles.data[base + 2u];
  var typeInfo = computeSourceParticles.data[base + 3u];
  var kindDataA = computeSourceParticles.data[base + 4u];
  var kindDataB = computeSourceParticles.data[base + 5u];
  let spawnPositionVelocity = computeSpawnParticles.data[base + 0u];
  let spawnLifecycle = computeSpawnParticles.data[base + 1u];
  let spawnColorRgba = computeSpawnParticles.data[base + 2u];
  let spawnTypeInfo = computeSpawnParticles.data[base + 3u];
  let spawnKindDataA = computeSpawnParticles.data[base + 4u];
  let spawnKindDataB = computeSpawnParticles.data[base + 5u];
  if (spawnLifecycle.w > 0.5) {
    let spawnKind = spawnTypeInfo.x;
    let wasGobblerOrBlackHoleExplosion = (typeInfo.x > 7.5 && typeInfo.x <= 8.5) || (typeInfo.x > 9.5 && typeInfo.x <= 10.5);
    let wasStressTest = typeInfo.x > 10.5 && typeInfo.x <= 11.5;
    let wasConsumed = (wasGobblerOrBlackHoleExplosion && typeInfo.w > 0.5) ||
        (wasStressTest && typeInfo.w >= 4.0);
    let isFreshSpawnInstance = spawnLifecycle.x < 1.0;
    let isProjectileSpawn = (spawnKind > 1.5 && spawnKind <= 2.5) || (spawnKind > 11.5 && spawnKind <= 12.5);
    let preserveGpuState = (spawnKind > 1.5 && lifecycle.w > 0.5 && !(isProjectileSpawn && isFreshSpawnInstance)) ||
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
    let particleKind = typeInfo.x;
    let stepSeconds = computeUniforms.deltaTime * computeUniforms.simulationSpeed;
    let stepLifetime = computeUniforms.deltaTime * computeUniforms.tickRate * computeUniforms.lifetimeDecay;
    let frameStep = max(1.0, round(computeUniforms.deltaTime * computeUniforms.tickRate));
    let elapsedTicks = clamp(computeUniforms.deltaTime * computeUniforms.tickRate, 0.5, 2.0);
    if (particleKind > 4.5 && particleKind <= 5.5) {
      const fireworkBurstYAcceleration = 0.05;
      const fireworkBurstMass = 0.02;
      const fireworkBurstDrag = 0.02;
      const fireworkBurstMaxVelocity = 10.0;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        positionVelocity.w = positionVelocity.w + (fireworkBurstYAcceleration * elapsedTicks);
        let gravityScale = min(1.0, fireworkBurstMass);
        positionVelocity.w = positionVelocity.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (fireworkBurstDrag * elapsedTicks));
        positionVelocity.z = positionVelocity.z * dragFactor;
        positionVelocity.w = positionVelocity.w * dragFactor;
        positionVelocity.z = clamp(positionVelocity.z, -fireworkBurstMaxVelocity, fireworkBurstMaxVelocity);
        positionVelocity.w = clamp(positionVelocity.w, -fireworkBurstMaxVelocity, fireworkBurstMaxVelocity);
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
      }
    } else if (particleKind > 3.5 && particleKind <= 4.5) {
      const fireworkTailMass = 0.02;
      lifecycle.x = lifecycle.x + elapsedTicks;
      let tailDelay = max(typeInfo.w, 0.0);
      let tailTotalLifetime = lifecycle.y + tailDelay;
      if (lifecycle.x >= tailTotalLifetime) {
        lifecycle.w = 0.0;
      } else if (lifecycle.x >= tailDelay) {
        let gravityScale = min(1.0, fireworkTailMass);
        positionVelocity.w = positionVelocity.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let maxYVelocity = max(typeInfo.z, 1.0);
        positionVelocity.w = clamp(positionVelocity.w, -maxYVelocity, maxYVelocity);
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
      }
    } else if (particleKind > 2.5 && particleKind <= 3.5) {
      const itemReturnThrust = 0.16;
      const itemReturnMaxSpeed = 5.25;
      const itemReturnVelocityHeadroom = 0.2;
      const itemReturnMass = 0.15;
      const itemReturnDrag = 0.001;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        let unitX = typeInfo.z;
        let unitY = typeInfo.w;
        let maxVelocityX = (abs(unitX) * itemReturnMaxSpeed) + itemReturnVelocityHeadroom;
        let maxVelocityY = (abs(unitY) * itemReturnMaxSpeed) + itemReturnVelocityHeadroom;
        positionVelocity.z = positionVelocity.z + (unitX * itemReturnThrust * elapsedTicks);
        positionVelocity.w = positionVelocity.w + (unitY * itemReturnThrust * elapsedTicks);
        let gravityScale = min(1.0, itemReturnMass);
        positionVelocity.w = positionVelocity.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (itemReturnDrag * elapsedTicks));
        positionVelocity.z = positionVelocity.z * dragFactor;
        positionVelocity.w = positionVelocity.w * dragFactor;
        positionVelocity.z = clamp(positionVelocity.z, -maxVelocityX, maxVelocityX);
        positionVelocity.w = clamp(positionVelocity.w, -maxVelocityY, maxVelocityY);
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
      }
    } else if ((particleKind > 1.5 && particleKind <= 2.5) || (particleKind > 11.5 && particleKind <= 12.5)) {
      const projectileThrust = 0.16;
      const projectileMaxSpeed = 5.25;
      const projectileVelocityHeadroom = 0.2;
      const projectileMass = 0.15;
      const projectileDrag = 0.001;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        let unitX = typeInfo.z;
        let unitY = typeInfo.w;
        let maxVelocityX = (abs(unitX) * projectileMaxSpeed) + projectileVelocityHeadroom;
        let maxVelocityY = (abs(unitY) * projectileMaxSpeed) + projectileVelocityHeadroom;
        positionVelocity.z = positionVelocity.z + (unitX * projectileThrust * elapsedTicks);
        positionVelocity.w = positionVelocity.w + (unitY * projectileThrust * elapsedTicks);
        let gravityScale = min(1.0, projectileMass);
        positionVelocity.w = positionVelocity.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (projectileDrag * elapsedTicks));
        positionVelocity.z = positionVelocity.z * dragFactor;
        positionVelocity.w = positionVelocity.w * dragFactor;
        positionVelocity.z = clamp(positionVelocity.z, -maxVelocityX, maxVelocityX);
        positionVelocity.w = clamp(positionVelocity.w, -maxVelocityY, maxVelocityY);
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
        let playerMinX = computeUniforms.playerX;
        let playerMinY = computeUniforms.playerY;
        let playerMaxX = playerMinX + max(computeUniforms.playerWidth, 1.0);
        let playerMaxY = playerMinY + max(computeUniforms.playerHeight, 1.0);
        let nearestX = clamp(positionVelocity.x, playerMinX, playerMaxX);
        let nearestY = clamp(positionVelocity.y, playerMinY, playerMaxY);
        let deltaX = positionVelocity.x - nearestX;
        let deltaY = positionVelocity.y - nearestY;
        let projectileRadius = max(lifecycle.z * 0.5, 1.0);
        if ((deltaX * deltaX) + (deltaY * deltaY) <= (projectileRadius * projectileRadius)) {
          lifecycle.w = 0.0;
          let previousHitCount = atomicAdd(&collisionSignal.projectileHitCount, 1u);
          if (previousHitCount == 0u) {
            atomicStore(&collisionSignal.projectileHitXBits, bitcast<u32>(positionVelocity.x));
            atomicStore(&collisionSignal.projectileHitYBits, bitcast<u32>(positionVelocity.y));
            atomicStore(&collisionSignal.projectileHitSizeBits, bitcast<u32>(max(lifecycle.z, 1.0)));
          }
        }
      }
    } else if (particleKind > 5.5 && particleKind <= 6.5) {
      const collisionMassScale = 0.04;
      const collisionDrag = 0.01;
      const collisionMaxVelocity = 10.0;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        let radius = lifecycle.z * 0.5;
        let mass = radius * collisionMassScale;
        let gravityScale = min(1.0, mass);
        positionVelocity.w = positionVelocity.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (collisionDrag * elapsedTicks));
        positionVelocity.z = positionVelocity.z * dragFactor;
        positionVelocity.w = positionVelocity.w * dragFactor;
        positionVelocity.z = clamp(positionVelocity.z, -collisionMaxVelocity, collisionMaxVelocity);
        positionVelocity.w = clamp(positionVelocity.w, -collisionMaxVelocity, collisionMaxVelocity);
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
      }
    } else if (particleKind > 6.5 && particleKind <= 7.5) {
      const collidingBitsMass = 0.12;
      const collidingBitsRestitution = 0.6;
      const collidingBitsMaxVelocity = 10.0;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        let gravityScale = min(1.0, collidingBitsMass);
        positionVelocity.w = positionVelocity.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        positionVelocity.z = clamp(positionVelocity.z, -collidingBitsMaxVelocity, collidingBitsMaxVelocity);
        positionVelocity.w = clamp(positionVelocity.w, -collidingBitsMaxVelocity, collidingBitsMaxVelocity);
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
        let halfSize = max(lifecycle.z * 0.5, 1.0);
        let massDamping = 1.0 / (1.0 + collidingBitsMass);
        let boundaryRestitutionFactor = collidingBitsRestitution * massDamping;
        let minX = computeUniforms.viewportX + halfSize;
        let maxX = computeUniforms.viewportX + computeUniforms.viewportWidth - halfSize;
        let minY = computeUniforms.viewportY + halfSize;
        let maxY = computeUniforms.viewportY + computeUniforms.viewportHeight - halfSize;
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
        let myRadius = halfSize;
        for (var otherIndex = 0u; otherIndex < maxParticles; otherIndex = otherIndex + 1u) {
          if (otherIndex == index) {
            continue;
          }
          let otherBase = otherIndex * 6u;
          let otherLifecycle = computeSourceParticles.data[otherBase + 1u];
          if (otherLifecycle.w <= 0.5) {
            continue;
          }
          let otherTypeInfo = computeSourceParticles.data[otherBase + 3u];
          if (otherTypeInfo.x <= 6.5 || otherTypeInfo.x > 7.5) {
            continue;
          }
          let otherPositionVelocity = computeSourceParticles.data[otherBase + 0u];
          let otherRadius = max(otherLifecycle.z * 0.5, 1.0);
          let deltaX = otherPositionVelocity.x - positionVelocity.x;
          let deltaY = otherPositionVelocity.y - positionVelocity.y;
          let distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
          let minimumDistance = myRadius + otherRadius;
          if (distance > 0.0 && distance < minimumDistance) {
            let normalX = deltaX / distance;
            let normalY = deltaY / distance;
            let overlap = minimumDistance - distance;
            positionVelocity.x = positionVelocity.x - (normalX * overlap * 0.5);
            positionVelocity.y = positionVelocity.y - (normalY * overlap * 0.5);
            let relativeVelocityX = positionVelocity.z - otherPositionVelocity.z;
            let relativeVelocityY = positionVelocity.w - otherPositionVelocity.w;
            let dotProduct = (normalX * relativeVelocityX) + (normalY * relativeVelocityY);
            positionVelocity.z = positionVelocity.z - (dotProduct * normalX * (1.0 + collidingBitsRestitution));
            positionVelocity.w = positionVelocity.w - (dotProduct * normalY * (1.0 + collidingBitsRestitution));
          }
        }
      }
    } else if (particleKind > 7.5 && particleKind <= 8.5) {
      const gobblerDrag = 0.002;
      const gobblerMaxVelocity = 6.0;
      const gobblerRestitution = 1.0;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        let dragFactor = max(0.0, 1.0 - (gobblerDrag * elapsedTicks));
        positionVelocity.z = positionVelocity.z * dragFactor;
        positionVelocity.w = positionVelocity.w * dragFactor;
        positionVelocity.z = clamp(positionVelocity.z, -gobblerMaxVelocity, gobblerMaxVelocity);
        positionVelocity.w = clamp(positionVelocity.w, -gobblerMaxVelocity, gobblerMaxVelocity);
        var radius = max(lifecycle.z * 0.5, 1.0);
        let mass = attractionMassFor(particleKind, typeInfo, kindDataA);
        let attraction = attractionValueFor(particleKind, typeInfo, kindDataA);
        let attractionResult = applyGenericAttraction(
          index, maxParticles, elapsedTicks, mass, attraction, radius, gobblerMaxVelocity, &positionVelocity
        );
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
        let massDamping = 1.0 / (1.0 + mass);
        let boundaryRestitutionFactor = gobblerRestitution * massDamping;
        let minX = computeUniforms.viewportX + radius;
        let maxX = computeUniforms.viewportX + computeUniforms.viewportWidth - radius;
        let minY = computeUniforms.viewportY + radius;
        let maxY = computeUniforms.viewportY + computeUniforms.viewportHeight - radius;
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
      const blackHoleCoreMaxVelocity = 10.0;
      const blackHoleCoreRestitution = 0.0;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        positionVelocity.z = clamp(positionVelocity.z, -blackHoleCoreMaxVelocity, blackHoleCoreMaxVelocity);
        positionVelocity.w = clamp(positionVelocity.w, -blackHoleCoreMaxVelocity, blackHoleCoreMaxVelocity);
        var radius = max(lifecycle.z * 0.5, 1.0);
        let mass = attractionMassFor(particleKind, typeInfo, kindDataA);
        let attraction = attractionValueFor(particleKind, typeInfo, kindDataA);
        var attractionResult: AttractionResult;
        attractionResult.consumedBySomeone = 0.0;
        attractionResult.consumedMassTotal = 0.0;
        attractionResult.consumedAttractionTotal = 0.0;
        attractionResult.consumedAreaTotal = 0.0;
        if (isAttractionVisible(particleKind, lifecycle.x)) {
          attractionResult = applyGenericAttraction(
            index, maxParticles, elapsedTicks, mass, attraction, radius, blackHoleCoreMaxVelocity, &positionVelocity
          );
        }
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
        let massDamping = 1.0 / (1.0 + mass);
        let boundaryRestitutionFactor = blackHoleCoreRestitution * massDamping;
        let minX = computeUniforms.viewportX + radius;
        let maxX = computeUniforms.viewportX + computeUniforms.viewportWidth - radius;
        let minY = computeUniforms.viewportY + radius;
        let maxY = computeUniforms.viewportY + computeUniforms.viewportHeight - radius;
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
      const blackHoleExplosionDrag = 0.02;
      const blackHoleExplosionMaxVelocity = 10.0;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        let dragFactor = max(0.0, 1.0 - (blackHoleExplosionDrag * elapsedTicks));
        positionVelocity.z = positionVelocity.z * dragFactor;
        positionVelocity.w = positionVelocity.w * dragFactor;
        positionVelocity.z = clamp(positionVelocity.z, -blackHoleExplosionMaxVelocity, blackHoleExplosionMaxVelocity);
        positionVelocity.w = clamp(positionVelocity.w, -blackHoleExplosionMaxVelocity, blackHoleExplosionMaxVelocity);
        let radius = max(lifecycle.z * 0.5, 1.0);
        let mass = attractionMassFor(particleKind, typeInfo, kindDataA);
        let attraction = attractionValueFor(particleKind, typeInfo, kindDataA);
        let attractionResult = applyGenericAttraction(
          index, maxParticles, elapsedTicks, mass, attraction, radius, blackHoleExplosionMaxVelocity, &positionVelocity
        );
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
        if (attractionResult.consumedBySomeone > 0.5) {
          lifecycle.w = 0.0;
          typeInfo.w = 1.0;
        } else if (attractionResult.consumedAreaTotal > 0.0) {
          let newRadius = sqrt((radius * radius) + attractionResult.consumedAreaTotal);
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
    } else if (particleKind > 10.5 && particleKind <= 11.5) {
      const stressTestAttractionPerMass = 10.0;
      let myDelay = kindDataB.x;
      lifecycle.x = lifecycle.x + elapsedTicks;
      if (lifecycle.x >= lifecycle.y + myDelay) {
        lifecycle.w = 0.0;
      } else if (lifecycle.x < myDelay) {
        // still within spawn delay: frozen, not yet eligible to attract/merge/be attracted to
      } else {
        let myMass = abs(typeInfo.z);
        let gravityScale = min(1.0, myMass);
        positionVelocity.w = positionVelocity.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        positionVelocity.z = positionVelocity.z + (kindDataA.x * elapsedTicks);
        positionVelocity.w = positionVelocity.w + (kindDataA.y * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (kindDataA.z * elapsedTicks));
        positionVelocity.z = positionVelocity.z * dragFactor;
        positionVelocity.w = positionVelocity.w * dragFactor;
        let restitutionEncoding = typeInfo.w - select(0.0, 4.0, typeInfo.w >= 4.0);
        let viewportBound = restitutionEncoding >= 2.0;
        let myRestitution = restitutionEncoding - select(0.0, 2.0, viewportBound);
        let canCollide = typeInfo.z >= 0.0;
        let myRadius = max(lifecycle.z * 0.5, 1.0);
        let myAttraction = myMass * stressTestAttractionPerMass;
        var consumedBySomeone = false;
        var consumedMassTotal = 0.0;
        var consumedAreaTotal = 0.0;
        for (var otherIndex = 0u; otherIndex < maxParticles; otherIndex = otherIndex + 1u) {
          if (otherIndex == index) {
            continue;
          }
          let otherBase = otherIndex * 6u;
          let otherLifecycle = computeSourceParticles.data[otherBase + 1u];
          if (otherLifecycle.w <= 0.5) {
            continue;
          }
          let otherTypeInfo = computeSourceParticles.data[otherBase + 3u];
          if (otherTypeInfo.x <= 10.5 || otherTypeInfo.x > 11.5) {
            continue;
          }
          let otherKindDataB = computeSourceParticles.data[otherBase + 5u];
          if (otherLifecycle.x < otherKindDataB.x) {
            continue;
          }
          let otherCanCollide = otherTypeInfo.z >= 0.0;
          let otherMass = abs(otherTypeInfo.z);
          let otherAttraction = otherMass * stressTestAttractionPerMass;
          let otherPositionVelocity = computeSourceParticles.data[otherBase + 0u];
          let otherRadius = max(otherLifecycle.z * 0.5, 1.0);
          let deltaX = otherPositionVelocity.x - positionVelocity.x;
          let deltaY = otherPositionVelocity.y - positionVelocity.y;
          let distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
          if (distance <= 0.0) {
            continue;
          }
          let combinedRadius = myRadius + otherRadius;
          let attractionsDiffer = abs(myAttraction - otherAttraction) > 0.0001;
          var suppressedByAttraction = false;
          if (attractionsDiffer) {
            let attractionRange = max(myAttraction, otherAttraction);
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
                let pullMagnitude = otherAttraction * 0.5;
                positionVelocity.z = positionVelocity.z + ((deltaX / distance) * pullMagnitude * elapsedTicks);
                positionVelocity.w = positionVelocity.w + ((deltaY / distance) * pullMagnitude * elapsedTicks);
              }
            }
          }
          if (canCollide && otherCanCollide && !suppressedByAttraction && distance < combinedRadius) {
            let totalMass = myMass + otherMass;
            if (totalMass > 0.0) {
              let normalX = deltaX / distance;
              let normalY = deltaY / distance;
              let overlap = combinedRadius - distance;
              positionVelocity.x = positionVelocity.x - (normalX * overlap * (otherMass / totalMass));
              positionVelocity.y = positionVelocity.y - (normalY * overlap * (otherMass / totalMass));
              let relativeVelocityX = positionVelocity.z - otherPositionVelocity.z;
              let relativeVelocityY = positionVelocity.w - otherPositionVelocity.w;
              let impulse = 2.0 * ((normalX * relativeVelocityX) + (normalY * relativeVelocityY)) / totalMass;
              let otherRestitutionEncoding = otherTypeInfo.w - select(0.0, 4.0, otherTypeInfo.w >= 4.0);
              let otherViewportBound = otherRestitutionEncoding >= 2.0;
              let otherRestitution = otherRestitutionEncoding - select(0.0, 2.0, otherViewportBound);
              let restitution = min(myRestitution, otherRestitution);
              positionVelocity.z = positionVelocity.z - (impulse * otherMass * normalX * (1.0 + restitution));
              positionVelocity.w = positionVelocity.w - (impulse * otherMass * normalY * (1.0 + restitution));
            }
          }
        }
        let maxVelocity = kindDataA.w;
        if (maxVelocity > 0.0) {
          let speed = sqrt((positionVelocity.z * positionVelocity.z) + (positionVelocity.w * positionVelocity.w));
          if (speed > maxVelocity) {
            let speedScale = maxVelocity / speed;
            positionVelocity.z = positionVelocity.z * speedScale;
            positionVelocity.w = positionVelocity.w * speedScale;
          }
        }
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * elapsedTicks);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * elapsedTicks);
        if (viewportBound) {
          let halfSize = max(lifecycle.z * 0.5, 1.0);
          let massDamping = 1.0 / (1.0 + myMass);
          let boundaryRestitutionFactor = myRestitution * massDamping;
          let minX = computeUniforms.viewportX + halfSize;
          let maxX = computeUniforms.viewportX + computeUniforms.viewportWidth - halfSize;
          let minY = computeUniforms.viewportY + halfSize;
          let maxY = computeUniforms.viewportY + computeUniforms.viewportHeight - halfSize;
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
          let newRadius = sqrt((myRadius * myRadius) + consumedAreaTotal);
          let newMass = myMass + consumedMassTotal;
          lifecycle.z = newRadius * 2.0;
          typeInfo.z = select(-newMass, newMass, canCollide);
        }
      }
    } else {
      lifecycle.x = lifecycle.x + stepLifetime;
      if (lifecycle.x >= lifecycle.y) {
        lifecycle.w = 0.0;
      } else {
        positionVelocity.x = positionVelocity.x + (positionVelocity.z * stepSeconds);
        positionVelocity.y = positionVelocity.y + (positionVelocity.w * stepSeconds);
        positionVelocity.w = positionVelocity.w + (computeUniforms.gravity * computeUniforms.gravityBoost * stepSeconds);
      }
    }
  }
  computeDestinationParticles.data[base + 0u] = positionVelocity;
  computeDestinationParticles.data[base + 1u] = lifecycle;
  computeDestinationParticles.data[base + 2u] = colorRgba;
  computeDestinationParticles.data[base + 3u] = typeInfo;
  computeDestinationParticles.data[base + 4u] = kindDataA;
  computeDestinationParticles.data[base + 5u] = kindDataB;
}

struct RenderUniforms {
  viewport: vec4<f32>,
  renderScale: vec4<f32>,
};

struct VertexOutput {
  @builtin(position) position: vec4<f32>,
  @location(0) color: vec4<f32>,
  @location(1) quadCoordinate: vec2<f32>,
  @location(2) shapeFlag: f32,
  @location(3) uv: vec2<f32>,
  @location(4) particleKind: f32,
  @location(5) age: f32,
};

@group(0) @binding(0) var<storage, read> renderParticles: ParticleBuffer;
@group(0) @binding(1) var<uniform> renderUniforms: RenderUniforms;
@group(0) @binding(2) var renderSampler: sampler;
@group(0) @binding(3) var renderTexture: texture_2d<f32>;

const blobProjectileQuadSizeMultiplier = 1.4;
const itemReturnSpriteFrameCount = 8.0;
const itemReturnSpriteTicksPerFrame = 5.0;

fn blobProjectileBoundaryScale(angle: f32, age: f32) -> f32 {
  let wobbleA = sin((angle * 2.0) + (age * 0.05));
  let wobbleB = sin((angle * 3.0) + (age * 0.035) + 2.1);
  let wobbleC = sin((angle * 5.0) + (age * 0.07) + 4.2);
  return 1.0 + (0.12 * wobbleA) + (0.08 * wobbleB) + (0.06 * wobbleC);
}

fn quadCorner(vertexIndex: u32) -> vec2<f32> {
  switch(vertexIndex) {
    case 0u: { return vec2<f32>(-1.0, -1.0); }
    case 1u: { return vec2<f32>( 1.0, -1.0); }
    case 2u: { return vec2<f32>( 1.0,  1.0); }
    case 3u: { return vec2<f32>(-1.0, -1.0); }
    case 4u: { return vec2<f32>( 1.0,  1.0); }
    default: { return vec2<f32>(-1.0,  1.0); }
  }
}

@vertex
fn vertexMain(
  @builtin(vertex_index) vertexIndex: u32,
  @builtin(instance_index) instanceIndex: u32
) -> VertexOutput {
  let base = instanceIndex * 6u;
  let positionVelocity = renderParticles.data[base + 0u];
  let lifecycle = renderParticles.data[base + 1u];
  let colorRgba = renderParticles.data[base + 2u];
  let typeInfo = renderParticles.data[base + 3u];
  let kindDataA = renderParticles.data[base + 4u];
  var out: VertexOutput;
  if (lifecycle.w <= 0.5) {
    out.position = vec4<f32>(-2.0, -2.0, 0.0, 1.0);
    out.color = vec4<f32>(0.0, 0.0, 0.0, 0.0);
    out.quadCoordinate = vec2<f32>(0.0, 0.0);
    out.shapeFlag = 0.0;
    out.uv = vec2<f32>(0.0, 0.0);
    out.particleKind = 0.0;
    out.age = 0.0;
    return out;
  }
  let scale = renderUniforms.renderScale.x;
  let sizeScale = renderUniforms.renderScale.y;
  let particleKind = typeInfo.x;
  let isMapItemReturn = particleKind > 2.5 && particleKind <= 3.5;
  let isDust = particleKind > 0.5 && particleKind <= 1.5;
  let isBlobProjectile = particleKind > 11.5 && particleKind <= 12.5;
  let usesUnscaledSize = isMapItemReturn || isDust;
  let widthScale = select(sizeScale, 1.0, usesUnscaledSize);
  let baseWidth = select(lifecycle.z, renderUniforms.renderScale.z, isMapItemReturn);
  let baseHeight = select(lifecycle.z, renderUniforms.renderScale.w, isMapItemReturn);
  let blobSizeMultiplier = select(1.0, blobProjectileQuadSizeMultiplier, isBlobProjectile);
  let halfWidth = max(baseWidth * widthScale * scale * 0.5 * blobSizeMultiplier, 1.0);
  let halfHeight = max(baseHeight * widthScale * scale * 0.5 * blobSizeMultiplier, 1.0);
  let corner = quadCorner(vertexIndex);
  let localX = ((positionVelocity.x - renderUniforms.viewport.x) * scale) + (corner.x * halfWidth);
  let localY = ((positionVelocity.y - renderUniforms.viewport.y) * scale) + (corner.y * halfHeight);
  let x = (localX / renderUniforms.viewport.z) * 2.0 - 1.0;
  let y = (localY / renderUniforms.viewport.w) * 2.0 - 1.0;
  let lifetime = max(lifecycle.y, 1.0);
  let ageProgress = clamp(lifecycle.x / lifetime, 0.0, 1.0);
  let isFireworkBurst = particleKind > 4.5 && particleKind <= 5.5;
  let isFireworkTail = particleKind > 3.5 && particleKind <= 4.5;
  let isCollision = particleKind > 5.5 && particleKind <= 6.5;
  var resolvedAlpha: f32;
  if (isDust || isCollision) {
    resolvedAlpha = mix(colorRgba.w, typeInfo.w, ageProgress);
  } else {
    resolvedAlpha = colorRgba.w;
  }
  var resolvedColor = vec3<f32>(colorRgba.x, colorRgba.y, colorRgba.z);
  if (isFireworkBurst) {
    let packedEndColor = typeInfo.z;
    let endColorRed = floor(packedEndColor / 65536.0);
    let endColorGreen = floor((packedEndColor - (endColorRed * 65536.0)) / 256.0);
    let endColorBlue = packedEndColor - (endColorRed * 65536.0) - (endColorGreen * 256.0);
    let endColor = vec3<f32>(endColorRed / 255.0, endColorGreen / 255.0, endColorBlue / 255.0);
    resolvedColor = mix(resolvedColor, endColor, ageProgress);
  }
  out.position = vec4<f32>(x, -y, 0.0, 1.0);
  out.color = vec4<f32>(resolvedColor, resolvedAlpha);
  out.quadCoordinate = corner;
  out.shapeFlag = typeInfo.y;
  let localU = (corner.x + 1.0) * 0.5;
  let localV = (corner.y + 1.0) * 0.5;
  let itemReturnMaxFrames = max(kindDataA.x, 1.0);
  let itemReturnFrameIndex = floor(lifecycle.x / itemReturnSpriteTicksPerFrame) % itemReturnMaxFrames;
  let itemReturnUvX = (localU + itemReturnFrameIndex) / itemReturnSpriteFrameCount;
  out.uv = vec2<f32>(select(localU, itemReturnUvX, isMapItemReturn), localV);
  out.particleKind = particleKind;
  out.age = lifecycle.x;
  return out;
}

fn shouldDiscardCircle(shapeFlag: f32, quadCoordinate: vec2<f32>) -> bool {
  return shapeFlag > 0.5 && dot(quadCoordinate, quadCoordinate) > 1.0;
}

fn shouldDiscardBlobProjectile(quadCoordinate: vec2<f32>, age: f32) -> bool {
  let pixelDistance = length(quadCoordinate) * blobProjectileQuadSizeMultiplier;
  let pixelAngle = atan2(quadCoordinate.y, quadCoordinate.x);
  let boundaryScale = blobProjectileBoundaryScale(pixelAngle, age);
  return pixelDistance > boundaryScale;
}

@fragment
fn fragmentMainNonDust(
  @location(0) color: vec4<f32>,
  @location(1) quadCoordinate: vec2<f32>,
  @location(2) shapeFlag: f32,
  @location(3) uv: vec2<f32>,
  @location(4) particleKind: f32,
  @location(5) age: f32
) -> @location(0) vec4<f32> {
  let isDust = particleKind > 0.5 && particleKind <= 1.5;
  if (isDust) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  if (particleKind > 2.5 && particleKind <= 3.5) {
    let sampled = textureSampleLevel(renderTexture, renderSampler, uv, 0.0);
    return sampled * color;
  }
  if (particleKind > 11.5 && particleKind <= 12.5) {
    if (shouldDiscardBlobProjectile(quadCoordinate, age)) {
      return vec4<f32>(0.0, 0.0, 0.0, 0.0);
    }
    return color;
  }
  if (shouldDiscardCircle(shapeFlag, quadCoordinate)) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return color;
}

@fragment
fn fragmentMainDust(
  @location(0) color: vec4<f32>,
  @location(1) quadCoordinate: vec2<f32>,
  @location(2) shapeFlag: f32,
  @location(3) uv: vec2<f32>,
  @location(4) particleKind: f32,
  @location(5) age: f32
) -> @location(0) vec4<f32> {
  let _layoutAnchorSample = textureSampleLevel(renderTexture, renderSampler, uv, 0.0);
  let isDust = particleKind > 0.5 && particleKind <= 1.5;
  if (!isDust) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  if (shouldDiscardCircle(shapeFlag, quadCoordinate)) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return vec4<f32>(color.rgb * color.a, color.a);
}

