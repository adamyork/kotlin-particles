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

@group(0) @binding(0) var<storage, read> computeSrcParticles: ParticleBuffer;
@group(0) @binding(1) var<storage, read_write> computeDstParticles: ParticleBuffer;
@group(0) @binding(2) var<storage, read> computeSpawnParticles: ParticleBuffer;
@group(0) @binding(3) var<uniform> computeUniforms: ComputeUniforms;
@group(0) @binding(4) var<storage, read_write> collisionSignal: CollisionSignalBuffer;

@compute @workgroup_size(64)
fn computeMain(@builtin(global_invocation_id) gid: vec3<u32>) {
  let index = gid.x;
  let maxParticles = u32(computeUniforms.maxParticles);
  if (index >= maxParticles) {
    return;
  }

  let base = index * 4u;

  var p0 = computeSrcParticles.data[base + 0u];
  var p1 = computeSrcParticles.data[base + 1u];
  var p2 = computeSrcParticles.data[base + 2u];
  var p3 = computeSrcParticles.data[base + 3u];

  let s0 = computeSpawnParticles.data[base + 0u];
  let s1 = computeSpawnParticles.data[base + 1u];
  let s2 = computeSpawnParticles.data[base + 2u];
  let s3 = computeSpawnParticles.data[base + 3u];

  if (s1.w > 0.5) {
    let spawnKind = s3.x;
    let wasGobblerOrBlackHoleExplosion = (p3.x > 7.5 && p3.x <= 8.5) || (p3.x > 9.5 && p3.x <= 10.5);
    let wasStressTest = p3.x > 10.5 && p3.x <= 11.5;
    let isTombstoned = (wasGobblerOrBlackHoleExplosion && p3.w > 0.5) ||
        (wasStressTest && p3.w >= 4.0);
    let isFreshSpawnInstance = s1.x < 1.0;
    let preserveGpuState = (spawnKind > 1.5 && p1.w > 0.5) ||
        (isTombstoned && !isFreshSpawnInstance);
    if (!preserveGpuState) {
      p0 = s0;
      p1 = s1;
      p2 = s2;
      p3 = s3;
    }
  }

  if (p1.w > 0.5) {
    let particleKind = p3.x;
    let stepSeconds = computeUniforms.deltaTime * computeUniforms.simulationSpeed;
    let stepLifetime = computeUniforms.deltaTime * computeUniforms.tickRate * computeUniforms.lifetimeDecay;
    let frameStep = max(1.0, round(computeUniforms.deltaTime * computeUniforms.tickRate));
    let elapsedTicks = computeUniforms.deltaTime * computeUniforms.tickRate;
    if (particleKind > 4.5 && particleKind <= 5.5) {
      const fireworkBurstYAcceleration = 0.05;
      const fireworkBurstMass = 0.02;
      const fireworkBurstDrag = 0.02;
      const fireworkBurstMaxVelocity = 10.0;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        p0.w = p0.w + (fireworkBurstYAcceleration * elapsedTicks);
        let gravityScale = min(1.0, fireworkBurstMass);
        p0.w = p0.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (fireworkBurstDrag * elapsedTicks));
        p0.z = p0.z * dragFactor;
        p0.w = p0.w * dragFactor;
        p0.z = clamp(p0.z, -fireworkBurstMaxVelocity, fireworkBurstMaxVelocity);
        p0.w = clamp(p0.w, -fireworkBurstMaxVelocity, fireworkBurstMaxVelocity);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);
      }
    } else if (particleKind > 3.5 && particleKind <= 4.5) {
      const fireworkTailMass = 0.02;
      p1.x = p1.x + elapsedTicks;
      let tailDelay = max(p3.w, 0.0);
      let tailTotalLifetime = p1.y + tailDelay;
      if (p1.x >= tailTotalLifetime) {
        p1.w = 0.0;
      } else if (p1.x >= tailDelay) {
        let gravityScale = min(1.0, fireworkTailMass);
        p0.w = p0.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let maxYVelocity = max(p3.z, 1.0);
        p0.w = clamp(p0.w, -maxYVelocity, maxYVelocity);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);
      }
    } else if (particleKind > 2.5 && particleKind <= 3.5) {
      const itemReturnThrust = 0.16;
      const itemReturnMaxSpeed = 5.25;
      const itemReturnVelocityHeadroom = 0.2;
      const itemReturnMass = 0.15;
      const itemReturnDrag = 0.001;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        let unitX = p3.z;
        let unitY = p3.w;
        let maxVelocityX = (abs(unitX) * itemReturnMaxSpeed) + itemReturnVelocityHeadroom;
        let maxVelocityY = (abs(unitY) * itemReturnMaxSpeed) + itemReturnVelocityHeadroom;
        p0.z = p0.z + (unitX * itemReturnThrust * elapsedTicks);
        p0.w = p0.w + (unitY * itemReturnThrust * elapsedTicks);
        let gravityScale = min(1.0, itemReturnMass);
        p0.w = p0.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (itemReturnDrag * elapsedTicks));
        p0.z = p0.z * dragFactor;
        p0.w = p0.w * dragFactor;
        p0.z = clamp(p0.z, -maxVelocityX, maxVelocityX);
        p0.w = clamp(p0.w, -maxVelocityY, maxVelocityY);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);
      }
    } else if (particleKind > 1.5 && particleKind <= 2.5) {
      const projectileThrust = 0.16;
      const projectileMaxSpeed = 5.25;
      const projectileVelocityHeadroom = 0.2;
      const projectileMass = 0.15;
      const projectileDrag = 0.001;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        let unitX = p3.z;
        let unitY = p3.w;
        let maxVelocityX = (abs(unitX) * projectileMaxSpeed) + projectileVelocityHeadroom;
        let maxVelocityY = (abs(unitY) * projectileMaxSpeed) + projectileVelocityHeadroom;
        p0.z = p0.z + (unitX * projectileThrust * elapsedTicks);
        p0.w = p0.w + (unitY * projectileThrust * elapsedTicks);
        let gravityScale = min(1.0, projectileMass);
        p0.w = p0.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (projectileDrag * elapsedTicks));
        p0.z = p0.z * dragFactor;
        p0.w = p0.w * dragFactor;
        p0.z = clamp(p0.z, -maxVelocityX, maxVelocityX);
        p0.w = clamp(p0.w, -maxVelocityY, maxVelocityY);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);

        let playerMinX = computeUniforms.playerX;
        let playerMinY = computeUniforms.playerY;
        let playerMaxX = playerMinX + max(computeUniforms.playerWidth, 1.0);
        let playerMaxY = playerMinY + max(computeUniforms.playerHeight, 1.0);
        let nearestX = clamp(p0.x, playerMinX, playerMaxX);
        let nearestY = clamp(p0.y, playerMinY, playerMaxY);
        let dx = p0.x - nearestX;
        let dy = p0.y - nearestY;
        let projectileRadius = max(p1.z * 0.5, 1.0);
        if ((dx * dx) + (dy * dy) <= (projectileRadius * projectileRadius)) {
          p1.w = 0.0;
          let previousHitCount = atomicAdd(&collisionSignal.projectileHitCount, 1u);
          if (previousHitCount == 0u) {
            atomicStore(&collisionSignal.projectileHitXBits, bitcast<u32>(p0.x));
            atomicStore(&collisionSignal.projectileHitYBits, bitcast<u32>(p0.y));
            atomicStore(&collisionSignal.projectileHitSizeBits, bitcast<u32>(max(p1.z, 1.0)));
          }
        }
      }
    } else if (particleKind > 5.5 && particleKind <= 6.5) {
      const collisionMassScale = 0.04;
      const collisionDrag = 0.01;
      const collisionMaxVelocity = 10.0;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        let radius = p1.z * 0.5;
        let mass = radius * collisionMassScale;
        let gravityScale = min(1.0, mass);
        p0.w = p0.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        let dragFactor = max(0.0, 1.0 - (collisionDrag * elapsedTicks));
        p0.z = p0.z * dragFactor;
        p0.w = p0.w * dragFactor;
        p0.z = clamp(p0.z, -collisionMaxVelocity, collisionMaxVelocity);
        p0.w = clamp(p0.w, -collisionMaxVelocity, collisionMaxVelocity);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);
      }
    } else if (particleKind > 6.5 && particleKind <= 7.5) {
      const collidingBitsMass = 0.12;
      const collidingBitsRestitution = 0.6;
      const collidingBitsMaxVelocity = 10.0;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        let gravityScale = min(1.0, collidingBitsMass);
        p0.w = p0.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        p0.z = clamp(p0.z, -collidingBitsMaxVelocity, collidingBitsMaxVelocity);
        p0.w = clamp(p0.w, -collidingBitsMaxVelocity, collidingBitsMaxVelocity);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);

        let halfSize = max(p1.z * 0.5, 1.0);
        let massDamping = 1.0 / (1.0 + collidingBitsMass);
        let boundaryRestitutionFactor = collidingBitsRestitution * massDamping;
        let minX = computeUniforms.viewportX + halfSize;
        let maxX = computeUniforms.viewportX + computeUniforms.viewportWidth - halfSize;
        let minY = computeUniforms.viewportY + halfSize;
        let maxY = computeUniforms.viewportY + computeUniforms.viewportHeight - halfSize;
        if (p0.x < minX) {
          p0.x = minX + (minX - p0.x);
          p0.z = -p0.z * boundaryRestitutionFactor;
        } else if (p0.x > maxX) {
          p0.x = maxX - (p0.x - maxX);
          p0.z = -p0.z * boundaryRestitutionFactor;
        }
        if (p0.y < minY) {
          p0.y = minY + (minY - p0.y);
          p0.w = -p0.w * boundaryRestitutionFactor;
        } else if (p0.y > maxY) {
          p0.y = maxY - (p0.y - maxY);
          p0.w = -p0.w * boundaryRestitutionFactor;
        }

        let myRadius = halfSize;
        for (var otherIndex = 0u; otherIndex < maxParticles; otherIndex = otherIndex + 1u) {
          if (otherIndex == index) {
            continue;
          }
          let otherBase = otherIndex * 4u;
          let otherP1 = computeSrcParticles.data[otherBase + 1u];
          if (otherP1.w <= 0.5) {
            continue;
          }
          let otherP3 = computeSrcParticles.data[otherBase + 3u];
          if (otherP3.x <= 6.5 || otherP3.x > 7.5) {
            continue;
          }
          let otherP0 = computeSrcParticles.data[otherBase + 0u];
          let otherRadius = max(otherP1.z * 0.5, 1.0);
          let deltaX = otherP0.x - p0.x;
          let deltaY = otherP0.y - p0.y;
          let distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
          let minimumDistance = myRadius + otherRadius;
          if (distance > 0.0 && distance < minimumDistance) {
            let normalX = deltaX / distance;
            let normalY = deltaY / distance;
            let overlap = minimumDistance - distance;
            p0.x = p0.x - (normalX * overlap * 0.5);
            p0.y = p0.y - (normalY * overlap * 0.5);
            let relativeVelocityX = p0.z - otherP0.z;
            let relativeVelocityY = p0.w - otherP0.w;
            let dotProduct = (normalX * relativeVelocityX) + (normalY * relativeVelocityY);
            p0.z = p0.z - (dotProduct * normalX * (1.0 + collidingBitsRestitution));
            p0.w = p0.w - (dotProduct * normalY * (1.0 + collidingBitsRestitution));
          }
        }
      }
    } else if (particleKind > 7.5 && particleKind <= 8.5) {
      const gobblerDrag = 0.002;
      const gobblerMaxVelocity = 6.0;
      const gobblerMassPerRadiusSquared = 1.0 / 20480.0;
      const gobblerAttractionPerMass = 480.0;
      const gobblerRestitution = 1.0;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        let dragFactor = max(0.0, 1.0 - (gobblerDrag * elapsedTicks));
        p0.z = p0.z * dragFactor;
        p0.w = p0.w * dragFactor;
        p0.z = clamp(p0.z, -gobblerMaxVelocity, gobblerMaxVelocity);
        p0.w = clamp(p0.w, -gobblerMaxVelocity, gobblerMaxVelocity);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);

        var radius = max(p1.z * 0.5, 1.0);
        let mass = radius * radius * gobblerMassPerRadiusSquared;
        let massDamping = 1.0 / (1.0 + mass);
        let boundaryRestitutionFactor = gobblerRestitution * massDamping;
        let minX = computeUniforms.viewportX + radius;
        let maxX = computeUniforms.viewportX + computeUniforms.viewportWidth - radius;
        let minY = computeUniforms.viewportY + radius;
        let maxY = computeUniforms.viewportY + computeUniforms.viewportHeight - radius;
        if (p0.x < minX) {
          p0.x = minX + (minX - p0.x);
          p0.z = -p0.z * boundaryRestitutionFactor;
        } else if (p0.x > maxX) {
          p0.x = maxX - (p0.x - maxX);
          p0.z = -p0.z * boundaryRestitutionFactor;
        }
        if (p0.y < minY) {
          p0.y = minY + (minY - p0.y);
          p0.w = -p0.w * boundaryRestitutionFactor;
        } else if (p0.y > maxY) {
          p0.y = maxY - (p0.y - maxY);
          p0.w = -p0.w * boundaryRestitutionFactor;
        }

        let attraction = mass * gobblerAttractionPerMass;
        var consumedBySomeone = false;
        var consumedAreaTotal = 0.0;

        for (var otherIndex = 0u; otherIndex < maxParticles; otherIndex = otherIndex + 1u) {
          if (otherIndex == index) {
            continue;
          }
          let otherBase = otherIndex * 4u;
          let otherP1 = computeSrcParticles.data[otherBase + 1u];
          if (otherP1.w <= 0.5) {
            continue;
          }
          let otherP3 = computeSrcParticles.data[otherBase + 3u];
          if (otherP3.x <= 7.5 || otherP3.x > 8.5) {
            continue;
          }
          let otherP0 = computeSrcParticles.data[otherBase + 0u];
          let otherRadius = max(otherP1.z * 0.5, 1.0);
          let otherMass = otherRadius * otherRadius * gobblerMassPerRadiusSquared;
          let deltaX = otherP0.x - p0.x;
          let deltaY = otherP0.y - p0.y;
          let distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
          if (distance <= 0.0) {
            continue;
          }
          let combinedRadius = radius + otherRadius;
          if (distance <= combinedRadius) {
            if (otherMass > mass) {
              consumedBySomeone = true;
            } else if (mass > otherMass) {
              consumedAreaTotal = consumedAreaTotal + (otherRadius * otherRadius);
            }
          } else {
            let otherAttraction = otherMass * gobblerAttractionPerMass;
            if (attraction < otherAttraction) {
              let strongerAttraction = otherAttraction;
              if (abs(deltaX) <= strongerAttraction && abs(deltaY) <= strongerAttraction) {
                let pullMagnitude = strongerAttraction * 0.5;
                p0.z = p0.z + ((deltaX / distance) * pullMagnitude * elapsedTicks);
                p0.w = p0.w + ((deltaY / distance) * pullMagnitude * elapsedTicks);
                let pulledSpeed = sqrt((p0.z * p0.z) + (p0.w * p0.w));
                if (pulledSpeed > gobblerMaxVelocity) {
                  let pulledSpeedScale = gobblerMaxVelocity / pulledSpeed;
                  p0.z = p0.z * pulledSpeedScale;
                  p0.w = p0.w * pulledSpeedScale;
                }
              }
            }
          }
        }

        if (consumedBySomeone) {
          p1.w = 0.0;
          p3.w = 1.0;
        } else if (consumedAreaTotal > 0.0) {
          radius = sqrt((radius * radius) + consumedAreaTotal);
          p1.z = radius * 2.0;
        }
      }
    } else if (particleKind > 8.5 && particleKind <= 9.5) {
      const blackHoleCoreDelay = 60.0;
      const blackHoleCoreMaxVelocity = 10.0;
      const blackHoleCoreRestitution = 0.0;
      const blackHoleExplosionRadius = 2.0;
      const blackHoleExplosionMass = 0.05;
      const blackHoleExplosionAttraction = 1.0;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        p0.z = clamp(p0.z, -blackHoleCoreMaxVelocity, blackHoleCoreMaxVelocity);
        p0.w = clamp(p0.w, -blackHoleCoreMaxVelocity, blackHoleCoreMaxVelocity);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);

        var radius = max(p1.z * 0.5, 1.0);
        var mass = max(p3.z, 1.0);
        var attraction = max(p3.w, 0.0);
        let massDamping = 1.0 / (1.0 + mass);
        let boundaryRestitutionFactor = blackHoleCoreRestitution * massDamping;
        let minX = computeUniforms.viewportX + radius;
        let maxX = computeUniforms.viewportX + computeUniforms.viewportWidth - radius;
        let minY = computeUniforms.viewportY + radius;
        let maxY = computeUniforms.viewportY + computeUniforms.viewportHeight - radius;
        if (p0.x < minX) {
          p0.x = minX + (minX - p0.x);
          p0.z = -p0.z * boundaryRestitutionFactor;
        } else if (p0.x > maxX) {
          p0.x = maxX - (p0.x - maxX);
          p0.z = -p0.z * boundaryRestitutionFactor;
        }
        if (p0.y < minY) {
          p0.y = minY + (minY - p0.y);
          p0.w = -p0.w * boundaryRestitutionFactor;
        } else if (p0.y > maxY) {
          p0.y = maxY - (p0.y - maxY);
          p0.w = -p0.w * boundaryRestitutionFactor;
        }

        if (p1.x >= blackHoleCoreDelay) {
          var consumedCount = 0.0;
          for (var otherIndex = 0u; otherIndex < maxParticles; otherIndex = otherIndex + 1u) {
            if (otherIndex == index) {
              continue;
            }
            let otherBase = otherIndex * 4u;
            let otherP1 = computeSrcParticles.data[otherBase + 1u];
            if (otherP1.w <= 0.5) {
              continue;
            }
            let otherP3 = computeSrcParticles.data[otherBase + 3u];
            if (otherP3.x <= 9.5 || otherP3.x > 10.5) {
              continue;
            }
            let otherP0 = computeSrcParticles.data[otherBase + 0u];
            let deltaX = otherP0.x - p0.x;
            let deltaY = otherP0.y - p0.y;
            let distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
            if (distance <= 0.0) {
              continue;
            }
            if (distance <= radius + blackHoleExplosionRadius) {
              consumedCount = consumedCount + 1.0;
            }
          }

          if (consumedCount > 0.0) {
            let consumedAreaTotal = consumedCount * (blackHoleExplosionRadius * blackHoleExplosionRadius);
            radius = sqrt((radius * radius) + consumedAreaTotal);
            mass = mass + (consumedCount * blackHoleExplosionMass);
            attraction = attraction + (consumedCount * blackHoleExplosionAttraction);
            p1.z = radius * 2.0;
            p3.z = mass;
            p3.w = attraction;
          }
        }
      }
    } else if (particleKind > 9.5 && particleKind <= 10.5) {
      const blackHoleCoreDelay = 60.0;
      const blackHoleExplosionDrag = 0.02;
      const blackHoleExplosionMaxVelocity = 10.0;
      const blackHoleExplosionRadius = 2.0;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        let dragFactor = max(0.0, 1.0 - (blackHoleExplosionDrag * elapsedTicks));
        p0.z = p0.z * dragFactor;
        p0.w = p0.w * dragFactor;
        p0.z = clamp(p0.z, -blackHoleExplosionMaxVelocity, blackHoleExplosionMaxVelocity);
        p0.w = clamp(p0.w, -blackHoleExplosionMaxVelocity, blackHoleExplosionMaxVelocity);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);

        for (var otherIndex = 0u; otherIndex < maxParticles; otherIndex = otherIndex + 1u) {
          if (otherIndex == index) {
            continue;
          }
          let otherBase = otherIndex * 4u;
          let otherP1 = computeSrcParticles.data[otherBase + 1u];
          if (otherP1.w <= 0.5) {
            continue;
          }
          let otherP3 = computeSrcParticles.data[otherBase + 3u];
          if (otherP3.x <= 8.5 || otherP3.x > 9.5) {
            continue;
          }
          if (otherP1.x < blackHoleCoreDelay) {
            continue;
          }
          let otherP0 = computeSrcParticles.data[otherBase + 0u];
          let otherRadius = max(otherP1.z * 0.5, 1.0);
          let otherAttraction = max(otherP3.w, 0.0);
          let deltaX = otherP0.x - p0.x;
          let deltaY = otherP0.y - p0.y;
          let distance = sqrt((deltaX * deltaX) + (deltaY * deltaY));
          if (distance <= 0.0) {
            continue;
          }
          if (distance <= blackHoleExplosionRadius + otherRadius) {
            p1.w = 0.0;
            p3.w = 1.0;
          } else if (abs(deltaX) <= otherAttraction && abs(deltaY) <= otherAttraction) {
            let pullMagnitude = otherAttraction * 0.5;
            p0.z = p0.z + ((deltaX / distance) * pullMagnitude * elapsedTicks);
            p0.w = p0.w + ((deltaY / distance) * pullMagnitude * elapsedTicks);
            let pulledSpeed = sqrt((p0.z * p0.z) + (p0.w * p0.w));
            if (pulledSpeed > blackHoleExplosionMaxVelocity) {
              let pulledSpeedScale = blackHoleExplosionMaxVelocity / pulledSpeed;
              p0.z = p0.z * pulledSpeedScale;
              p0.w = p0.w * pulledSpeedScale;
            }
          }
        }
      }
    } else if (particleKind > 0.5 && particleKind <= 1.5) {
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);
      }
    } else if (particleKind > 10.5 && particleKind <= 11.5) {
      const stressTestMaxVelocity = 12.0;
      const stressTestAttractionPerMass = 10.0;
      p1.x = p1.x + elapsedTicks;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        let myMass = abs(p3.z);
        let gravityScale = min(1.0, myMass);
        p0.w = p0.w + (computeUniforms.gravity * gravityScale * elapsedTicks);
        p0.x = p0.x + (p0.z * elapsedTicks);
        p0.y = p0.y + (p0.w * elapsedTicks);

        let afterTombstoneBand = p3.w - select(0.0, 4.0, p3.w >= 4.0);
        let viewportBound = afterTombstoneBand >= 2.0;
        let myRestitution = afterTombstoneBand - select(0.0, 2.0, viewportBound);

        if (viewportBound) {
          let halfSize = max(p1.z * 0.5, 1.0);
          let massDamping = 1.0 / (1.0 + myMass);
          let boundaryRestitutionFactor = myRestitution * massDamping;
          let minX = computeUniforms.viewportX + halfSize;
          let maxX = computeUniforms.viewportX + computeUniforms.viewportWidth - halfSize;
          let minY = computeUniforms.viewportY + halfSize;
          let maxY = computeUniforms.viewportY + computeUniforms.viewportHeight - halfSize;
          if (p0.x < minX) {
            p0.x = minX + (minX - p0.x);
            p0.z = -p0.z * boundaryRestitutionFactor;
          } else if (p0.x > maxX) {
            p0.x = maxX - (p0.x - maxX);
            p0.z = -p0.z * boundaryRestitutionFactor;
          }
          if (p0.y < minY) {
            p0.y = minY + (minY - p0.y);
            p0.w = -p0.w * boundaryRestitutionFactor;
          } else if (p0.y > maxY) {
            p0.y = maxY - (p0.y - maxY);
            p0.w = -p0.w * boundaryRestitutionFactor;
          }
        }

        let canCollide = p3.z >= 0.0;
        let myRadius = max(p1.z * 0.5, 1.0);
        let myAttraction = myMass * stressTestAttractionPerMass;
        var consumedBySomeone = false;
        var consumedMassTotal = 0.0;
        var consumedAreaTotal = 0.0;

        for (var otherIndex = 0u; otherIndex < maxParticles; otherIndex = otherIndex + 1u) {
          if (otherIndex == index) {
            continue;
          }
          let otherBase = otherIndex * 4u;
          let otherP1 = computeSrcParticles.data[otherBase + 1u];
          if (otherP1.w <= 0.5) {
            continue;
          }
          let otherP3 = computeSrcParticles.data[otherBase + 3u];
          if (otherP3.x <= 10.5 || otherP3.x > 11.5) {
            continue;
          }
          let otherCanCollide = otherP3.z >= 0.0;
          let otherMass = abs(otherP3.z);
          let otherAttraction = otherMass * stressTestAttractionPerMass;
          let otherP0 = computeSrcParticles.data[otherBase + 0u];
          let otherRadius = max(otherP1.z * 0.5, 1.0);
          let deltaX = otherP0.x - p0.x;
          let deltaY = otherP0.y - p0.y;
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
                p0.z = p0.z + ((deltaX / distance) * pullMagnitude * elapsedTicks);
                p0.w = p0.w + ((deltaY / distance) * pullMagnitude * elapsedTicks);
              }
            }
          }

          if (canCollide && otherCanCollide && !suppressedByAttraction && distance < combinedRadius) {
            let totalMass = myMass + otherMass;
            if (totalMass > 0.0) {
              let normalX = deltaX / distance;
              let normalY = deltaY / distance;
              let overlap = combinedRadius - distance;
              p0.x = p0.x - (normalX * overlap * (otherMass / totalMass));
              p0.y = p0.y - (normalY * overlap * (otherMass / totalMass));
              let relativeVelocityX = p0.z - otherP0.z;
              let relativeVelocityY = p0.w - otherP0.w;
              let impulse = 2.0 * ((normalX * relativeVelocityX) + (normalY * relativeVelocityY)) / totalMass;
              let otherRestitutionBand = otherP3.w - select(0.0, 4.0, otherP3.w >= 4.0);
              let otherViewportBound = otherRestitutionBand >= 2.0;
              let otherRestitution = otherRestitutionBand - select(0.0, 2.0, otherViewportBound);
              let restitution = min(myRestitution, otherRestitution);
              p0.z = p0.z - (impulse * otherMass * normalX * (1.0 + restitution));
              p0.w = p0.w - (impulse * otherMass * normalY * (1.0 + restitution));
            }
          }
        }

        if (consumedBySomeone) {
          p1.w = 0.0;
          p3.w = p3.w + 4.0;
        } else if (consumedMassTotal > 0.0) {
          let newRadius = sqrt((myRadius * myRadius) + consumedAreaTotal);
          let newMass = myMass + consumedMassTotal;
          p1.z = newRadius * 2.0;
          p3.z = select(-newMass, newMass, canCollide);
        }
        p0.z = clamp(p0.z, -stressTestMaxVelocity, stressTestMaxVelocity);
        p0.w = clamp(p0.w, -stressTestMaxVelocity, stressTestMaxVelocity);
      }
    } else {
      p1.x = p1.x + stepLifetime;
      if (p1.x >= p1.y) {
        p1.w = 0.0;
      } else {
        p0.x = p0.x + (p0.z * stepSeconds);
        p0.y = p0.y + (p0.w * stepSeconds);
        p0.w = p0.w + (computeUniforms.gravity * computeUniforms.gravityBoost * stepSeconds);
      }
    }
  }

  computeDstParticles.data[base + 0u] = p0;
  computeDstParticles.data[base + 1u] = p1;
  computeDstParticles.data[base + 2u] = p2;
  computeDstParticles.data[base + 3u] = p3;
}

struct RenderUniforms {
  viewport: vec4<f32>,
  renderScale: vec4<f32>,
};

struct VertexOut {
  @builtin(position) position: vec4<f32>,
  @location(0) color: vec4<f32>,
  @location(1) quadCoord: vec2<f32>,
  @location(2) shapeFlag: f32,
  @location(3) uv: vec2<f32>,
  @location(4) particleKind: f32,
};

@group(0) @binding(0) var<storage, read> renderParticles: ParticleBuffer;
@group(0) @binding(1) var<uniform> renderUniforms: RenderUniforms;
@group(0) @binding(2) var renderSampler: sampler;
@group(0) @binding(3) var renderTexture: texture_2d<f32>;

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
) -> VertexOut {
  let base = instanceIndex * 4u;
  let p0 = renderParticles.data[base + 0u];
  let p1 = renderParticles.data[base + 1u];
  let p2 = renderParticles.data[base + 2u];
  let p3 = renderParticles.data[base + 3u];

  var out: VertexOut;
  if (p1.w <= 0.5) {
    out.position = vec4<f32>(-2.0, -2.0, 0.0, 1.0);
    out.color = vec4<f32>(0.0, 0.0, 0.0, 0.0);
    out.quadCoord = vec2<f32>(0.0, 0.0);
    out.shapeFlag = 0.0;
    out.uv = vec2<f32>(0.0, 0.0);
    out.particleKind = 0.0;
    return out;
  }

  let scale = renderUniforms.renderScale.x;
  let sizeScale = renderUniforms.renderScale.y;
  let particleKind = p3.x;
  let isMapItemReturn = particleKind > 2.5 && particleKind <= 3.5;
  let isDust = particleKind > 0.5 && particleKind <= 1.5;
  let usesUnscaledSize = isMapItemReturn || isDust;
  let widthScale = select(sizeScale, 1.0, usesUnscaledSize);
  let baseWidth = select(p1.z, renderUniforms.renderScale.z, isMapItemReturn);
  let baseHeight = select(p1.z, renderUniforms.renderScale.w, isMapItemReturn);
  let halfWidth = max(baseWidth * widthScale * scale * 0.5, 1.0);
  let halfHeight = max(baseHeight * widthScale * scale * 0.5, 1.0);
  let corner = quadCorner(vertexIndex);
  let localX = ((p0.x - renderUniforms.viewport.x) * scale) + (corner.x * halfWidth);
  let localY = ((p0.y - renderUniforms.viewport.y) * scale) + (corner.y * halfHeight);
  let x = (localX / renderUniforms.viewport.z) * 2.0 - 1.0;
  let y = (localY / renderUniforms.viewport.w) * 2.0 - 1.0;
  let lifetime = max(p1.y, 1.0);
  let ageProgress = clamp(p1.x / lifetime, 0.0, 1.0);
  let isFireworkBurst = particleKind > 4.5 && particleKind <= 5.5;
  let isFireworkTail = particleKind > 3.5 && particleKind <= 4.5;
  var resolvedAlpha: f32;
  if (isDust) {
    resolvedAlpha = mix(p2.w, p3.w, ageProgress);
  } else if (isFireworkBurst || isFireworkTail) {
    resolvedAlpha = p2.w;
  } else {
    var alphaMultiplier = 1.0;
    if (particleKind > 1.5 && particleKind <= 2.5) {
      alphaMultiplier = 1.0;
    } else if (ageProgress < 0.33) {
      alphaMultiplier = 1.0;
    } else if (ageProgress < 0.66) {
      alphaMultiplier = 0.66;
    } else {
      alphaMultiplier = 0.33;
    }
    resolvedAlpha = p2.w * alphaMultiplier;
  }

  var resolvedColor = vec3<f32>(p2.x, p2.y, p2.z);
  if (isFireworkBurst) {
    let packedEndColor = p3.z;
    let endColorRed = floor(packedEndColor / 65536.0);
    let endColorGreen = floor((packedEndColor - (endColorRed * 65536.0)) / 256.0);
    let endColorBlue = packedEndColor - (endColorRed * 65536.0) - (endColorGreen * 256.0);
    let endColor = vec3<f32>(endColorRed / 255.0, endColorGreen / 255.0, endColorBlue / 255.0);
    resolvedColor = mix(resolvedColor, endColor, ageProgress);
  }

  out.position = vec4<f32>(x, -y, 0.0, 1.0);
  out.color = vec4<f32>(resolvedColor, resolvedAlpha);
  out.quadCoord = corner;
  out.shapeFlag = p3.y;
  out.uv = vec2<f32>((corner.x + 1.0) * 0.5, (corner.y + 1.0) * 0.5);
  out.particleKind = particleKind;
  return out;
}

fn shouldDiscardCircle(shapeFlag: f32, quadCoord: vec2<f32>) -> bool {
  return shapeFlag > 0.5 && dot(quadCoord, quadCoord) > 1.0;
}

@fragment
fn fragmentMainNonDust(
  @location(0) color: vec4<f32>,
  @location(1) quadCoord: vec2<f32>,
  @location(2) shapeFlag: f32,
  @location(3) uv: vec2<f32>,
  @location(4) particleKind: f32
) -> @location(0) vec4<f32> {
  let isDust = particleKind > 0.5 && particleKind <= 1.5;
  if (isDust) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  if (particleKind > 2.5 && particleKind <= 3.5) {
    let sampled = textureSampleLevel(renderTexture, renderSampler, uv, 0.0);
    return sampled * color;
  }
  if (shouldDiscardCircle(shapeFlag, quadCoord)) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return color;
}

@fragment
fn fragmentMainDust(
  @location(0) color: vec4<f32>,
  @location(1) quadCoord: vec2<f32>,
  @location(2) shapeFlag: f32,
  @location(3) uv: vec2<f32>,
  @location(4) particleKind: f32
) -> @location(0) vec4<f32> {
  let _layoutAnchorSample = textureSampleLevel(renderTexture, renderSampler, uv, 0.0);
  let isDust = particleKind > 0.5 && particleKind <= 1.5;
  if (!isDust) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  if (shouldDiscardCircle(shapeFlag, quadCoord)) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return vec4<f32>(color.rgb * color.a, color.a);
}

