import Engine from './Engine.js';
import ParticleFactory from './Factory.js';

const canvas = document.getElementById('particleCanvas');
const ctx = canvas.getContext('2d');

const particleImage = new Image();
particleImage.src = 'image.png';

const particleFactory = new ParticleFactory(particleImage);

const fpsLabel = document.createElement('div');
fpsLabel.style.position = 'absolute';
fpsLabel.style.top = '10px';
fpsLabel.style.left = '10px';
fpsLabel.style.padding = '4px 8px';
fpsLabel.style.background = 'rgba(0, 0, 0, 0.7)';
fpsLabel.style.color = '#00ffcc';
fpsLabel.style.fontFamily = 'monospace';
fpsLabel.style.fontSize = '14px';
fpsLabel.style.borderRadius = '4px';
fpsLabel.style.pointerEvents = 'none';
fpsLabel.textContent = 'FPS: --';
document.body.appendChild(fpsLabel);

const engine = new Engine({
  gravity: { x: 0, y: 0.05, z: 0 },
  globalDrag: 0.01
});

let particles = [];

const createBtn = document.getElementById('createBtn');
const particleModeSelect = document.getElementById('particleMode');

createBtn.addEventListener('click', () => {
  const mode = particleModeSelect.value;
  const centerX = canvas.width / 2;
  const centerY = canvas.height / 2;

  if (mode === 'projectile' || mode === 'itemReturn') {
    // Guided launch modes pass an explicit destination.
    particles = particleFactory.create(mode, centerX, centerY, canvas.width, 0);
    return;
  }

  if (mode === 'collision') {
    const collisionDirection = 'left';
    particles = particleFactory.create(mode, centerX, centerY, undefined, undefined, collisionDirection);
    return;
  }

  particles = particleFactory.create(mode, centerX, centerY);
});

// Initial load
particles = particleFactory.create('dust', canvas.width / 2, canvas.height / 2);

function handleCollisions(particles) {
  for (let i = 0; i < particles.length; i++) {
    for (let j = i + 1; j < particles.length; j++) {
      const p1 = particles[i];
      const p2 = particles[j];

      if (!p1.canCollide || !p2.canCollide) continue;

      const r1 = p1.radius || p1.width / 2;
      const r2 = p2.radius || p2.width / 2;

      const dx = p2.x - p1.x;
      const dy = p2.y - p1.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const minDist = r1 + r2;

      if (dist < minDist && dist > 0) {
        const overlap = minDist - dist;
        const nx = dx / dist;
        const ny = dy / dist;

        const m1 = p1.mass || 1;
        const m2 = p2.mass || 1;
        const totalMass = m1 + m2;

        p1.x -= nx * overlap * (m2 / totalMass);
        p1.y -= ny * overlap * (m2 / totalMass);
        p2.x += nx * overlap * (m1 / totalMass);
        p2.y += ny * overlap * (m1 / totalMass);

        const kx = p1.xVelocity - p2.xVelocity;
        const ky = p1.yVelocity - p2.yVelocity;
        const p = 2 * (nx * kx + ny * ky) / totalMass;

        const restitution = Math.min(p1.restitution ?? 0.5, p2.restitution ?? 0.5);

        p1.xVelocity -= p * m2 * nx * (1 + restitution);
        p1.yVelocity -= p * m2 * ny * (1 + restitution);
        p2.xVelocity += p * m1 * nx * (1 + restitution);
        p2.yVelocity += p * m1 * ny * (1 + restitution);
      }
    }
  }
}

let lastTime = performance.now();
let frameCount = 0;
let fps = 0;

function animate(currentTime) {
  frameCount++;
  if (currentTime - lastTime >= 1000) {
    fps = Math.round((frameCount * 1000) / (currentTime - lastTime));
    fpsLabel.textContent = `FPS: ${fps} | Particles: ${particles.length}`;
    frameCount = 0;
    lastTime = currentTime;
  }

  ctx.clearRect(0, 0, canvas.width, canvas.height);

  const expiredParticles = engine.update(particles, 1);
  expiredParticles.forEach(p => {
    if (p.type === 'fireworkTail') {
      particles.push(...particleFactory.create('fireworkBurst', p.x, p.y));
    }
  });
  handleCollisions(particles);

  particles.forEach(p => {
    if (p.isRenderable === false) return;
    ctx.save();
    ctx.globalAlpha = Math.max(0, Math.min(1, p.alpha));

    if (p.type === 'image' && p.imageData && p.imageData.complete) {
      const w = p.width || p.radius * 2;
      const h = p.height || p.radius * 2;
      ctx.drawImage(p.imageData, p.x - w / 2, p.y - h / 2, w, h);
    } else {
      ctx.fillStyle = p.color;
      if (p.shape === 'circle') {
        ctx.beginPath();
        ctx.arc(p.x, p.y, Math.max(0, p.radius), 0, Math.PI * 2);
        ctx.fill();
      } else {
        ctx.fillRect(p.x - p.width / 2, p.y - p.height / 2, p.width, p.height);
      }
    }

    ctx.restore();
  });

  requestAnimationFrame(animate);
}

requestAnimationFrame(animate);
