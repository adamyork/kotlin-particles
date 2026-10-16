struct ParticleBuffer {
  data: array<vec4<f32>>,
};

struct SimulationUniforms {
  deltaTime: f32,
  worldTime: f32,
  gravity: f32,
  mediumDensity: f32,
  boundaryZ: f32,
  boundaryDepth: f32,
  wallRestitution: f32,
  environmentFriction: f32,
  drag: f32,
  maxParticleSpeed: f32,
  minActiveVelocity: f32,
  spriteTicksPerFrame: f32,
  flowAmplitude: f32,
  flowFrequency: f32,
  flowWavelength: f32,
  maxParticles: f32,
  viewportX: f32,
  viewportY: f32,
  viewportWidth: f32,
  viewportHeight: f32,
  collisionPlaneThickness: f32,
  gravityPlaneThickness: f32,
  cellSize: f32,
  gridColumns: f32,
  gridRows: f32,
  gravitationalConstant: f32,
  gravitationalSoftening: f32,
  gravitySourceCount: f32,
  gravityMinimumAcceleration: f32,
  accretionMassRatio: f32,
  shatterMinimumFragmentRadius: f32,
  shatterMinimumFragmentCount: f32,
  shatterMaximumFragmentCount: f32,
  shatterMomentumPerFragment: f32,
  shatterSpreadSpeed: f32,
  shatterMaxVelocity: f32,
  contactPositionRelaxationExponent: f32,
  contactVelocityRelaxationExponent: f32,
  reservedC: f32,
  reservedD: f32,
};

@group(0) @binding(0) var<storage, read_write> particles: ParticleBuffer;
@group(0) @binding(1) var<storage, read> spawnParticles: ParticleBuffer;
@group(0) @binding(2) var<uniform> simulation: SimulationUniforms;

struct KinematicsBuffer {
  data: array<vec4<f32>>,
};

struct AtomicBuffer {
  data: array<atomic<u32>>,
};

@group(0) @binding(3) var<storage, read_write> kinematics: KinematicsBuffer;
@group(0) @binding(4) var<storage, read_write> grid: AtomicBuffer;
@group(0) @binding(5) var<storage, read_write> scratch: AtomicBuffer;

const VECTORS_PER_PARTICLE = 16u;
const PI = 3.14159265358979;
const TWO_PI = 6.28318530717959;
const MINIMUM_MASS = 0.000000001;
const MAXIMUM_ANGULAR_VELOCITY = 0.5;
const SOLVER_ITERATIONS = 6u;
const WALL_CONTACT_TOLERANCE = 0.5;

const RENDER_KIND_SHAPE = 0.0;
const RENDER_KIND_DUST = 1.0;
const RENDER_KIND_SPRITE = 2.0;
const RENDER_KIND_ANIMATED_SPRITE = 3.0;
const RENDER_KIND_BLOB = 4.0;
const RENDER_KIND_BUBBLE = 5.0;
const RENDER_KIND_DRIP = 6.0;

struct Particle {
  position: vec3<f32>,
  angle: f32,
  velocity: vec3<f32>,
  angularVelocity: f32,
  thrust: vec3<f32>,
  density: f32,
  age: f32,
  delay: f32,
  lifetime: f32,
  alive: f32,
  shapeKind: f32,
  sizeA: f32,
  sizeB: f32,
  scaleRate: f32,
  maximumSizeA: f32,
  maximumSizeB: f32,
  renderKind: f32,
  uniqueId: f32,
  restitution: f32,
  hardness: f32,
  adhesion: f32,
  friction: f32,
  staticFriction: f32,
  rollingResistance: f32,
  materialReservedA: f32,
  materialReservedB: f32,
  color: vec4<f32>,
  startColor: vec3<f32>,
  initialAlpha: f32,
  endColor: vec3<f32>,
  endAlpha: f32,
  origin: vec2<f32>,
  animationFrame: f32,
  animationMaximumFrames: f32,
  scratchA: vec4<f32>,
  scratchB: vec4<f32>,
  reservedA: vec4<f32>,
  reservedB: vec4<f32>,
};

fn assembleParticle(
  vectorZero: vec4<f32>,
  vectorOne: vec4<f32>,
  vectorTwo: vec4<f32>,
  vectorThree: vec4<f32>,
  vectorFour: vec4<f32>,
  vectorFive: vec4<f32>,
  vectorSix: vec4<f32>,
  vectorSeven: vec4<f32>,
  vectorEight: vec4<f32>,
  vectorNine: vec4<f32>,
  vectorTen: vec4<f32>,
  vectorEleven: vec4<f32>,
  vectorTwelve: vec4<f32>,
  vectorThirteen: vec4<f32>,
  vectorFourteen: vec4<f32>,
  vectorFifteen: vec4<f32>
) -> Particle {
  var particle: Particle;
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

fn loadParticle(index: u32) -> Particle {
  let base = index * VECTORS_PER_PARTICLE;
  return assembleParticle(
    particles.data[base + 0u], particles.data[base + 1u], particles.data[base + 2u], particles.data[base + 3u],
    particles.data[base + 4u], particles.data[base + 5u], particles.data[base + 6u], particles.data[base + 7u],
    particles.data[base + 8u], particles.data[base + 9u], particles.data[base + 10u], particles.data[base + 11u],
    particles.data[base + 12u], particles.data[base + 13u], particles.data[base + 14u], particles.data[base + 15u]
  );
}

fn loadSpawnParticle(index: u32) -> Particle {
  let base = index * VECTORS_PER_PARTICLE;
  return assembleParticle(
    spawnParticles.data[base + 0u], spawnParticles.data[base + 1u], spawnParticles.data[base + 2u], spawnParticles.data[base + 3u],
    spawnParticles.data[base + 4u], spawnParticles.data[base + 5u], spawnParticles.data[base + 6u], spawnParticles.data[base + 7u],
    spawnParticles.data[base + 8u], spawnParticles.data[base + 9u], spawnParticles.data[base + 10u], spawnParticles.data[base + 11u],
    spawnParticles.data[base + 12u], spawnParticles.data[base + 13u], spawnParticles.data[base + 14u], spawnParticles.data[base + 15u]
  );
}

fn storeParticle(index: u32, particle: Particle) {
  let base = index * VECTORS_PER_PARTICLE;
  particles.data[base + 0u] = vec4<f32>(particle.position, particle.angle);
  particles.data[base + 1u] = vec4<f32>(particle.velocity, particle.angularVelocity);
  particles.data[base + 2u] = vec4<f32>(particle.thrust, particle.density);
  particles.data[base + 3u] = vec4<f32>(particle.age, particle.delay, particle.lifetime, particle.alive);
  particles.data[base + 4u] = vec4<f32>(particle.shapeKind, particle.sizeA, particle.sizeB, particle.scaleRate);
  particles.data[base + 5u] = vec4<f32>(particle.maximumSizeA, particle.maximumSizeB, particle.renderKind, particle.uniqueId);
  particles.data[base + 6u] = vec4<f32>(particle.restitution, particle.hardness, particle.adhesion, particle.friction);
  particles.data[base + 7u] = vec4<f32>(
    particle.staticFriction, particle.rollingResistance, particle.materialReservedA, particle.materialReservedB
  );
  particles.data[base + 8u] = particle.color;
  particles.data[base + 9u] = vec4<f32>(particle.startColor, particle.initialAlpha);
  particles.data[base + 10u] = vec4<f32>(particle.endColor, particle.endAlpha);
  particles.data[base + 11u] = vec4<f32>(particle.origin, particle.animationFrame, particle.animationMaximumFrames);
  particles.data[base + 12u] = particle.scratchA;
  particles.data[base + 13u] = particle.scratchB;
  particles.data[base + 14u] = particle.reservedA;
  particles.data[base + 15u] = particle.reservedB;
}

fn isRectangle(particle: Particle) -> bool {
  return particle.shapeKind > 0.5;
}

fn particleArea(particle: Particle) -> f32 {
  if (isRectangle(particle)) {
    return particle.sizeA * particle.sizeB;
  }
  return PI * particle.sizeA * particle.sizeA;
}

fn particleMass(particle: Particle) -> f32 {
  return particle.density * particleArea(particle);
}

fn inertialMass(particle: Particle) -> f32 {
  return max(particleMass(particle), MINIMUM_MASS);
}

fn collisionRadius(particle: Particle) -> f32 {
  if (isRectangle(particle)) {
    return particle.sizeA * 0.5;
  }
  return particle.sizeA;
}

fn momentOfInertia(particle: Particle) -> f32 {
  var shapeFactor = particle.sizeA * particle.sizeA * 0.5;
  if (isRectangle(particle)) {
    shapeFactor = (particle.sizeA * particle.sizeA + particle.sizeB * particle.sizeB) / 12.0;
  }
  return max(inertialMass(particle) * shapeFactor, MINIMUM_MASS);
}

fn axisAlignedHalfWidth(particle: Particle) -> f32 {
  if (isRectangle(particle)) {
    return (abs(cos(particle.angle)) * particle.sizeA + abs(sin(particle.angle)) * particle.sizeB) * 0.5;
  }
  return particle.sizeA;
}

fn axisAlignedHalfHeight(particle: Particle) -> f32 {
  if (isRectangle(particle)) {
    return (abs(sin(particle.angle)) * particle.sizeA + abs(cos(particle.angle)) * particle.sizeB) * 0.5;
  }
  return particle.sizeA;
}

fn isVisible(particle: Particle) -> bool {
  return particle.age >= particle.delay;
}

fn isExpired(particle: Particle) -> bool {
  return particle.age >= particle.lifetime + particle.delay;
}

fn limitSpeed(velocity: vec3<f32>, maximumSpeed: f32) -> vec3<f32> {
  if (maximumSpeed <= 0.0) {
    return velocity;
  }
  let speedSquared = velocity.x * velocity.x + velocity.y * velocity.y;
  if (speedSquared <= maximumSpeed * maximumSpeed) {
    return velocity;
  }
  let scale = maximumSpeed / sqrt(speedSquared);
  return vec3<f32>(velocity.x * scale, velocity.y * scale, velocity.z);
}

fn flowVelocityXAt(positionY: f32) -> f32 {
  if (simulation.flowAmplitude == 0.0 || simulation.flowWavelength <= 0.0) {
    return 0.0;
  }
  return simulation.flowAmplitude * cos(TWO_PI * positionY / simulation.flowWavelength + simulation.worldTime * simulation.flowFrequency);
}

fn integrateAdhesive(source: Particle) -> Particle {
  var particle = source;
  let deltaTime = simulation.deltaTime;
  let mass = inertialMass(particle);
  let buoyancyAcceleration = simulation.mediumDensity * particleArea(particle) * simulation.gravity / mass;
  let netForce = mass * (simulation.gravity - buoyancyAcceleration) - particle.adhesion;
  particle.velocity.x = 0.0;
  if (particle.velocity.y <= 0.0 && netForce <= 0.0) {
    particle.velocity.y = 0.0;
    return particle;
  }
  particle.velocity.y = max(0.0, particle.velocity.y + netForce / mass * deltaTime);
  particle.velocity = limitSpeed(particle.velocity, simulation.maxParticleSpeed);
  let distance = particle.velocity.y * deltaTime;
  particle.position.y = particle.position.y + distance;
  var wettedMass = 0.0;
  if (simulation.gravity > 0.0) {
    wettedMass = particle.adhesion / simulation.gravity;
  }
  let massLoss = (simulation.environmentFriction + particle.friction) * wettedMass * distance / (collisionRadius(particle) * 2.0);
  let remainingMass = max(mass - massLoss, MINIMUM_MASS);
  particle.velocity.y = particle.velocity.y * (remainingMass / mass);
  particle.density = remainingMass / particleArea(particle);
  return particle;
}

fn integrateFree(source: Particle) -> Particle {
  var particle = source;
  let deltaTime = simulation.deltaTime;
  let mass = inertialMass(particle);
  let buoyancyAcceleration = simulation.mediumDensity * particleArea(particle) * simulation.gravity / mass;
  particle.velocity.y = particle.velocity.y + (simulation.gravity - buoyancyAcceleration) * deltaTime;
  particle.velocity.x = particle.velocity.x + particle.thrust.x / mass * deltaTime;
  particle.velocity.y = particle.velocity.y + particle.thrust.y / mass * deltaTime;
  particle.velocity.z = particle.velocity.z + particle.thrust.z / mass * deltaTime;
  let dragFraction = min(1.0, max(0.0, simulation.drag) * deltaTime);
  if (dragFraction > 0.0) {
    let flowVelocityX = flowVelocityXAt(particle.position.y);
    particle.velocity.x = particle.velocity.x + (flowVelocityX - particle.velocity.x) * dragFraction;
    particle.velocity.y = particle.velocity.y * (1.0 - dragFraction);
    particle.velocity.z = particle.velocity.z * (1.0 - dragFraction);
  }
  particle.velocity = limitSpeed(particle.velocity, simulation.maxParticleSpeed);
  particle.velocity.z = clamp(particle.velocity.z, -simulation.maxParticleSpeed, simulation.maxParticleSpeed);
  particle.position = particle.position + particle.velocity * deltaTime;
  return particle;
}

fn advanceDimensions(source: Particle) -> Particle {
  var particle = source;
  if (particle.scaleRate == 0.0) {
    return particle;
  }
  let scale = 1.0 + particle.scaleRate * simulation.deltaTime;
  if (isRectangle(particle)) {
    particle.sizeA = clamp(particle.sizeA * scale, 0.0, particle.maximumSizeA);
    particle.sizeB = clamp(particle.sizeB * scale, 0.0, particle.maximumSizeB);
  } else {
    particle.sizeA = clamp(particle.sizeA * scale, 0.0, particle.maximumSizeA);
    particle.sizeB = particle.sizeA;
  }
  return particle;
}

fn integrateParticle(source: Particle) -> Particle {
  var particle = source;
  particle.age = particle.age + simulation.deltaTime;
  if (particle.age < particle.delay) {
    return particle;
  }
  let activeAge = particle.age - particle.delay;
  let progress = clamp(activeAge / particle.lifetime, 0.0, 1.0);
  if (particle.adhesion > 0.0) {
    particle = integrateAdhesive(particle);
  } else {
    particle = integrateFree(particle);
  }
  if (particle.angularVelocity != 0.0) {
    particle.angle = (particle.angle + particle.angularVelocity * simulation.deltaTime) % TWO_PI;
  }
  particle = advanceDimensions(particle);
  particle.color = vec4<f32>(
    mix(particle.startColor, particle.endColor, progress),
    particle.initialAlpha + (particle.endAlpha - particle.initialAlpha) * progress
  );
  if (particle.animationMaximumFrames > 0.5) {
    particle.animationFrame = floor(particle.age / simulation.spriteTicksPerFrame) % particle.animationMaximumFrames;
  }
  return particle;
}

@compute @workgroup_size(64)
fn spawnIntegrateMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  let spawnFlag = spawnParticles.data[index * VECTORS_PER_PARTICLE + 3u].w;
  var particle: Particle;
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

fn relativeWallSpeed(particle: Particle, arm: vec2<f32>, direction: vec2<f32>) -> f32 {
  let pointVelocity = vec2<f32>(
    particle.velocity.x - particle.angularVelocity * arm.y,
    particle.velocity.y + particle.angularVelocity * arm.x
  );
  return -dot(pointVelocity, direction);
}

fn solveWallContact(
  source: Particle,
  normal: vec2<f32>,
  restitution: f32,
  friction: f32,
  staticFriction: f32
) -> Particle {
  var particle = source;
  let radius = collisionRadius(particle);
  var points = array<vec2<f32>, 2>(vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0));
  var pointCount = 0u;
  if (isRectangle(particle)) {
    let cosine = cos(particle.angle);
    let sine = sin(particle.angle);
    let halfWidth = particle.sizeA * 0.5;
    let halfHeight = particle.sizeB * 0.5;
    var offsets = array<vec2<f32>, 4>(
      vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0)
    );
    var maximumSupport = -3.0e38;
    for (var cornerIndex = 0u; cornerIndex < 4u; cornerIndex = cornerIndex + 1u) {
      var signX = 1.0;
      if ((cornerIndex & 1u) == 0u) {
        signX = -1.0;
      }
      var signY = 1.0;
      if ((cornerIndex & 2u) == 0u) {
        signY = -1.0;
      }
      offsets[cornerIndex] = vec2<f32>(
        signX * halfWidth * cosine - signY * halfHeight * sine,
        signX * halfWidth * sine + signY * halfHeight * cosine
      );
      maximumSupport = max(maximumSupport, dot(offsets[cornerIndex], normal));
    }
    for (var cornerIndex = 0u; cornerIndex < 4u; cornerIndex = cornerIndex + 1u) {
      if (dot(offsets[cornerIndex], normal) >= maximumSupport - WALL_CONTACT_TOLERANCE && pointCount < 2u) {
        points[pointCount] = particle.position.xy + offsets[cornerIndex];
        pointCount = pointCount + 1u;
      }
    }
  } else {
    points[0] = particle.position.xy + normal * radius;
    pointCount = 1u;
  }
  let inverseMass = 1.0 / inertialMass(particle);
  let inverseInertia = 1.0 / momentOfInertia(particle);
  let tangent = vec2<f32>(-normal.y, normal.x);
  var arms = array<vec2<f32>, 2>(vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0));
  var normalMasses = array<f32, 2>(0.0, 0.0);
  var tangentMasses = array<f32, 2>(0.0, 0.0);
  var normalImpulses = array<f32, 2>(0.0, 0.0);
  var tangentImpulses = array<f32, 2>(0.0, 0.0);
  var restitutionTargets = array<f32, 2>(0.0, 0.0);
  for (var pointIndex = 0u; pointIndex < pointCount; pointIndex = pointIndex + 1u) {
    let arm = points[pointIndex] - particle.position.xy;
    arms[pointIndex] = arm;
    let normalCross = arm.x * normal.y - arm.y * normal.x;
    let tangentCross = arm.x * tangent.y - arm.y * tangent.x;
    normalMasses[pointIndex] = 1.0 / (inverseMass + normalCross * normalCross * inverseInertia);
    tangentMasses[pointIndex] = 1.0 / (inverseMass + tangentCross * tangentCross * inverseInertia);
    let approachSpeed = relativeWallSpeed(particle, arm, normal);
    if (approachSpeed < -simulation.minActiveVelocity) {
      restitutionTargets[pointIndex] = -restitution * approachSpeed;
    }
  }
  for (var iteration = 0u; iteration < SOLVER_ITERATIONS; iteration = iteration + 1u) {
    for (var pointIndex = 0u; pointIndex < pointCount; pointIndex = pointIndex + 1u) {
      let arm = arms[pointIndex];
      let normalSpeed = relativeWallSpeed(particle, arm, normal);
      let accumulatedNormal = normalImpulses[pointIndex];
      let updatedNormal = max(accumulatedNormal + normalMasses[pointIndex] * (restitutionTargets[pointIndex] - normalSpeed), 0.0);
      let appliedNormal = updatedNormal - accumulatedNormal;
      normalImpulses[pointIndex] = updatedNormal;
      let normalImpulse = normal * appliedNormal;
      particle.velocity.x = particle.velocity.x - normalImpulse.x * inverseMass;
      particle.velocity.y = particle.velocity.y - normalImpulse.y * inverseMass;
      particle.angularVelocity = particle.angularVelocity - inverseInertia * (arm.x * normalImpulse.y - arm.y * normalImpulse.x);
      let tangentSpeed = relativeWallSpeed(particle, arm, tangent);
      let accumulatedTangent = tangentImpulses[pointIndex];
      var updatedTangent = accumulatedTangent - tangentMasses[pointIndex] * tangentSpeed;
      if (abs(updatedTangent) > staticFriction * updatedNormal) {
        updatedTangent = sign(updatedTangent) * friction * updatedNormal;
      }
      let appliedTangent = updatedTangent - accumulatedTangent;
      tangentImpulses[pointIndex] = updatedTangent;
      let tangentImpulse = tangent * appliedTangent;
      particle.velocity.x = particle.velocity.x - tangentImpulse.x * inverseMass;
      particle.velocity.y = particle.velocity.y - tangentImpulse.y * inverseMass;
      particle.angularVelocity = particle.angularVelocity - inverseInertia * (arm.x * tangentImpulse.y - arm.y * tangentImpulse.x);
    }
  }
  var totalNormalImpulse = 0.0;
  for (var pointIndex = 0u; pointIndex < pointCount; pointIndex = pointIndex + 1u) {
    totalNormalImpulse = totalNormalImpulse + normalImpulses[pointIndex];
  }
  particle.angularVelocity = clamp(particle.angularVelocity, -MAXIMUM_ANGULAR_VELOCITY, MAXIMUM_ANGULAR_VELOCITY);
  let rollingDecay = particle.rollingResistance * totalNormalImpulse * radius / momentOfInertia(particle);
  if (abs(particle.angularVelocity) <= rollingDecay) {
    particle.angularVelocity = 0.0;
  } else {
    particle.angularVelocity = particle.angularVelocity - sign(particle.angularVelocity) * rollingDecay;
  }
  return particle;
}

fn applyWalls(source: Particle) -> Particle {
  var particle = source;
  let halfWidth = axisAlignedHalfWidth(particle);
  let halfHeight = axisAlignedHalfHeight(particle);
  let restitution = clamp(particle.restitution, 0.0, 1.0) * simulation.wallRestitution;
  let friction = simulation.environmentFriction + particle.friction;
  let staticFriction = max(friction, simulation.environmentFriction + particle.staticFriction);
  let minimumX = simulation.viewportX + halfWidth;
  let maximumX = simulation.viewportX + simulation.viewportWidth - halfWidth;
  let minimumY = simulation.viewportY + halfHeight;
  let maximumY = simulation.viewportY + simulation.viewportHeight - halfHeight;
  let hitLeftWall = particle.position.x < minimumX;
  let hitRightWall = particle.position.x > maximumX;
  if (hitLeftWall || hitRightWall) {
    if (hitLeftWall) {
      particle.position.x = minimumX;
      particle = solveWallContact(particle, vec2<f32>(-1.0, 0.0), restitution, friction, staticFriction);
    } else {
      particle.position.x = maximumX;
      particle = solveWallContact(particle, vec2<f32>(1.0, 0.0), restitution, friction, staticFriction);
    }
  }
  let hitTopWall = particle.position.y < minimumY;
  let hitBottomWall = particle.position.y > maximumY;
  if (hitTopWall || hitBottomWall) {
    if (hitTopWall) {
      particle.position.y = minimumY;
      particle = solveWallContact(particle, vec2<f32>(0.0, -1.0), restitution, friction, staticFriction);
    } else {
      particle.position.y = maximumY;
      particle = solveWallContact(particle, vec2<f32>(0.0, 1.0), restitution, friction, staticFriction);
    }
  }
  return particle;
}

@compute @workgroup_size(64)
fn wallMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  var particle = loadParticle(index);
  if (particle.alive < 0.5 || !isVisible(particle) || isExpired(particle)) {
    return;
  }
  if (particle.position.z <= simulation.boundaryZ || particle.position.z > simulation.boundaryZ + simulation.boundaryDepth) {
    return;
  }
  particle = applyWalls(particle);
  storeParticle(index, particle);
}

const SCRATCH_LARGE_COUNT = 0u;
const SCRATCH_FREE_COUNT = 1u;
const SCRATCH_FREE_CURSOR = 2u;
const SCRATCH_SOURCE_COUNT = 3u;
const SCRATCH_SPLIT_IDENTIFIER_COUNTER = 5u;
const SCRATCH_SOURCES = 16u;
const SCRATCH_LARGE_LIST = 64u;
const MAXIMUM_GRAVITY_SOURCES = 16u;
const STATE_SET = 2u;
const ACCRETION_OWNER_MODE = 2u;
const ACCRETION_ABSORB_MODE = 3u;
const SPLIT_IDENTIFIER_BASE = 2000000.0;
const NO_SPLIT_REQUEST = -1.0;

fn freeListBase() -> u32 {
  return SCRATCH_LARGE_LIST + u32(simulation.maxParticles);
}

fn massListBase() -> u32 {
  return SCRATCH_LARGE_LIST + u32(simulation.maxParticles) * 2u;
}
const CONTACT_SWEEPS_COUNT_MODE = 0u;
const CONTACT_SWEEPS_SOLVE_MODE = 1u;
const CONTAINMENT_TOLERANCE = 0.5;
const MANIFOLD_TANGENT_EPSILON = 0.001;
const MAXIMUM_CANDIDATES = 8u;
const DENSE_CONTACT_COUNT = 8.0;

fn gridCellCount() -> u32 {
  return u32(simulation.gridColumns * simulation.gridRows);
}

fn gridStartsBase() -> u32 {
  return gridCellCount();
}

fn gridCursorsBase() -> u32 {
  return gridCellCount() * 2u + 1u;
}

fn gridSortedBase() -> u32 {
  return gridCellCount() * 3u + 1u;
}

fn gridCellIndexFor(position: vec2<f32>) -> i32 {
  let column = i32((position.x - simulation.viewportX) / simulation.cellSize);
  let row = i32((position.y - simulation.viewportY) / simulation.cellSize);
  if (position.x < simulation.viewportX || position.y < simulation.viewportY) {
    return -1;
  }
  if (column < 0 || column >= i32(simulation.gridColumns) || row < 0 || row >= i32(simulation.gridRows)) {
    return -1;
  }
  return row * i32(simulation.gridColumns) + column;
}

fn boundingRadiusOf(particle: Particle) -> f32 {
  if (isRectangle(particle)) {
    return length(vec2<f32>(particle.sizeA, particle.sizeB)) * 0.5;
  }
  return particle.sizeA;
}

fn takesPartInCollisions(lifecycle: vec4<f32>) -> bool {
  return lifecycle.w > 0.5 && lifecycle.x >= lifecycle.y && lifecycle.x < lifecycle.z + lifecycle.y;
}

@compute @workgroup_size(64)
fn gridClearMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  let cellCount = gridCellCount();
  for (var cellIndex = index; cellIndex < cellCount; cellIndex = cellIndex + u32(simulation.maxParticles)) {
    atomicStore(&grid.data[cellIndex], 0u);
  }
  if (index == 0u) {
    atomicStore(&scratch.data[SCRATCH_LARGE_COUNT], 0u);
    atomicStore(&scratch.data[SCRATCH_FREE_COUNT], 0u);
    atomicStore(&scratch.data[SCRATCH_FREE_CURSOR], 0u);
  }
}

@compute @workgroup_size(64)
fn gridCountMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  let base = index * VECTORS_PER_PARTICLE;
  let lifecycle = particles.data[base + 3u];
  var cellIndex = -1;
  var category = 0.0;
  var massValue = 0.0;
  if (takesPartInCollisions(lifecycle)) {
    let positionVector = particles.data[base + 0u];
    let dimensions = particles.data[base + 4u];
    var shapeArea = PI * dimensions.y * dimensions.y;
    if (dimensions.x > 0.5) {
      shapeArea = dimensions.y * dimensions.z;
    }
    massValue = particles.data[base + 2u].w * shapeArea;
    cellIndex = gridCellIndexFor(positionVector.xy);
    if (cellIndex >= 0) {
      var boundingRadius = dimensions.y;
      if (dimensions.x > 0.5) {
        boundingRadius = length(vec2<f32>(dimensions.y, dimensions.z)) * 0.5;
      }
      if (boundingRadius * 2.0 > simulation.cellSize) {
        category = 2.0;
        let largeSlot = atomicAdd(&scratch.data[SCRATCH_LARGE_COUNT], 1u);
        atomicStore(&scratch.data[SCRATCH_LARGE_LIST + largeSlot], index);
        cellIndex = -1;
      } else {
        category = 1.0;
        atomicAdd(&grid.data[u32(cellIndex)], 1u);
      }
    }
  }
  particles.data[base + 12u] = vec4<f32>(f32(cellIndex), category, 0.0, 0.0);
  atomicStore(&scratch.data[massListBase() + index], bitcast<u32>(massValue));
}

@compute @workgroup_size(1)
fn gridScanMain() {
  let cellCount = gridCellCount();
  var runningTotal = 0u;
  for (var cellIndex = 0u; cellIndex < cellCount; cellIndex = cellIndex + 1u) {
    atomicStore(&grid.data[gridStartsBase() + cellIndex], runningTotal);
    atomicStore(&grid.data[gridCursorsBase() + cellIndex], runningTotal);
    runningTotal = runningTotal + atomicLoad(&grid.data[cellIndex]);
  }
  atomicStore(&grid.data[gridStartsBase() + cellCount], runningTotal);
}

@compute @workgroup_size(64)
fn gridScatterMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  let cellInformation = particles.data[index * VECTORS_PER_PARTICLE + 12u];
  if (cellInformation.y > 0.5 && cellInformation.y < 1.5) {
    let sortedPosition = atomicAdd(&grid.data[gridCursorsBase() + u32(cellInformation.x)], 1u);
    atomicStore(&grid.data[gridSortedBase() + sortedPosition], index);
  }
}

struct Body {
  index: u32,
  position: vec2<f32>,
  planeZ: f32,
  angle: f32,
  velocity: vec2<f32>,
  angularVelocity: f32,
  isRectangleShape: bool,
  sizeA: f32,
  sizeB: f32,
  mass: f32,
  inertia: f32,
  restitution: f32,
  friction: f32,
  staticFriction: f32,
  rollingResistance: f32,
  hardness: f32,
  collisionRadius: f32,
  boundingRadius: f32,
  contactCount: f32,
};

struct Contact {
  isValid: bool,
  normal: vec2<f32>,
  depth: f32,
  pointCount: u32,
  pointA: vec2<f32>,
  pointB: vec2<f32>,
};

struct PairOutcome {
  first: Body,
  second: Body,
};

struct Gather {
  count: f32,
  positionDelta: vec2<f32>,
  velocityDelta: vec2<f32>,
  angularVelocityDelta: f32,
  hadContact: bool,
  ownerMass: f32,
  ownerPlusOne: f32,
  absorbedMass: f32,
  absorbedArea: f32,
  absorbedMomentum: vec2<f32>,
  absorbedCount: f32,
  splitMomentum: f32,
  splitNormal: vec2<f32>,
};

fn newGather() -> Gather {
  var gather: Gather;
  gather.count = 0.0;
  gather.positionDelta = vec2<f32>(0.0, 0.0);
  gather.velocityDelta = vec2<f32>(0.0, 0.0);
  gather.angularVelocityDelta = 0.0;
  gather.hadContact = false;
  gather.ownerMass = 0.0;
  gather.ownerPlusOne = 0.0;
  gather.absorbedMass = 0.0;
  gather.absorbedArea = 0.0;
  gather.absorbedMomentum = vec2<f32>(0.0, 0.0);
  gather.absorbedCount = 0.0;
  gather.splitMomentum = NO_SPLIT_REQUEST;
  gather.splitNormal = vec2<f32>(1.0, 0.0);
  return gather;
}

fn kinematicsBase(setIndex: u32, index: u32) -> u32 {
  return (setIndex * u32(simulation.maxParticles) + index) * 2u;
}

fn loadBody(index: u32, setIndex: u32) -> Body {
  let base = index * VECTORS_PER_PARTICLE;
  let positionVector = particles.data[base + 0u];
  let densityVector = particles.data[base + 2u];
  let dimensions = particles.data[base + 4u];
  let materialVector = particles.data[base + 6u];
  let materialSecondVector = particles.data[base + 7u];
  var motion = vec4<f32>(positionVector.x, positionVector.y, particles.data[base + 1u].x, particles.data[base + 1u].y);
  var orientation = vec4<f32>(positionVector.w, particles.data[base + 1u].w, 0.0, 0.0);
  if (setIndex != STATE_SET) {
    let kinematicsOffset = kinematicsBase(setIndex, index);
    motion = kinematics.data[kinematicsOffset];
    orientation = kinematics.data[kinematicsOffset + 1u];
  }
  var body: Body;
  body.index = index;
  body.position = motion.xy;
  body.planeZ = positionVector.z;
  body.angle = orientation.x;
  body.velocity = motion.zw;
  body.angularVelocity = orientation.y;
  body.isRectangleShape = dimensions.x > 0.5;
  body.sizeA = dimensions.y;
  body.sizeB = dimensions.z;
  var area = PI * dimensions.y * dimensions.y;
  var shapeFactor = dimensions.y * dimensions.y * 0.5;
  body.collisionRadius = dimensions.y;
  body.boundingRadius = dimensions.y;
  if (body.isRectangleShape) {
    area = dimensions.y * dimensions.z;
    shapeFactor = (dimensions.y * dimensions.y + dimensions.z * dimensions.z) / 12.0;
    body.collisionRadius = dimensions.y * 0.5;
    body.boundingRadius = length(vec2<f32>(dimensions.y, dimensions.z)) * 0.5;
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

fn emptyContact() -> Contact {
  var contact: Contact;
  contact.isValid = false;
  contact.normal = vec2<f32>(0.0, 0.0);
  contact.depth = 0.0;
  contact.pointCount = 0u;
  contact.pointA = vec2<f32>(0.0, 0.0);
  contact.pointB = vec2<f32>(0.0, 0.0);
  return contact;
}

fn detectCircleCircle(first: Body, second: Body) -> Contact {
  var contact = emptyContact();
  let delta = second.position - first.position;
  let distance = length(delta);
  if (distance <= 0.0) {
    return contact;
  }
  let minimumDistance = first.collisionRadius + second.collisionRadius;
  if (distance >= minimumDistance) {
    return contact;
  }
  let overlap = minimumDistance - distance;
  contact.normal = delta / distance;
  contact.depth = overlap;
  contact.pointCount = 1u;
  contact.pointA = first.position + contact.normal * (first.collisionRadius - overlap * 0.5);
  contact.isValid = true;
  return contact;
}

fn detectCircleBox(circle: Body, box: Body, isBoxFirst: bool) -> Contact {
  var contact = emptyContact();
  let radius = circle.collisionRadius;
  let halfSize = vec2<f32>(box.sizeA * 0.5, box.sizeB * 0.5);
  let cosine = cos(box.angle);
  let sine = sin(box.angle);
  let offset = circle.position - box.position;
  let local = vec2<f32>(offset.x * cosine + offset.y * sine, -offset.x * sine + offset.y * cosine);
  let closest = clamp(local, -halfSize, halfSize);
  let delta = local - closest;
  let distanceSquared = dot(delta, delta);
  var localNormal = vec2<f32>(0.0, 0.0);
  var localPoint = closest;
  var depth = 0.0;
  if (distanceSquared > 0.0) {
    let distance = sqrt(distanceSquared);
    if (distance >= radius) {
      return contact;
    }
    localNormal = delta / distance;
    depth = radius - distance;
  } else {
    let penetrationX = halfSize.x - abs(local.x);
    let penetrationY = halfSize.y - abs(local.y);
    if (penetrationX < penetrationY) {
      localNormal = vec2<f32>(select(-1.0, 1.0, local.x >= 0.0), 0.0);
      localPoint.x = localNormal.x * halfSize.x;
      depth = radius + penetrationX;
    } else {
      localNormal = vec2<f32>(0.0, select(-1.0, 1.0, local.y >= 0.0));
      localPoint.y = localNormal.y * halfSize.y;
      depth = radius + penetrationY;
    }
  }
  let boxToCircle = vec2<f32>(
    localNormal.x * cosine - localNormal.y * sine,
    localNormal.x * sine + localNormal.y * cosine
  );
  contact.normal = boxToCircle * select(-1.0, 1.0, isBoxFirst);
  contact.depth = depth;
  contact.pointCount = 1u;
  contact.pointA = box.position + vec2<f32>(
    localPoint.x * cosine - localPoint.y * sine,
    localPoint.x * sine + localPoint.y * cosine
  );
  contact.isValid = true;
  return contact;
}

fn collectContainedCorners(
  cornerBody: Body,
  containerBody: Body,
  candidates: ptr<function, array<vec2<f32>, 8>>,
  startCount: u32
) -> u32 {
  var count = startCount;
  let cornerCosine = cos(cornerBody.angle);
  let cornerSine = sin(cornerBody.angle);
  let containerCosine = cos(containerBody.angle);
  let containerSine = sin(containerBody.angle);
  let cornerHalfWidth = cornerBody.sizeA * 0.5;
  let cornerHalfHeight = cornerBody.sizeB * 0.5;
  let containerHalfWidth = containerBody.sizeA * 0.5 + CONTAINMENT_TOLERANCE;
  let containerHalfHeight = containerBody.sizeB * 0.5 + CONTAINMENT_TOLERANCE;
  for (var cornerIndex = 0u; cornerIndex < 4u; cornerIndex = cornerIndex + 1u) {
    if (count >= MAXIMUM_CANDIDATES) {
      return count;
    }
    let signX = select(1.0, -1.0, (cornerIndex & 1u) == 0u);
    let signY = select(1.0, -1.0, (cornerIndex & 2u) == 0u);
    let cornerOffset = vec2<f32>(
      signX * cornerHalfWidth * cornerCosine - signY * cornerHalfHeight * cornerSine,
      signX * cornerHalfWidth * cornerSine + signY * cornerHalfHeight * cornerCosine
    );
    let worldPoint = cornerBody.position + cornerOffset;
    let offset = worldPoint - containerBody.position;
    let local = vec2<f32>(
      offset.x * containerCosine + offset.y * containerSine,
      -offset.x * containerSine + offset.y * containerCosine
    );
    if (abs(local.x) <= containerHalfWidth && abs(local.y) <= containerHalfHeight) {
      (*candidates)[count] = worldPoint;
      count = count + 1u;
    }
  }
  return count;
}

fn supportPointOffset(body: Body, direction: vec2<f32>) -> vec2<f32> {
  let cosine = cos(body.angle);
  let sine = sin(body.angle);
  let localDirection = vec2<f32>(direction.x * cosine + direction.y * sine, -direction.x * sine + direction.y * cosine);
  let local = vec2<f32>(
    select(-body.sizeA * 0.5, body.sizeA * 0.5, localDirection.x >= 0.0),
    select(-body.sizeB * 0.5, body.sizeB * 0.5, localDirection.y >= 0.0)
  );
  return vec2<f32>(local.x * cosine - local.y * sine, local.x * sine + local.y * cosine);
}

fn detectBoxBox(first: Body, second: Body) -> Contact {
  var contact = emptyContact();
  let firstHalf = vec2<f32>(first.sizeA * 0.5, first.sizeB * 0.5);
  let secondHalf = vec2<f32>(second.sizeA * 0.5, second.sizeB * 0.5);
  let firstCosine = cos(first.angle);
  let firstSine = sin(first.angle);
  let secondCosine = cos(second.angle);
  let secondSine = sin(second.angle);
  let delta = second.position - first.position;
  var minimumOverlap = 3.0e38;
  var normal = vec2<f32>(0.0, 0.0);
  for (var axisIndex = 0u; axisIndex < 4u; axisIndex = axisIndex + 1u) {
    var axis = vec2<f32>(firstCosine, firstSine);
    if (axisIndex == 1u) {
      axis = vec2<f32>(-firstSine, firstCosine);
    } else if (axisIndex == 2u) {
      axis = vec2<f32>(secondCosine, secondSine);
    } else if (axisIndex == 3u) {
      axis = vec2<f32>(-secondSine, secondCosine);
    }
    let firstProjection = firstHalf.x * abs(axis.x * firstCosine + axis.y * firstSine) +
      firstHalf.y * abs(-axis.x * firstSine + axis.y * firstCosine);
    let secondProjection = secondHalf.x * abs(axis.x * secondCosine + axis.y * secondSine) +
      secondHalf.y * abs(-axis.x * secondSine + axis.y * secondCosine);
    let separation = dot(delta, axis);
    let overlap = firstProjection + secondProjection - abs(separation);
    if (overlap <= 0.0) {
      return contact;
    }
    if (overlap < minimumOverlap) {
      minimumOverlap = overlap;
      normal = select(-axis, axis, separation >= 0.0);
    }
  }
  contact.normal = normal;
  contact.depth = minimumOverlap;
  var candidates = array<vec2<f32>, 8>(
    vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0),
    vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0)
  );
  var candidateCount = collectContainedCorners(first, second, &candidates, 0u);
  candidateCount = collectContainedCorners(second, first, &candidates, candidateCount);
  if (candidateCount == 0u) {
    let firstPoint = first.position + supportPointOffset(first, normal);
    let secondPoint = second.position + supportPointOffset(second, -normal);
    contact.pointCount = 1u;
    contact.pointA = (firstPoint + secondPoint) * 0.5;
    contact.isValid = true;
    return contact;
  }
  let tangent = vec2<f32>(-normal.y, normal.x);
  var minimumIndex = 0u;
  var maximumIndex = 0u;
  var minimumTangent = 3.0e38;
  var maximumTangent = -3.0e38;
  var sum = vec2<f32>(0.0, 0.0);
  for (var candidateIndex = 0u; candidateIndex < candidateCount; candidateIndex = candidateIndex + 1u) {
    let tangentCoordinate = dot(candidates[candidateIndex], tangent);
    if (tangentCoordinate < minimumTangent) {
      minimumTangent = tangentCoordinate;
      minimumIndex = candidateIndex;
    }
    if (tangentCoordinate > maximumTangent) {
      maximumTangent = tangentCoordinate;
      maximumIndex = candidateIndex;
    }
    sum = sum + candidates[candidateIndex];
  }
  if (maximumTangent - minimumTangent < MANIFOLD_TANGENT_EPSILON) {
    contact.pointCount = 1u;
    contact.pointA = sum / f32(candidateCount);
  } else {
    contact.pointCount = 2u;
    contact.pointA = candidates[minimumIndex];
    contact.pointB = candidates[maximumIndex];
  }
  contact.isValid = true;
  return contact;
}

fn detectContact(first: Body, second: Body) -> Contact {
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

fn rollingDecayFor(body: Body, normalImpulse: f32) -> f32 {
  let decay = body.rollingResistance * normalImpulse * body.collisionRadius / body.inertia;
  if (abs(body.angularVelocity) <= decay) {
    return 0.0;
  }
  return body.angularVelocity - sign(body.angularVelocity) * decay;
}

fn solveCircleCircle(firstSource: Body, secondSource: Body, contact: Contact) -> PairOutcome {
  var first = firstSource;
  var second = secondSource;
  var outcome: PairOutcome;
  let normal = contact.normal;
  let totalMass = first.mass + second.mass;
  first.position = first.position - normal * contact.depth * (second.mass / totalMass);
  second.position = second.position + normal * contact.depth * (first.mass / totalMass);
  let relativeVelocity = first.velocity - second.velocity;
  let relativeNormalSpeed = dot(normal, relativeVelocity);
  let impulse = 2.0 * relativeNormalSpeed / totalMass;
  let restitution = min(first.restitution, second.restitution);
  first.velocity = first.velocity - normal * (impulse * second.mass * (1.0 + restitution));
  second.velocity = second.velocity + normal * (impulse * first.mass * (1.0 + restitution));
  let friction = simulation.environmentFriction + sqrt(first.friction * second.friction);
  let staticFriction = max(
    friction,
    simulation.environmentFriction + sqrt(first.staticFriction * second.staticFriction)
  );
  if (friction <= 0.0) {
    outcome.first = first;
    outcome.second = second;
    return outcome;
  }
  let tangent = vec2<f32>(-normal.y, normal.x);
  let isSpinning = first.angularVelocity != 0.0 || second.angularVelocity != 0.0;
  var firstSpinArm = 0.0;
  var secondSpinArm = 0.0;
  if (isSpinning) {
    firstSpinArm = first.collisionRadius;
    secondSpinArm = second.collisionRadius;
  }
  let slipSpeed = dot(tangent, relativeVelocity) + first.angularVelocity * firstSpinArm + second.angularVelocity * secondSpinArm;
  let effectiveInverseMass = 1.0 / first.mass + 1.0 / second.mass +
    firstSpinArm * firstSpinArm / first.inertia + secondSpinArm * secondSpinArm / second.inertia;
  let reducedMass = first.mass * second.mass / totalMass;
  let normalImpulseMagnitude = abs(2.0 * reducedMass * relativeNormalSpeed * (1.0 + restitution));
  let requiredImpulse = slipSpeed / effectiveInverseMass;
  var frictionImpulse = sign(requiredImpulse) * friction * normalImpulseMagnitude;
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

fn pointSpeedAlong(body: Body, arm: vec2<f32>, direction: vec2<f32>) -> f32 {
  let pointVelocity = vec2<f32>(
    body.velocity.x - body.angularVelocity * arm.y,
    body.velocity.y + body.angularVelocity * arm.x
  );
  return dot(pointVelocity, direction);
}

fn solveBoxContact(firstSource: Body, secondSource: Body, contact: Contact) -> PairOutcome {
  var first = firstSource;
  var second = secondSource;
  var outcome: PairOutcome;
  let normal = contact.normal;
  let tangent = vec2<f32>(-normal.y, normal.x);
  let totalMass = first.mass + second.mass;
  first.position = first.position - normal * contact.depth * (second.mass / totalMass);
  second.position = second.position + normal * contact.depth * (first.mass / totalMass);
  let restitution = 1.0 + 2.0 * min(first.restitution, second.restitution);
  let friction = simulation.environmentFriction + sqrt(first.friction * second.friction);
  let staticFriction = max(
    friction,
    simulation.environmentFriction + sqrt(first.staticFriction * second.staticFriction)
  );
  let firstInverseMass = 1.0 / first.mass;
  let firstInverseInertia = 1.0 / first.inertia;
  let secondInverseMass = 1.0 / second.mass;
  let secondInverseInertia = 1.0 / second.inertia;
  var points = array<vec2<f32>, 2>(contact.pointA, contact.pointB);
  var firstArms = array<vec2<f32>, 2>(vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0));
  var secondArms = array<vec2<f32>, 2>(vec2<f32>(0.0, 0.0), vec2<f32>(0.0, 0.0));
  var normalMasses = array<f32, 2>(0.0, 0.0);
  var tangentMasses = array<f32, 2>(0.0, 0.0);
  var normalImpulses = array<f32, 2>(0.0, 0.0);
  var tangentImpulses = array<f32, 2>(0.0, 0.0);
  var restitutionTargets = array<f32, 2>(0.0, 0.0);
  for (var pointIndex = 0u; pointIndex < contact.pointCount; pointIndex = pointIndex + 1u) {
    let firstArm = points[pointIndex] - first.position;
    let secondArm = points[pointIndex] - second.position;
    firstArms[pointIndex] = firstArm;
    secondArms[pointIndex] = secondArm;
    let firstNormalCross = firstArm.x * normal.y - firstArm.y * normal.x;
    let secondNormalCross = secondArm.x * normal.y - secondArm.y * normal.x;
    let firstTangentCross = firstArm.x * tangent.y - firstArm.y * tangent.x;
    let secondTangentCross = secondArm.x * tangent.y - secondArm.y * tangent.x;
    normalMasses[pointIndex] = 1.0 / (firstInverseMass + secondInverseMass +
      firstNormalCross * firstNormalCross * firstInverseInertia +
      secondNormalCross * secondNormalCross * secondInverseInertia);
    tangentMasses[pointIndex] = 1.0 / (firstInverseMass + secondInverseMass +
      firstTangentCross * firstTangentCross * firstInverseInertia +
      secondTangentCross * secondTangentCross * secondInverseInertia);
    let approachSpeed = pointSpeedAlong(second, secondArm, normal) - pointSpeedAlong(first, firstArm, normal);
    if (approachSpeed < -simulation.minActiveVelocity) {
      restitutionTargets[pointIndex] = -restitution * approachSpeed;
    }
  }
  for (var iteration = 0u; iteration < SOLVER_ITERATIONS; iteration = iteration + 1u) {
    for (var pointIndex = 0u; pointIndex < contact.pointCount; pointIndex = pointIndex + 1u) {
      let firstArm = firstArms[pointIndex];
      let secondArm = secondArms[pointIndex];
      let normalSpeed = pointSpeedAlong(second, secondArm, normal) - pointSpeedAlong(first, firstArm, normal);
      let accumulatedNormal = normalImpulses[pointIndex];
      let updatedNormal = max(accumulatedNormal + normalMasses[pointIndex] * (restitutionTargets[pointIndex] - normalSpeed), 0.0);
      let appliedNormal = updatedNormal - accumulatedNormal;
      normalImpulses[pointIndex] = updatedNormal;
      let normalImpulse = normal * appliedNormal;
      first.velocity = first.velocity - normalImpulse * firstInverseMass;
      first.angularVelocity = first.angularVelocity - firstInverseInertia * (firstArm.x * normalImpulse.y - firstArm.y * normalImpulse.x);
      second.velocity = second.velocity + normalImpulse * secondInverseMass;
      second.angularVelocity = second.angularVelocity + secondInverseInertia * (secondArm.x * normalImpulse.y - secondArm.y * normalImpulse.x);
      let tangentSpeed = pointSpeedAlong(second, secondArm, tangent) - pointSpeedAlong(first, firstArm, tangent);
      let accumulatedTangent = tangentImpulses[pointIndex];
      var updatedTangent = accumulatedTangent - tangentMasses[pointIndex] * tangentSpeed;
      if (abs(updatedTangent) > staticFriction * updatedNormal) {
        updatedTangent = sign(updatedTangent) * friction * updatedNormal;
      }
      let appliedTangent = updatedTangent - accumulatedTangent;
      tangentImpulses[pointIndex] = updatedTangent;
      let tangentImpulse = tangent * appliedTangent;
      first.velocity = first.velocity - tangentImpulse * firstInverseMass;
      first.angularVelocity = first.angularVelocity - firstInverseInertia * (firstArm.x * tangentImpulse.y - firstArm.y * tangentImpulse.x);
      second.velocity = second.velocity + tangentImpulse * secondInverseMass;
      second.angularVelocity = second.angularVelocity + secondInverseInertia * (secondArm.x * tangentImpulse.y - secondArm.y * tangentImpulse.x);
    }
  }
  var totalNormalImpulse = 0.0;
  for (var pointIndex = 0u; pointIndex < contact.pointCount; pointIndex = pointIndex + 1u) {
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

fn isResting(body: Body) -> bool {
  let angularSurfaceSpeed = body.angularVelocity * body.collisionRadius;
  let speedSquared = dot(body.velocity, body.velocity) + angularSurfaceSpeed * angularSurfaceSpeed;
  return speedSquared < simulation.minActiveVelocity * simulation.minActiveVelocity;
}

fn bodyArea(body: Body) -> f32 {
  if (body.isRectangleShape) {
    return body.sizeA * body.sizeB;
  }
  return PI * body.sizeA * body.sizeA;
}

fn consumesBody(consumer: Body, consumed: Body) -> bool {
  if (abs(consumer.planeZ - consumed.planeZ) >= simulation.gravityPlaneThickness) {
    return false;
  }
  if (consumer.mass < consumed.mass || (consumer.mass == consumed.mass && consumer.index > consumed.index)) {
    return false;
  }
  return consumer.mass >= consumed.mass * simulation.accretionMassRatio;
}

fn processPair(
  gatherSource: Gather,
  selfBody: Body,
  otherIndex: u32,
  readSet: u32,
  mode: u32,
  sweepIndex: u32
) -> Gather {
  var gather = gatherSource;
  if (otherIndex == selfBody.index) {
    return gather;
  }
  let otherBase = otherIndex * VECTORS_PER_PARTICLE;
  if (!takesPartInCollisions(particles.data[otherBase + 3u])) {
    return gather;
  }
  if (mode == ACCRETION_ABSORB_MODE) {
    if (u32(particles.data[otherBase + 13u].x) != selfBody.index + 1u) {
      return gather;
    }
    let victim = loadBody(otherIndex, STATE_SET);
    gather.absorbedMass = gather.absorbedMass + victim.mass;
    gather.absorbedArea = gather.absorbedArea + bodyArea(victim);
    gather.absorbedMomentum = gather.absorbedMomentum + victim.velocity * victim.mass;
    gather.absorbedCount = gather.absorbedCount + 1.0;
    return gather;
  }
  let other = loadBody(otherIndex, readSet);
  if (abs(selfBody.planeZ - other.planeZ) >= simulation.collisionPlaneThickness) {
    return gather;
  }
  let selfIsFirst = selfBody.index < otherIndex;
  var first = selfBody;
  var second = other;
  if (!selfIsFirst) {
    first = other;
    second = selfBody;
  }
  if (mode == ACCRETION_OWNER_MODE) {
    if (!consumesBody(other, selfBody)) {
      return gather;
    }
    let ownerContact = detectContact(first, second);
    if (!ownerContact.isValid) {
      return gather;
    }
    if (other.mass > gather.ownerMass || (other.mass == gather.ownerMass && f32(otherIndex + 1u) < gather.ownerPlusOne)) {
      gather.ownerMass = other.mass;
      gather.ownerPlusOne = f32(otherIndex + 1u);
    }
    return gather;
  }
  let contact = detectContact(first, second);
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
  let bothCircles = !first.isRectangleShape && !second.isRectangleShape;
  if (sweepIndex > 0u && bothCircles) {
    return gather;
  }
  if (sweepIndex == 0u && selfBody.hardness < other.hardness) {
    let impactMomentum = other.mass * length(other.velocity);
    if (impactMomentum > gather.splitMomentum) {
      gather.splitMomentum = impactMomentum;
      gather.splitNormal = contact.normal;
    }
  }
  let contactLoad = max(1.0, max(selfBody.contactCount, other.contactCount));
  var outcome: PairOutcome;
  if (bothCircles) {
    outcome = solveCircleCircle(first, second, contact);
    let isSeparating = dot(contact.normal, first.velocity - second.velocity) <= 0.0;
    if (isSeparating && contactLoad >= DENSE_CONTACT_COUNT) {
      outcome.first.velocity = first.velocity;
      outcome.first.angularVelocity = first.angularVelocity;
      outcome.second.velocity = second.velocity;
      outcome.second.angularVelocity = second.angularVelocity;
    }
  } else {
    outcome = solveBoxContact(first, second, contact);
  }
  let positionRelaxation = pow(1.0 / contactLoad, simulation.contactPositionRelaxationExponent);
  let velocityRelaxation = pow(1.0 / contactLoad, simulation.contactVelocityRelaxationExponent);
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

fn gatherContacts(index: u32, readSet: u32, mode: u32, sweepIndex: u32) -> Gather {
  var gather = newGather();
  let cellInformation = particles.data[index * VECTORS_PER_PARTICLE + 12u];
  if (cellInformation.y < 0.5) {
    return gather;
  }
  let selfBody = loadBody(index, readSet);
  let columns = i32(simulation.gridColumns);
  let rows = i32(simulation.gridRows);
  var firstColumn = 0;
  var lastColumn = -1;
  var firstRow = 0;
  var lastRow = -1;
  if (cellInformation.y < 1.5) {
    let cellIndex = i32(cellInformation.x);
    let column = cellIndex % columns;
    let row = cellIndex / columns;
    firstColumn = max(column - 1, 0);
    lastColumn = min(column + 1, columns - 1);
    firstRow = max(row - 1, 0);
    lastRow = min(row + 1, rows - 1);
  } else {
    let reach = selfBody.boundingRadius + simulation.cellSize * 0.5;
    firstColumn = max(i32((selfBody.position.x - reach - simulation.viewportX) / simulation.cellSize), 0);
    lastColumn = min(i32((selfBody.position.x + reach - simulation.viewportX) / simulation.cellSize), columns - 1);
    firstRow = max(i32((selfBody.position.y - reach - simulation.viewportY) / simulation.cellSize), 0);
    lastRow = min(i32((selfBody.position.y + reach - simulation.viewportY) / simulation.cellSize), rows - 1);
  }
  for (var row = firstRow; row <= lastRow; row = row + 1) {
    for (var column = firstColumn; column <= lastColumn; column = column + 1) {
      let neighborCell = u32(row * columns + column);
      let cellStart = atomicLoad(&grid.data[gridStartsBase() + neighborCell]);
      let cellEnd = atomicLoad(&grid.data[gridStartsBase() + neighborCell + 1u]);
      for (var sortedIndex = cellStart; sortedIndex < cellEnd; sortedIndex = sortedIndex + 1u) {
        let otherIndex = atomicLoad(&grid.data[gridSortedBase() + sortedIndex]);
        gather = processPair(gather, selfBody, otherIndex, readSet, mode, sweepIndex);
      }
    }
  }
  let largeCount = min(atomicLoad(&scratch.data[SCRATCH_LARGE_COUNT]), u32(simulation.maxParticles));
  for (var largeIndex = 0u; largeIndex < largeCount; largeIndex = largeIndex + 1u) {
    let otherIndex = atomicLoad(&scratch.data[SCRATCH_LARGE_LIST + largeIndex]);
    gather = processPair(gather, selfBody, otherIndex, readSet, mode, sweepIndex);
  }
  return gather;
}

fn countContacts(index: u32, readSet: u32) {
  let base = index * VECTORS_PER_PARTICLE;
  if (!takesPartInCollisions(particles.data[base + 3u])) {
    return;
  }
  let gather = gatherContacts(index, readSet, CONTACT_SWEEPS_COUNT_MODE, 0u);
  let offset = kinematicsBase(readSet, index) + 1u;
  var orientation = kinematics.data[offset];
  orientation.z = gather.count;
  kinematics.data[offset] = orientation;
}

fn solveContacts(index: u32, readSet: u32, writeSet: u32, sweepIndex: u32) {
  let base = index * VECTORS_PER_PARTICLE;
  if (!takesPartInCollisions(particles.data[base + 3u])) {
    return;
  }
  let gather = gatherContacts(index, readSet, CONTACT_SWEEPS_SOLVE_MODE, sweepIndex);
  let readOffset = kinematicsBase(readSet, index);
  let writeOffset = kinematicsBase(writeSet, index);
  let motion = kinematics.data[readOffset];
  let orientation = kinematics.data[readOffset + 1u];
  var angularVelocity = orientation.y + gather.angularVelocityDelta;
  if (gather.hadContact) {
    angularVelocity = clamp(angularVelocity, -MAXIMUM_ANGULAR_VELOCITY, MAXIMUM_ANGULAR_VELOCITY);
  }
  kinematics.data[writeOffset] = vec4<f32>(
    motion.x + gather.positionDelta.x,
    motion.y + gather.positionDelta.y,
    motion.z + gather.velocityDelta.x,
    motion.w + gather.velocityDelta.y
  );
  kinematics.data[writeOffset + 1u] = vec4<f32>(orientation.x, angularVelocity, 0.0, 0.0);
  if (sweepIndex == 0u) {
    particles.data[base + 13u] = vec4<f32>(0.0, gather.splitNormal.x, gather.splitNormal.y, gather.splitMomentum);
  }
}

@compute @workgroup_size(64)
fn contactBeginMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  let base = index * VECTORS_PER_PARTICLE;
  if (!takesPartInCollisions(particles.data[base + 3u])) {
    return;
  }
  let positionVector = particles.data[base + 0u];
  let velocityVector = particles.data[base + 1u];
  let offset = kinematicsBase(0u, index);
  particles.data[base + 13u] = vec4<f32>(0.0, 0.0, 0.0, NO_SPLIT_REQUEST);
  kinematics.data[offset] = vec4<f32>(positionVector.x, positionVector.y, velocityVector.x, velocityVector.y);
  kinematics.data[offset + 1u] = vec4<f32>(positionVector.w, velocityVector.w, 0.0, 0.0);
}

@compute @workgroup_size(64)
fn countContactsFromAMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  if (globalInvocationId.x < u32(simulation.maxParticles)) {
    countContacts(globalInvocationId.x, 0u);
  }
}

@compute @workgroup_size(64)
fn countContactsFromBMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  if (globalInvocationId.x < u32(simulation.maxParticles)) {
    countContacts(globalInvocationId.x, 1u);
  }
}

@compute @workgroup_size(64)
fn solveContactsAToBMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  if (globalInvocationId.x < u32(simulation.maxParticles)) {
    solveContacts(globalInvocationId.x, 0u, 1u, 0u);
  }
}

@compute @workgroup_size(64)
fn solveContactsBToAMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  if (globalInvocationId.x < u32(simulation.maxParticles)) {
    solveContacts(globalInvocationId.x, 1u, 0u, 1u);
  }
}

@compute @workgroup_size(64)
fn contactEndMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  let base = index * VECTORS_PER_PARTICLE;
  if (!takesPartInCollisions(particles.data[base + 3u])) {
    return;
  }
  let offset = kinematicsBase(0u, index);
  let motion = kinematics.data[offset];
  let orientation = kinematics.data[offset + 1u];
  var positionVector = particles.data[base + 0u];
  var velocityVector = particles.data[base + 1u];
  positionVector.x = motion.x;
  positionVector.y = motion.y;
  velocityVector.x = motion.z;
  velocityVector.y = motion.w;
  velocityVector.w = orientation.y;
  particles.data[base + 0u] = positionVector;
  particles.data[base + 1u] = velocityVector;
}

var<workgroup> reductionMass: array<f32, 256>;
var<workgroup> reductionIndex: array<u32, 256>;
var<workgroup> chosenSources: array<u32, 16>;
var<workgroup> chosenCount: u32;

@compute @workgroup_size(256)
fn selectGravitySourcesMain(@builtin(local_invocation_index) localIndex: u32) {
  let slotCount = u32(simulation.maxParticles);
  let wantedCount = min(u32(max(simulation.gravitySourceCount, 0.0)), MAXIMUM_GRAVITY_SOURCES);
  if (localIndex == 0u) {
    chosenCount = 0u;
  }
  workgroupBarrier();
  for (var round = 0u; round < wantedCount; round = round + 1u) {
    var bestMass = 0.0;
    var bestIndex = 0xFFFFFFFFu;
    for (var slot = localIndex; slot < slotCount; slot = slot + 256u) {
      let slotMass = bitcast<f32>(atomicLoad(&scratch.data[massListBase() + slot]));
      if (slotMass < MINIMUM_MASS) {
        continue;
      }
      var alreadyChosen = false;
      for (var chosenIndex = 0u; chosenIndex < chosenCount; chosenIndex = chosenIndex + 1u) {
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
    workgroupBarrier();
    for (var stride = 128u; stride > 0u; stride = stride >> 1u) {
      if (localIndex < stride) {
        let candidateMass = reductionMass[localIndex + stride];
        let candidateIndex = reductionIndex[localIndex + stride];
        if (candidateMass > reductionMass[localIndex] ||
          (candidateMass == reductionMass[localIndex] && candidateIndex < reductionIndex[localIndex])) {
          reductionMass[localIndex] = candidateMass;
          reductionIndex[localIndex] = candidateIndex;
        }
      }
      workgroupBarrier();
    }
    if (localIndex == 0u) {
      if (reductionIndex[0] != 0xFFFFFFFFu) {
        chosenSources[chosenCount] = reductionIndex[0];
        chosenCount = chosenCount + 1u;
      }
    }
    workgroupBarrier();
  }
  if (localIndex == 0u) {
    atomicStore(&scratch.data[SCRATCH_SOURCE_COUNT], chosenCount);
    for (var sourceIndex = 0u; sourceIndex < chosenCount; sourceIndex = sourceIndex + 1u) {
      atomicStore(&scratch.data[SCRATCH_SOURCES + sourceIndex], chosenSources[sourceIndex]);
    }
  }
}

fn massOfSlot(slot: u32) -> f32 {
  return bitcast<f32>(atomicLoad(&scratch.data[massListBase() + slot]));
}

fn isGravitySourceSlot(slot: u32, sourceCount: u32) -> bool {
  for (var sourceIndex = 0u; sourceIndex < sourceCount; sourceIndex = sourceIndex + 1u) {
    if (atomicLoad(&scratch.data[SCRATCH_SOURCES + sourceIndex]) == slot) {
      return true;
    }
  }
  return false;
}

fn gravityAccelerationFrom(
  delta: vec2<f32>,
  pullingMass: f32,
  softeningSquared: f32
) -> vec2<f32> {
  let softenedDistanceSquared = dot(delta, delta) + softeningSquared;
  let inverseDistanceCubed = 1.0 / (softenedDistanceSquared * sqrt(softenedDistanceSquared));
  return delta * (simulation.gravitationalConstant * pullingMass * inverseDistanceCubed * simulation.deltaTime);
}

@compute @workgroup_size(64)
fn gravityMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles) || simulation.gravitationalConstant <= 0.0) {
    return;
  }
  let base = index * VECTORS_PER_PARTICLE;
  if (!takesPartInCollisions(particles.data[base + 3u])) {
    return;
  }
  let sourceCount = atomicLoad(&scratch.data[SCRATCH_SOURCE_COUNT]);
  if (sourceCount == 0u) {
    return;
  }
  let positionVector = particles.data[base + 0u];
  var velocityVector = particles.data[base + 1u];
  let selfPosition = positionVector.xy;
  let softeningSquared = simulation.gravitationalSoftening * simulation.gravitationalSoftening;
  let minimumAcceleration = max(simulation.gravityMinimumAcceleration, 1.0e-30);
  let isSource = isGravitySourceSlot(index, sourceCount);
  var acceleration = vec2<f32>(0.0, 0.0);
  for (var sourceIndex = 0u; sourceIndex < sourceCount; sourceIndex = sourceIndex + 1u) {
    let sourceSlot = atomicLoad(&scratch.data[SCRATCH_SOURCES + sourceIndex]);
    if (sourceSlot == index) {
      continue;
    }
    let sourceVector = particles.data[sourceSlot * VECTORS_PER_PARTICLE + 0u];
    if (abs(sourceVector.z - positionVector.z) >= simulation.gravityPlaneThickness) {
      continue;
    }
    let delta = sourceVector.xy - selfPosition;
    let sourceMass = massOfSlot(sourceSlot);
    if (!isSource) {
      let sourceRange = sqrt(simulation.gravitationalConstant * sourceMass / minimumAcceleration);
      if (dot(delta, delta) > sourceRange * sourceRange) {
        continue;
      }
    }
    acceleration = acceleration + gravityAccelerationFrom(delta, sourceMass, softeningSquared);
  }
  if (isSource) {
    let selfMass = massOfSlot(index);
    let range = sqrt(simulation.gravitationalConstant * selfMass / minimumAcceleration);
    let rangeSquared = range * range;
    let columns = i32(simulation.gridColumns);
    let rows = i32(simulation.gridRows);
    let firstColumn = max(i32((selfPosition.x - range - simulation.viewportX) / simulation.cellSize), 0);
    let lastColumn = min(i32((selfPosition.x + range - simulation.viewportX) / simulation.cellSize), columns - 1);
    let firstRow = max(i32((selfPosition.y - range - simulation.viewportY) / simulation.cellSize), 0);
    let lastRow = min(i32((selfPosition.y + range - simulation.viewportY) / simulation.cellSize), rows - 1);
    for (var row = firstRow; row <= lastRow; row = row + 1) {
      for (var column = firstColumn; column <= lastColumn; column = column + 1) {
        let cell = u32(row * columns + column);
        let cellStart = atomicLoad(&grid.data[gridStartsBase() + cell]);
        let cellEnd = atomicLoad(&grid.data[gridStartsBase() + cell + 1u]);
        for (var sortedIndex = cellStart; sortedIndex < cellEnd; sortedIndex = sortedIndex + 1u) {
          let otherSlot = atomicLoad(&grid.data[gridSortedBase() + sortedIndex]);
          if (otherSlot == index || isGravitySourceSlot(otherSlot, sourceCount)) {
            continue;
          }
          let otherVector = particles.data[otherSlot * VECTORS_PER_PARTICLE + 0u];
          let delta = otherVector.xy - selfPosition;
          if (abs(otherVector.z - positionVector.z) >= simulation.gravityPlaneThickness || dot(delta, delta) > rangeSquared) {
            continue;
          }
          acceleration = acceleration + gravityAccelerationFrom(delta, massOfSlot(otherSlot), softeningSquared);
        }
      }
    }
    let largeCount = min(atomicLoad(&scratch.data[SCRATCH_LARGE_COUNT]), u32(simulation.maxParticles));
    for (var largeIndex = 0u; largeIndex < largeCount; largeIndex = largeIndex + 1u) {
      let otherSlot = atomicLoad(&scratch.data[SCRATCH_LARGE_LIST + largeIndex]);
      if (otherSlot == index || isGravitySourceSlot(otherSlot, sourceCount)) {
        continue;
      }
      let otherVector = particles.data[otherSlot * VECTORS_PER_PARTICLE + 0u];
      let delta = otherVector.xy - selfPosition;
      if (abs(otherVector.z - positionVector.z) >= simulation.gravityPlaneThickness || dot(delta, delta) > rangeSquared) {
        continue;
      }
      acceleration = acceleration + gravityAccelerationFrom(delta, massOfSlot(otherSlot), softeningSquared);
    }
  }
  let pulledVelocity = limitSpeed(
    vec3<f32>(velocityVector.x + acceleration.x, velocityVector.y + acceleration.y, velocityVector.z),
    simulation.maxParticleSpeed
  );
  velocityVector.x = pulledVelocity.x;
  velocityVector.y = pulledVelocity.y;
  particles.data[base + 1u] = velocityVector;
}

@compute @workgroup_size(64)
fn accretionOwnerMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  let base = index * VECTORS_PER_PARTICLE;
  if (!takesPartInCollisions(particles.data[base + 3u])) {
    return;
  }
  let gather = gatherContacts(index, STATE_SET, ACCRETION_OWNER_MODE, 0u);
  particles.data[base + 13u] = vec4<f32>(gather.ownerPlusOne, 0.0, 0.0, NO_SPLIT_REQUEST);
}

fn netGravityOf(density: f32) -> f32 {
  return simulation.gravity * (1.0 - simulation.mediumDensity / max(density, MINIMUM_MASS));
}

@compute @workgroup_size(64)
fn accretionApplyMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  let base = index * VECTORS_PER_PARTICLE;
  let lifecycle = particles.data[base + 3u];
  if (!takesPartInCollisions(lifecycle)) {
    return;
  }
  let ownerPlusOne = particles.data[base + 13u].x;
  if (ownerPlusOne > 0.5) {
    let ownerSlot = u32(ownerPlusOne) - 1u;
    if (particles.data[ownerSlot * VECTORS_PER_PARTICLE + 13u].x < 0.5) {
      particles.data[base + 3u] = vec4<f32>(lifecycle.z + lifecycle.y, lifecycle.y, lifecycle.z, lifecycle.w);
    }
    return;
  }
  let gather = gatherContacts(index, STATE_SET, ACCRETION_ABSORB_MODE, 0u);
  if (gather.absorbedCount < 0.5) {
    return;
  }
  var particle = loadParticle(index);
  let consumingMass = particleMass(particle);
  let previousNetWeight = consumingMass * netGravityOf(particle.density);
  var holdFraction = 0.0;
  if (previousNetWeight != 0.0) {
    holdFraction = -particle.thrust.y / previousNetWeight;
  }
  let combinedMass = consumingMass + gather.absorbedMass;
  let combinedVelocity = (particle.velocity.xy * consumingMass + gather.absorbedMomentum) / combinedMass;
  particle.velocity.x = combinedVelocity.x;
  particle.velocity.y = combinedVelocity.y;
  let combinedArea = particleArea(particle) + gather.absorbedArea;
  if (isRectangle(particle)) {
    let sideLength = sqrt(combinedArea);
    particle.sizeA = sideLength;
    particle.sizeB = sideLength;
  } else {
    particle.sizeA = sqrt(combinedArea / PI);
    particle.sizeB = particle.sizeA;
  }
  particle.density = combinedMass / combinedArea;
  let thrustScale = combinedMass / consumingMass;
  particle.thrust.x = particle.thrust.x * thrustScale;
  particle.thrust.z = particle.thrust.z * thrustScale;
  particle.thrust.y = -holdFraction * combinedMass * netGravityOf(particle.density);
  particles.data[base + 1u] = vec4<f32>(particle.velocity, particle.angularVelocity);
  particles.data[base + 2u] = vec4<f32>(particle.thrust, particle.density);
  particles.data[base + 4u] = vec4<f32>(particle.shapeKind, particle.sizeA, particle.sizeB, particle.scaleRate);
}

@compute @workgroup_size(64)
fn freeSlotsMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  if (particles.data[index * VECTORS_PER_PARTICLE + 3u].w < 0.5) {
    let freePosition = atomicAdd(&scratch.data[SCRATCH_FREE_COUNT], 1u);
    atomicStore(&scratch.data[freeListBase() + freePosition], index);
  }
}

fn wallContactPlaneZ(identifier: f32) -> f32 {
  let planeSpacing = simulation.gravityPlaneThickness * 2.0;
  let planeCount = max(1.0, floor(simulation.collisionPlaneThickness / planeSpacing) - 1.0);
  return (floor(identifier % planeCount) + 1.0) * planeSpacing;
}

@compute @workgroup_size(64)
fn splitMain(@builtin(global_invocation_id) globalInvocationId: vec3<u32>) {
  let index = globalInvocationId.x;
  if (index >= u32(simulation.maxParticles)) {
    return;
  }
  let base = index * VECTORS_PER_PARTICLE;
  if (!takesPartInCollisions(particles.data[base + 3u])) {
    return;
  }
  let splitRequest = particles.data[base + 13u];
  if (splitRequest.w < 0.0) {
    return;
  }
  var particle = loadParticle(index);
  let requestedCount = clamp(
    floor(splitRequest.w / simulation.shatterMomentumPerFragment),
    simulation.shatterMinimumFragmentCount,
    simulation.shatterMaximumFragmentCount
  );
  let currentRadius = collisionRadius(particle);
  let fittingCount = floor(currentRadius / simulation.shatterMinimumFragmentRadius);
  let wantedCount = min(requestedCount, fittingCount);
  if (wantedCount < simulation.shatterMinimumFragmentCount) {
    return;
  }
  let wantedChildren = u32(wantedCount) - 1u;
  let claimStart = atomicAdd(&scratch.data[SCRATCH_FREE_CURSOR], wantedChildren);
  let freeCount = atomicLoad(&scratch.data[SCRATCH_FREE_COUNT]);
  var grantedChildren = 0u;
  if (freeCount > claimStart) {
    grantedChildren = min(freeCount - claimStart, wantedChildren);
  }
  let fragmentCount = f32(grantedChildren + 1u);
  if (fragmentCount < simulation.shatterMinimumFragmentCount) {
    return;
  }
  let normal = splitRequest.yz;
  let tangent = vec2<f32>(-normal.y, normal.x);
  let sourcePosition = particle.position.xy;
  let sourceVelocity = particle.velocity.xy;
  let centerOffset = (fragmentCount - 1.0) * 0.5;
  let fragmentMassRatio = 1.0 / (fragmentCount * fragmentCount);
  let fragmentDiameter = currentRadius / fragmentCount * 2.0;
  let parentMass = particleMass(particle);
  let fragmentMass = parentMass * fragmentMassRatio;
  let parentNetWeight = parentMass * netGravityOf(particle.density);
  var holdFraction = 0.0;
  if (parentNetWeight != 0.0) {
    holdFraction = -particle.thrust.y / parentNetWeight;
  }
  let spreadVelocity = simulation.shatterSpreadSpeed * 2.0;
  let childThrustY = -holdFraction * fragmentMass * netGravityOf(particle.density);
  let freeBase = freeListBase();
  for (var childIndex = 1u; childIndex <= grantedChildren; childIndex = childIndex + 1u) {
    let offset = f32(childIndex) - centerOffset;
    var child = particle;
    child.position.x = sourcePosition.x + tangent.x * fragmentDiameter * offset;
    child.position.y = sourcePosition.y + tangent.y * fragmentDiameter * offset;
    child.velocity.x = sourceVelocity.x + tangent.x * spreadVelocity * offset;
    child.velocity.y = sourceVelocity.y + tangent.y * spreadVelocity * offset;
    child.sizeA = particle.sizeA / fragmentCount;
    child.sizeB = particle.sizeB / fragmentCount;
    child.thrust = vec3<f32>(particle.thrust.x * fragmentMassRatio, childThrustY, particle.thrust.z * fragmentMassRatio);
    child.uniqueId = SPLIT_IDENTIFIER_BASE + f32(atomicAdd(&scratch.data[SCRATCH_SPLIT_IDENTIFIER_COUNTER], 1u));
    if (particle.materialReservedA > 0.5) {
      child.position.z = wallContactPlaneZ(child.uniqueId);
    }
    child.alive = 1.0;
    child.scratchA = vec4<f32>(0.0, 0.0, 0.0, 0.0);
    child.scratchB = vec4<f32>(0.0, 0.0, 0.0, NO_SPLIT_REQUEST);
    storeParticle(atomicLoad(&scratch.data[freeBase + claimStart + childIndex - 1u]), child);
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
  particle.scratchB = vec4<f32>(0.0, 0.0, 0.0, NO_SPLIT_REQUEST);
  storeParticle(index, particle);
}

struct RenderUniforms {
  viewportX: f32,
  viewportY: f32,
  canvasWidth: f32,
  canvasHeight: f32,
  renderScale: f32,
  spriteFrameWidth: f32,
  spriteFrameHeight: f32,
  spriteSheetFrameCount: f32,
  spriteTicksPerFrame: f32,
  dripTrailRadiusRatio: f32,
  dripNeckLengthRatio: f32,
  dripGlossIntensity: f32,
  dripGlossStripeOffsetRatio: f32,
  dripGlossStripeWidthRatio: f32,
  dripGlossSpecularRadiusRatio: f32,
  dripGlossSpecularOffsetRatio: f32,
  blobLobeCount: f32,
  blobAmplitudeRatio: f32,
  blobBaseFrequency: f32,
  blobFrequencyStep: f32,
  blobPhaseStep: f32,
  reservedA: f32,
  reservedB: f32,
  reservedC: f32,
};

struct VertexOutput {
  @builtin(position) position: vec4<f32>,
  @location(0) color: vec4<f32>,
  @location(1) quadCoordinate: vec2<f32>,
  @location(2) renderKind: f32,
  @location(3) shapeKind: f32,
  @location(4) uv: vec2<f32>,
  @location(5) age: f32,
  @location(6) pixelRadius: f32,
};

@group(0) @binding(0) var<storage, read> renderParticles: ParticleBuffer;
@group(0) @binding(1) var<uniform> renderUniforms: RenderUniforms;
@group(0) @binding(2) var renderSampler: sampler;
@group(0) @binding(3) var renderTexture: texture_2d<f32>;

const BUBBLE_FILL_TOP_LEFT_ALPHA = 0.4;
const BUBBLE_FILL_BOTTOM_RIGHT_ALPHA = 0.051;
const BUBBLE_RIM_ALPHA = 0.8;
const BUBBLE_RIM_STROKE_WIDTH_PIXELS = 2.0;
const BUBBLE_HIGHLIGHT_ALPHA = 0.9;
const BUBBLE_HIGHLIGHT_RADIUS_RATIO = 0.28;
const BUBBLE_HIGHLIGHT_OFFSET_RATIO = 0.38;
const MAXIMUM_BLOB_LOBES = 16u;

fn quadCorner(vertexIndex: u32) -> vec2<f32> {
  switch (vertexIndex) {
    case 0u: { return vec2<f32>(-1.0, -1.0); }
    case 1u: { return vec2<f32>(1.0, -1.0); }
    case 2u: { return vec2<f32>(1.0, 1.0); }
    case 3u: { return vec2<f32>(-1.0, -1.0); }
    case 4u: { return vec2<f32>(1.0, 1.0); }
    default: { return vec2<f32>(-1.0, 1.0); }
  }
}

fn hiddenVertex() -> VertexOutput {
  var output: VertexOutput;
  output.position = vec4<f32>(-2.0, -2.0, 0.0, 1.0);
  output.color = vec4<f32>(0.0, 0.0, 0.0, 0.0);
  output.quadCoordinate = vec2<f32>(0.0, 0.0);
  output.renderKind = 0.0;
  output.shapeKind = 0.0;
  output.uv = vec2<f32>(0.0, 0.0);
  output.age = 0.0;
  output.pixelRadius = 0.0;
  return output;
}

@vertex
fn vertexMain(
  @builtin(vertex_index) vertexIndex: u32,
  @builtin(instance_index) instanceIndex: u32
) -> VertexOutput {
  let base = instanceIndex * VECTORS_PER_PARTICLE;
  let positionAngle = renderParticles.data[base + 0u];
  let lifecycle = renderParticles.data[base + 3u];
  let dimensions = renderParticles.data[base + 4u];
  let kindData = renderParticles.data[base + 5u];
  let color = renderParticles.data[base + 8u];
  let origin = renderParticles.data[base + 11u];
  let age = lifecycle.x;
  let delay = lifecycle.y;
  if (lifecycle.w < 0.5 || age < delay) {
    return hiddenVertex();
  }
  let scale = renderUniforms.renderScale;
  let renderKind = kindData.z;
  let isRectangleShape = dimensions.x > 0.5;
  let isSprite = renderKind > 1.5 && renderKind < 3.5;
  let isBlob = renderKind > 3.5 && renderKind < 4.5;
  let isDrip = renderKind > 5.5;
  var halfExtent = vec2<f32>(dimensions.y * 0.5, dimensions.y * 0.5);
  if (isRectangleShape) {
    halfExtent = vec2<f32>(dimensions.y * 0.5, dimensions.z * 0.5);
  } else {
    halfExtent = vec2<f32>(dimensions.y, dimensions.y);
  }
  if (isSprite) {
    halfExtent = vec2<f32>(renderUniforms.spriteFrameWidth * 0.5, renderUniforms.spriteFrameHeight * 0.5);
  }
  if (isBlob) {
    halfExtent = halfExtent * (1.0 + renderUniforms.blobAmplitudeRatio);
  }
  halfExtent = max(halfExtent * scale, vec2<f32>(0.5, 0.5));
  let corner = quadCorner(vertexIndex);
  var quadCenter = positionAngle.xy;
  var quadOffset = vec2<f32>(corner.x * halfExtent.x, corner.y * halfExtent.y);
  if (isRectangleShape && !isSprite) {
    let cosine = cos(positionAngle.w);
    let sine = sin(positionAngle.w);
    quadOffset = vec2<f32>(
      quadOffset.x * cosine - quadOffset.y * sine,
      quadOffset.x * sine + quadOffset.y * cosine
    );
  }
  var uv = vec2<f32>((corner.x + 1.0) * 0.5, (corner.y + 1.0) * 0.5);
  if (isSprite) {
    var frameIndex = 0.0;
    if (renderKind > 2.5) {
      frameIndex = floor(age / renderUniforms.spriteTicksPerFrame) % max(origin.w, 1.0);
    }
    uv = vec2<f32>((uv.x + frameIndex) / max(renderUniforms.spriteSheetFrameCount, 1.0), uv.y);
  }
  if (isDrip) {
    let beadRadius = dimensions.y;
    let tail = origin.xy;
    let head = positionAngle.xy;
    let axis = (head - tail) * scale;
    let axisLength = length(axis);
    var direction = vec2<f32>(0.0, 1.0);
    if (axisLength > 0.0001) {
      direction = axis / axisLength;
    }
    let beadPixels = max(beadRadius * scale, 0.5);
    let halfLength = axisLength * 0.5 + beadPixels;
    quadCenter = (head + tail) * 0.5;
    quadOffset = direction * (corner.x * halfLength) + vec2<f32>(-direction.y, direction.x) * (corner.y * beadPixels);
    uv = vec2<f32>(halfLength, beadPixels);
  }
  let localX = (quadCenter.x - renderUniforms.viewportX) * scale + quadOffset.x;
  let localY = (quadCenter.y - renderUniforms.viewportY) * scale + quadOffset.y;
  var output: VertexOutput;
  output.position = vec4<f32>(
    (localX / renderUniforms.canvasWidth) * 2.0 - 1.0,
    -((localY / renderUniforms.canvasHeight) * 2.0 - 1.0),
    0.0,
    1.0
  );
  output.color = color;
  output.quadCoordinate = corner;
  output.renderKind = renderKind;
  output.shapeKind = dimensions.x;
  output.uv = uv;
  output.age = age;
  output.pixelRadius = max(dimensions.y * scale, 1.0);
  return output;
}

fn blobBoundaryScale(angle: f32, age: f32) -> f32 {
  let lobeCount = min(u32(renderUniforms.blobLobeCount), MAXIMUM_BLOB_LOBES);
  let lobeStep = TWO_PI / f32(lobeCount);
  var normalizedAngle = angle;
  if (normalizedAngle < 0.0) {
    normalizedAngle = normalizedAngle + TWO_PI;
  }
  let position = normalizedAngle / lobeStep;
  let segmentIndex = u32(floor(position)) % lobeCount;
  let fraction = position - floor(position);
  var lobeRadii = array<f32, 4>(1.0, 1.0, 1.0, 1.0);
  for (var tapIndex = 0u; tapIndex < 4u; tapIndex = tapIndex + 1u) {
    let lobeIndex = (segmentIndex + lobeCount + tapIndex - 1u) % lobeCount;
    let frequency = renderUniforms.blobBaseFrequency + f32(lobeIndex) * renderUniforms.blobFrequencyStep;
    let phase = f32(lobeIndex) * renderUniforms.blobPhaseStep;
    lobeRadii[tapIndex] = 1.0 + renderUniforms.blobAmplitudeRatio * sin(age * frequency + phase);
  }
  let startTangent = (lobeRadii[2] - lobeRadii[0]) * 0.5;
  let endTangent = (lobeRadii[3] - lobeRadii[1]) * 0.5;
  let squared = fraction * fraction;
  let cubed = squared * fraction;
  return (2.0 * cubed - 3.0 * squared + 1.0) * lobeRadii[1] +
    (cubed - 2.0 * squared + fraction) * startTangent +
    (-2.0 * cubed + 3.0 * squared) * lobeRadii[2] +
    (cubed - squared) * endTangent;
}

fn bubbleFragmentColor(quadCoordinate: vec2<f32>, pixelRadius: f32) -> vec4<f32> {
  let distanceFromCenter = length(quadCoordinate);
  if (distanceFromCenter > 1.0) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  let diagonal = (quadCoordinate.x + quadCoordinate.y) * 0.5;
  let gradientProgress = clamp((diagonal + 1.0) * 0.5, 0.0, 1.0);
  let fillAlpha = mix(BUBBLE_FILL_TOP_LEFT_ALPHA, BUBBLE_FILL_BOTTOM_RIGHT_ALPHA, gradientProgress);
  var outputColor = vec4<f32>(1.0, 1.0, 1.0, fillAlpha);
  let rimStrokeWidthNormalized = BUBBLE_RIM_STROKE_WIDTH_PIXELS / max(pixelRadius, 1.0);
  let rimInnerEdge = 1.0 - rimStrokeWidthNormalized;
  if (distanceFromCenter >= rimInnerEdge) {
    outputColor = vec4<f32>(
      mix(outputColor.rgb, vec3<f32>(1.0, 1.0, 1.0), BUBBLE_RIM_ALPHA),
      outputColor.a + BUBBLE_RIM_ALPHA * (1.0 - outputColor.a)
    );
  }
  let highlightCenter = vec2<f32>(-BUBBLE_HIGHLIGHT_OFFSET_RATIO, -BUBBLE_HIGHLIGHT_OFFSET_RATIO);
  let highlightDistance = length(quadCoordinate - highlightCenter) / BUBBLE_HIGHLIGHT_RADIUS_RATIO;
  if (highlightDistance < 1.0) {
    let highlightSourceAlpha = BUBBLE_HIGHLIGHT_ALPHA * (1.0 - highlightDistance);
    outputColor = vec4<f32>(
      mix(outputColor.rgb, vec3<f32>(1.0, 1.0, 1.0), highlightSourceAlpha),
      outputColor.a + highlightSourceAlpha * (1.0 - outputColor.a)
    );
  }
  return outputColor;
}

fn dripFragmentColor(color: vec4<f32>, quadCoordinate: vec2<f32>, uv: vec2<f32>) -> vec4<f32> {
  let beadRadius = uv.y;
  let trailRadius = beadRadius * renderUniforms.dripTrailRadiusRatio;
  let streakLength = max(2.0 * (uv.x - uv.y), 0.0);
  let neckSetting = max(beadRadius * renderUniforms.dripNeckLengthRatio, 0.0001);
  let neckLength = min(streakLength, neckSetting);
  let tailRadius = mix(beadRadius, trailRadius, clamp(streakLength / neckSetting, 0.0, 1.0));
  let axisPosition = quadCoordinate.x * uv.x + streakLength * 0.5;
  let perpendicularPosition = quadCoordinate.y * uv.y;
  var isInside = false;
  if (axisPosition < 0.0) {
    isInside = length(vec2<f32>(axisPosition, perpendicularPosition)) <= tailRadius;
  } else if (axisPosition > streakLength) {
    isInside = length(vec2<f32>(axisPosition - streakLength, perpendicularPosition)) <= beadRadius;
  } else {
    let neckProgress = clamp((axisPosition - (streakLength - neckLength)) / max(neckLength, 0.0001), 0.0, 1.0);
    isInside = abs(perpendicularPosition) <= mix(tailRadius, beadRadius, neckProgress);
  }
  if (!isInside) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  var stripeGloss = 0.0;
  if (axisPosition >= 0.0 && axisPosition <= streakLength - neckLength) {
    let stripeWidth = max(trailRadius * renderUniforms.dripGlossStripeWidthRatio, 0.0001);
    stripeGloss = clamp(
      1.0 - abs(perpendicularPosition - trailRadius * renderUniforms.dripGlossStripeOffsetRatio) / stripeWidth,
      0.0,
      1.0
    );
  }
  let specularOffset = beadRadius * renderUniforms.dripGlossSpecularOffsetRatio;
  let specularCenter = vec2<f32>(streakLength - specularOffset, specularOffset);
  let specularRadius = max(beadRadius * renderUniforms.dripGlossSpecularRadiusRatio, 0.0001);
  let specularGloss = clamp(
    1.0 - length(vec2<f32>(axisPosition, perpendicularPosition) - specularCenter) / specularRadius,
    0.0,
    1.0
  );
  let glossAmount = clamp(max(stripeGloss, specularGloss) * renderUniforms.dripGlossIntensity, 0.0, 1.0);
  return vec4<f32>(mix(color.rgb, vec3<f32>(1.0, 1.0, 1.0), glossAmount), color.a);
}

@fragment
fn fragmentMainShapes(
  @location(0) color: vec4<f32>,
  @location(1) quadCoordinate: vec2<f32>,
  @location(2) renderKind: f32,
  @location(3) shapeKind: f32,
  @location(4) uv: vec2<f32>,
  @location(5) age: f32,
  @location(6) pixelRadius: f32
) -> @location(0) vec4<f32> {
  if (renderKind > 0.5 && renderKind < 1.5) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  if (renderKind > 1.5 && renderKind < 3.5) {
    return textureSampleLevel(renderTexture, renderSampler, uv, 0.0);
  }
  if (renderKind > 3.5 && renderKind < 4.5) {
    let boundaryScale = blobBoundaryScale(atan2(quadCoordinate.y, quadCoordinate.x), age);
    if (length(quadCoordinate) * (1.0 + renderUniforms.blobAmplitudeRatio) > boundaryScale) {
      return vec4<f32>(0.0, 0.0, 0.0, 0.0);
    }
    return color;
  }
  if (renderKind > 4.5 && renderKind < 5.5) {
    return bubbleFragmentColor(quadCoordinate, pixelRadius);
  }
  if (renderKind > 5.5) {
    return dripFragmentColor(color, quadCoordinate, uv);
  }
  if (shapeKind < 0.5 && dot(quadCoordinate, quadCoordinate) > 1.0) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return color;
}

@fragment
fn fragmentMainDust(
  @location(0) color: vec4<f32>,
  @location(1) quadCoordinate: vec2<f32>,
  @location(2) renderKind: f32,
  @location(3) shapeKind: f32,
  @location(4) uv: vec2<f32>,
  @location(5) age: f32,
  @location(6) pixelRadius: f32
) -> @location(0) vec4<f32> {
  let layoutAnchorSample = textureSampleLevel(renderTexture, renderSampler, uv, 0.0);
  if (renderKind < 0.5 || renderKind > 1.5) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  if (shapeKind < 0.5 && dot(quadCoordinate, quadCoordinate) > 1.0) {
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
  }
  return vec4<f32>(color.rgb * color.a, color.a) + layoutAnchorSample * 0.0;
}
