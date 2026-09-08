import Particle from './Particle.js';

export default class Factory {
  constructor(particleImage) {
    this.particleImage = particleImage;
  }

  create(mode, x, y, destinationX, destinationY, direction) {
    const centerX = typeof x === 'number' ? x : 400;
    const centerY = typeof y === 'number' ? y : 300;
    const width = Math.max(1, centerX * 2);
    const height = Math.max(1, centerY * 2);
    const context = {
      centerX,
      centerY,
      width,
      height,
      destinationX,
      destinationY,
      direction
    };

    let newParticles;
    switch (mode) {
      case 'dust':
        newParticles = this.createDustParticles(context);
        break;
      case 'collision':
        newParticles = this.createCollisionParticles(context);
        break;
      case 'projectile':
        newParticles = this.createProjectileParticles(context);
        break;
      case 'fireworkBurst':
        newParticles = this.createFireworkBurstParticles(context);
        break;
      case 'fireworkTails':
        newParticles = this.createFireworkTailParticles(context);
        break;
      case 'itemReturn':
        newParticles = this.createItemReturnParticles(context);
        break;
      default:
        newParticles = [];
    }

    this.normalizeMasslessParticles(newParticles);
    return newParticles;
  }

  randomColor() {
    return `#${Math.floor(Math.random() * 16777215).toString(16).padStart(6, '0')}`;
  }

  createDistinctColorPair() {
    const startColor = this.randomColor();
    let endColor = this.randomColor();
    while (endColor === startColor) {
      endColor = this.randomColor();
    }
    return { startColor, endColor };
  }

  createDustPuff(x, y, radius) {
    const destinationAngle = Math.random() * Math.PI * 2;
    const destinationDistance = 8 + Math.random() * 4;
    const destinationX = x + Math.cos(destinationAngle) * destinationDistance;
    const destinationY = y + Math.sin(destinationAngle) * destinationDistance;
    const deltaX = destinationX - x;
    const deltaY = destinationY - y;
    const length = Math.hypot(deltaX, deltaY) || 1;
    const speed = 0.06 + Math.random() * 0.04;

    return new Particle({
      type: 'default',
      shape: 'circle',
      lifetime: 128,
      color: '#ffffff',
      alpha: 0.85,
      endAlpha: 0,
      radius,
      x,
      y,
      originX: x,
      originY: y,
      destinationX,
      destinationY,
      xVelocity: (deltaX / length) * speed,
      yVelocity: (deltaY / length) * speed,
      xAcceleration: 0,
      yAcceleration: 0,
      zAcceleration: 0,
      mass: 0,
      canCollide: false
    });
  }

  createDustParticles({ centerX, centerY }) {
    const newParticles = [];
    const puffCount = 16;
    const clusterRadius = 12;

    for (let j = 0; j < puffCount; j++) {
      const angle = (j / puffCount) * Math.PI * 2;
      const dist = Math.random() * (clusterRadius * 0.6) + (clusterRadius * 0.3);
      const offsetX = Math.cos(angle) * dist;
      const offsetY = Math.sin(angle) * dist;
      const radius = Math.random() * 5 + 6;
      newParticles.push(this.createDustPuff(centerX + offsetX, centerY + offsetY, radius));
    }

    newParticles.push(this.createDustPuff(centerX, centerY, 9));
    return newParticles;
  }

  createCollisionParticles({ centerX, centerY, direction }) {
    const newParticles = [];
    const collisionDirection = direction === 'right' ? 'right' : 'left';
    const explodeAngle = collisionDirection === 'left' ? 0 : Math.PI;

    for (let i = 0; i < 256; i++) {
      const radius = Math.random() * 2.5 + 2;
      const spread = (Math.random() - 0.5) * 1.2;
      const velocityAngle = explodeAngle + spread;
      const speed = Math.random() * 6 + 4;
      newParticles.push(new Particle({
        type: 'default',
        shape: 'circle',
        lifetime: 240,
        color: this.randomColor(),
        alpha: 0.95,
        radius,
        mass: radius,
        restitution: 0.8,
        x: centerX,
        y: centerY,
        originX: centerX,
        originY: centerY,
        xVelocity: Math.cos(velocityAngle) * speed,
        yVelocity: Math.sin(velocityAngle) * speed,
        drag: 0.01,
        canCollide: false
      }));
    }

    return newParticles;
  }

  createProjectileParticles({ centerX, centerY, destinationX, destinationY }) {
    const launchAngle = Math.atan2(destinationY - centerY, destinationX - centerX);
    const initialSpeed = 2.8;
    const maxSpeed = 5.25;
    const thrust = 0.16;
    const initialXVelocity = Math.cos(launchAngle) * initialSpeed;
    const initialYVelocity = Math.sin(launchAngle) * initialSpeed;
    const forwardXAcceleration = Math.cos(launchAngle) * thrust;
    const forwardYAcceleration = Math.sin(launchAngle) * thrust;

    return [new Particle({
      type: 'spark',
      shape: 'circle',
      lifetime: 512,
      color: '#ff5500',
      alpha: 1,
      radius: 16,
      mass: 0.15,
      x: centerX,
      y: centerY,
      originX: centerX,
      originY: centerY,
      destinationX,
      destinationY,
      xVelocity: initialXVelocity,
      yVelocity: initialYVelocity,
      maxXVelocity: Math.abs(Math.cos(launchAngle) * maxSpeed) + 0.2,
      maxYVelocity: Math.abs(Math.sin(launchAngle) * maxSpeed) + 0.2,
      xAcceleration: forwardXAcceleration,
      yAcceleration: forwardYAcceleration,
      drag: 0.001,
      canCollide: false
    })];
  }

  createFireworkBurstParticles({ centerX, centerY }) {
    const newParticles = [];
    const { startColor, endColor } = this.createDistinctColorPair();

    for (let i = 0; i < 100; i++) {
      const angle = Math.random() * Math.PI * 2;
      const speed = Math.random() * 6 + 2;
      newParticles.push(new Particle({
        type: 'spark',
        shape: 'circle',
        lifetime: 120,
        color: startColor,
        startColor,
        endColor,
        alpha: 1,
        radius: 3,
        mass: 1,
        x: centerX,
        y: centerY,
        xVelocity: Math.cos(angle) * speed,
        yVelocity: Math.sin(angle) * speed,
        yAcceleration: 0.05,
        drag: 0.02,
        canCollide: false
      }));
    }

    return newParticles;
  }

  createFireworkTailParticles({ centerX, width, height }) {
    const newParticles = [];
    const tailCount = 256;
    const staggerFrames = 60;
    const delayOrder = Array.from({ length: tailCount }, (_, index) => index);

    for (let i = delayOrder.length - 1; i > 0; i--) {
      const randomIndex = Math.floor(Math.random() * (i + 1));
      const temp = delayOrder[i];
      delayOrder[i] = delayOrder[randomIndex];
      delayOrder[randomIndex] = temp;
    }

    for (let i = 0; i < tailCount; i++) {
      const { startColor, endColor } = this.createDistinctColorPair();
      const xPosition = tailCount > 1 ? (i / (tailCount - 1)) * width : centerX;
      const randomDestinationY = (height * 0.35) + Math.random() * (height * 0.3);
      const radius = 8 + Math.random() * 8;
      const delay = Math.max(0, Math.round(delayOrder[i] * staggerFrames + (Math.random() - 0.5) * 20));

      newParticles.push(new Particle({
        type: 'fireworkTail',
        shape: 'circle',
        delay,
        lifetime: 260,
        color: startColor,
        startColor,
        endColor,
        alpha: 1,
        radius,
        mass: 0.25,
        x: xPosition,
        y: height,
        originX: xPosition,
        originY: height,
        destinationX: xPosition,
        destinationY: randomDestinationY,
        xVelocity: 0,
        yVelocity: -(Math.random() * 2 + 5),
        canCollide: false
      }));
    }

    return newParticles;
  }

  createItemReturnParticles({ centerX, centerY, destinationX, destinationY }) {
    const launchAngle = Math.atan2(destinationY - centerY, destinationX - centerX);
    const initialSpeed = 2.8;
    const maxSpeed = 5.25;
    const thrust = 0.16;
    const initialXVelocity = Math.cos(launchAngle) * initialSpeed;
    const initialYVelocity = Math.sin(launchAngle) * initialSpeed;
    const forwardXAcceleration = Math.cos(launchAngle) * thrust;
    const forwardYAcceleration = Math.sin(launchAngle) * thrust;

    return [new Particle({
      type: 'image',
      shape: 'circle',
      lifetime: 512,
      color: '#ffffff',
      alpha: 1,
      radius: 16,
      width: 32,
      height: 32,
      mass: 0.15,
      x: centerX,
      y: centerY,
      originX: centerX,
      originY: centerY,
      destinationX,
      destinationY,
      xVelocity: initialXVelocity,
      yVelocity: initialYVelocity,
      maxXVelocity: Math.abs(Math.cos(launchAngle) * maxSpeed) + 0.2,
      maxYVelocity: Math.abs(Math.sin(launchAngle) * maxSpeed) + 0.2,
      xAcceleration: forwardXAcceleration,
      yAcceleration: forwardYAcceleration,
      drag: 0.001,
      canCollide: false,
      imageData: this.particleImage
    })];
  }

  normalizeMasslessParticles(particles) {
    particles.forEach(p => {
      if (p.mass === 0) {
        p.mass = undefined;
        p.yAcceleration = 0;
        p.xAcceleration = 0;
        p.zAcceleration = 0;
      }
    });
  }
}
