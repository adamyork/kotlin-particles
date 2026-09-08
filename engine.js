function interpolateColor(color1, color2, factor) {
  const c1 = parseInt(color1.replace('#', ''), 16);
  const c2 = parseInt(color2.replace('#', ''), 16);

  const r1 = (c1 >> 16) & 255;
  const g1 = (c1 >> 8) & 255;
  const b1 = c1 & 255;

  const r2 = (c2 >> 16) & 255;
  const g2 = (c2 >> 8) & 255;
  const b2 = c2 & 255;

  const r = Math.round(r1 + factor * (r2 - r1));
  const g = Math.round(g1 + factor * (g2 - g1));
  const b = Math.round(b1 + factor * (b2 - b1));

  return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;
}

export default class Engine {
  constructor({
    gravity = { x: 0, y: 0, z: 0 },
    globalDrag = 0
  } = {}) {
    this.gravity = gravity;
    this.globalDrag = globalDrag;
  }

  update(particles, deltaTime = 1) {
    const expiredParticles = [];
    for (let i = particles.length - 1; i >= 0; i--) {
      const p = particles[i];

      if (p.age >= (p.lifetime + p.delay)) {
        expiredParticles.push(p);
        particles.splice(i, 1);
        continue;
      }

      p.age += deltaTime;

      // Delayed particles should exist but stay hidden until their delay elapses.
      p.isRenderable = p.age >= p.delay;

      if (p.age < p.delay) continue;

      const activeAge = p.age - p.delay;
      const progress = Math.max(0, Math.min(1, activeAge / p.lifetime));

      p.xVelocity += p.xAcceleration * deltaTime;
      p.yVelocity += p.yAcceleration * deltaTime;
      p.zVelocity += p.zAcceleration * deltaTime;

      if (p.mass && p.mass > 0) {
        const gravityScale = Math.min(1, p.mass);
        p.xVelocity += this.gravity.x * gravityScale * deltaTime;
        p.yVelocity += this.gravity.y * gravityScale * deltaTime;
        p.zVelocity += this.gravity.z * gravityScale * deltaTime;
      }

      const totalDrag = Math.max(0, p.drag + this.globalDrag);
      if (totalDrag > 0) {
        const dragFactor = Math.max(0, 1 - totalDrag * deltaTime);
        p.xVelocity *= dragFactor;
        p.yVelocity *= dragFactor;
        p.zVelocity *= dragFactor;
      }

      if (p.maxXVelocity > 0) {
        p.xVelocity = Math.max(-p.maxXVelocity, Math.min(p.maxXVelocity, p.xVelocity));
      }
      if (p.maxYVelocity > 0) {
        p.yVelocity = Math.max(-p.maxYVelocity, Math.min(p.maxYVelocity, p.yVelocity));
      }
      if (p.maxZVelocity > 0) {
        p.zVelocity = Math.max(-p.maxZVelocity, Math.min(p.maxZVelocity, p.zVelocity));
      }

      p.x += p.xVelocity * deltaTime;
      p.y += p.yVelocity * deltaTime;
      p.z += p.zVelocity * deltaTime;

      if (p.growthRate !== 0) {
        p.radius = Math.max(0, Math.min(p.maxRadius, p.radius + p.growthRate * deltaTime));
        p.width = Math.max(0, Math.min(p.maxWidth, p.width + p.growthRate * deltaTime));
        p.height = Math.max(0, Math.min(p.maxHeight, p.height + p.growthRate * deltaTime));
      }

      if (p.startColor && p.endColor) {
        p.color = interpolateColor(p.startColor, p.endColor, progress);
      }
      if (p.endAlpha !== undefined) {
        const initialAlpha = p._initialAlpha ?? p.alpha;
        p._initialAlpha = initialAlpha;
        p.alpha = initialAlpha + (p.endAlpha - initialAlpha) * progress;
      }
    }

    return expiredParticles;
  }
}
