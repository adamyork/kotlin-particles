import Particle from './Particle.js';

export default class Factory {
  constructor(particleImage) {
    this.particleImage = particleImage;
  }

  create(mode, canvas) {
    const newParticles = [];
    const width = canvas ? canvas.width : 800;
    const height = canvas ? canvas.height : 600;
    const randomColor = () => `#${Math.floor(Math.random() * 16777215).toString(16).padStart(6, '0')}`;

    if (mode === 'dust') {
      const cloudX = width / 2;
      const cloudY = height / 2;
      const puffCount = 8;
      const clusterRadius = 12;
      for (let j = 0; j < puffCount; j++) {
        const angle = (j / puffCount) * Math.PI * 2;
        const dist = Math.random() * (clusterRadius * 0.6) + (clusterRadius * 0.3);
        const offsetX = Math.cos(angle) * dist;
        const offsetY = Math.sin(angle) * dist;
        const radius = Math.random() * 5 + 6; // Puffy overlapping circles
        newParticles.push(new Particle({
          type: 'default',
          shape: 'circle',
          lifetime: 128,
          color: '#ffffff',
          alpha: 0.85,
          endAlpha: 0,
          radius: radius,
          x: cloudX + offsetX,
          y: cloudY + offsetY,
          xVelocity: 0,
          yVelocity: 0,
          xAcceleration: 0,
          yAcceleration: 0,
          zAcceleration: 0,
          mass: 0,
          canCollide: false
        }));
      }
      newParticles.push(new Particle({
        type: 'default',
        shape: 'circle',
        lifetime: 128,
        color: '#ffffff',
        alpha: 0.85,
        endAlpha: 0,
        radius: 9,
        x: cloudX,
        y: cloudY,
        xVelocity: 0,
        yVelocity: 0,
        xAcceleration: 0,
        yAcceleration: 0,
        zAcceleration: 0,
        mass: 0,
        canCollide: false
      }));
    } else if (mode === 'collision') {
      for (let i = 0; i < 30; i++) {
        const radius = Math.random() * 15 + 10;
        newParticles.push(new Particle({
          type: 'default',
          shape: 'circle',
          lifetime: 1000,
          color: randomColor(),
          alpha: 0.9,
          radius: radius,
          mass: radius,
          restitution: 0.8,
          x: Math.random() * (width - 200) + 100,
          y: Math.random() * (height - 200) + 100,
          xVelocity: (Math.random() - 0.5) * 6,
          yVelocity: (Math.random() - 0.5) * 6,
          canCollide: true
        }));
      }
    } else if (mode === 'projectile') {
      for (let i = 0; i < 40; i++) {
        newParticles.push(new Particle({
          type: 'spark',
          shape: 'circle',
          lifetime: 150,
          color: '#ff5500',
          alpha: 1,
          radius: 4,
          mass: 1,
          x: width / 2,
          y: height - 50,
          xVelocity: (Math.random() - 0.5) * 8,
          yVelocity: -Math.random() * 12 - 4,
          yAcceleration: 0.2,
          canCollide: false
        }));
      }
    } else if (mode === 'firework') {
      const cx = width / 2;
      const cy = height / 2;
      const color = randomColor();
      for (let i = 0; i < 100; i++) {
        const angle = Math.random() * Math.PI * 2;
        const speed = Math.random() * 6 + 2;
        newParticles.push(new Particle({
          type: 'spark',
          shape: 'circle',
          lifetime: 120,
          color: color,
          alpha: 1,
          radius: 3,
          mass: 1,
          x: cx,
          y: cy,
          xVelocity: Math.cos(angle) * speed,
          yVelocity: Math.sin(angle) * speed,
          yAcceleration: 0.05,
          drag: 0.02,
          canCollide: false
        }));
      }
    } else if (mode === 'itemReturns') {
      for (let i = 0; i < 20; i++) {
        newParticles.push(new Particle({
          type: 'image',
          shape: 'circle',
          lifetime: 300,
          color: '#ffffff',
          alpha: 1,
          radius: 20,
          width: 40,
          height: 40,
          mass: 1,
          x: Math.random() * width,
          y: Math.random() * height,
          xVelocity: (Math.random() - 0.5) * 3,
          yVelocity: (Math.random() - 0.5) * 3,
          canCollide: false,
          imageData: this.particleImage
        }));
      }
    }

    newParticles.forEach(p => {
      if (p.mass === 0) {
        p.mass = undefined;
        p.yAcceleration = 0;
        p.xAcceleration = 0;
        p.zAcceleration = 0;
      }
    });

    return newParticles;
  }
}