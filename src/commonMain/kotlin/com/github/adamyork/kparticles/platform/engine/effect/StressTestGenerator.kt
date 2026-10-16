package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class StressTestGenerator(
    private val platformInterop: PlatformInterop,
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.STRESS_TEST) {

    private companion object {
        private const val STRESS_TEST_PARTICLE_COUNT_CPU: Int = 256
        private const val STRESS_TEST_PARTICLE_COUNT_GPU: Int = 2048
        private const val STRESS_TEST_COLOR_PALETTE_SIZE: Int = 50
        private val STRESS_TEST_ALPHA_OPTIONS = doubleArrayOf(0.25, 0.5, 1.0)
        private const val STRESS_TEST_MAX_ACTIVE_PARTICLES: Int = 2048
        private const val STRESS_TEST_GPU_KIND: Float = 11f
        private const val STRESS_TEST_WALL_BAND: Float = 2f
        private const val STRESS_TEST_SPINNER_CHANCE: Double = 0.25
        private const val STRESS_TEST_MIN_SPIN: Double = 0.02
        private const val STRESS_TEST_SPIN_RANGE: Double = 0.06
        private const val STRESS_TEST_SPINNER_FRICTION: Double = 0.5
        private const val STRESS_TEST_SPINNER_STATIC_FRICTION: Double = 0.7
        private const val STRESS_TEST_SPINNER_ROLLING_RESISTANCE: Double = 0.03
        private const val STRESS_TEST_HARD_CHANCE: Double = 0.15
        private const val STRESS_TEST_SOFT_CHANCE: Double = 0.15
        private const val STRESS_TEST_HARD_HARDNESS: Int = 2
        private const val STRESS_TEST_SOFT_HARDNESS: Int = 1
    }

    private val stressTestGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(STRESS_TEST_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(stressTestGpuSlotGroup)

    override val companionEffects: List<ParticleEffect> = listOf(
        ParticleEffect.DUST,
        ParticleEffect.COLLISION,
        ParticleEffect.PROJECTILE,
        ParticleEffect.BLOB_PROJECTILE,
        ParticleEffect.FIREWORK_BURST,
        ParticleEffect.FIREWORK_TAIL,
        ParticleEffect.ITEM_RETURN,
        ParticleEffect.ANIMATED_ITEM_RETURN,
        ParticleEffect.BUBBLE,
        ParticleEffect.COLLIDING_BITS,
        ParticleEffect.SHATTER,
        ParticleEffect.DRIPS,
        ParticleEffect.SPINNERS
    )

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val colorPalette = List(STRESS_TEST_COLOR_PALETTE_SIZE) { randomColor() }
        val stressTestParticleCount = if (platformInterop.isGpuEngineSupported()) {
            STRESS_TEST_PARTICLE_COUNT_GPU
        } else {
            STRESS_TEST_PARTICLE_COUNT_CPU
        }
        repeat(stressTestParticleCount) {
            val spawnX = viewPort.x + Random.nextDouble() * viewPort.width
            val spawnY = viewPort.y + Random.nextDouble() * viewPort.height
            val width = 4.0 + Random.nextDouble() * 20.0
            val height = 4.0 + Random.nextDouble() * 20.0
            val radius = 2.0 + Random.nextDouble() * 14.0
            val isCircle = Random.nextBoolean()
            val scaleRate = if (Random.nextBoolean()) 0.0 else -0.01 + Random.nextDouble() * 0.04
            val angle = Random.nextDouble() * PI * 2
            val speed = Random.nextDouble() * 6.0
            val xAcceleration = if (Random.nextBoolean()) 0.0 else -0.1 + Random.nextDouble() * 0.2
            val yAcceleration = if (Random.nextBoolean()) 0.0 else -0.1 + Random.nextDouble() * 0.2
            val zAcceleration = if (Random.nextBoolean()) 0.0 else -0.1 + Random.nextDouble() * 0.2
            val netGravityFraction = min(1.0, Random.nextDouble() * 2.0)
            val xExtraAcceleration = if (Random.nextBoolean()) 0.0 else -0.05 + Random.nextDouble() * 0.1
            val yExtraAcceleration = if (Random.nextBoolean()) 0.0 else -0.05 + Random.nextDouble() * 0.1
            val delay = if (Random.nextBoolean()) 0.0 else Random.nextDouble() * 60.0
            val particleColor = colorPalette[Random.nextInt(colorPalette.size)]
            val particleAlpha = STRESS_TEST_ALPHA_OPTIONS[Random.nextInt(STRESS_TEST_ALPHA_OPTIONS.size)]
            val dimensions = if (isCircle) {
                CircleDimensions(
                    radius = radius,
                    maxRadius = radius + Random.nextDouble() * 10.0,
                    scaleRate = scaleRate
                )
            } else {
                RectDimensions(
                    width = width,
                    height = height,
                    maxWidth = width + Random.nextDouble() * 20.0,
                    maxHeight = height + Random.nextDouble() * 20.0,
                    scaleRate = scaleRate
                )
            }
            val density = Particle.densityForNetGravity(
                physicsSettingsService.gravity * netGravityFraction,
                physicsSettingsService.gravity,
                physicsSettingsService.mediumDensity
            )
            val mass = density * dimensions.area()
            val isSpinner = !isCircle && Random.nextDouble() < STRESS_TEST_SPINNER_CHANCE
            val spinDirection = if (Random.nextBoolean()) 1.0 else -1.0
            val angularVelocity = if (isSpinner) {
                spinDirection * (STRESS_TEST_MIN_SPIN + Random.nextDouble() * STRESS_TEST_SPIN_RANGE)
            } else {
                0.0
            }
            val hardnessRoll = Random.nextDouble()
            val hardness = when {
                hardnessRoll < STRESS_TEST_HARD_CHANCE -> STRESS_TEST_HARD_HARDNESS
                hardnessRoll < STRESS_TEST_HARD_CHANCE + STRESS_TEST_SOFT_CHANCE -> STRESS_TEST_SOFT_HARDNESS
                else -> 0
            }
            val material = if (isSpinner) {
                ParticleMaterial(
                    density = density,
                    restitution = Random.nextDouble(),
                    hardness = hardness,
                    friction = STRESS_TEST_SPINNER_FRICTION,
                    staticFriction = STRESS_TEST_SPINNER_STATIC_FRICTION,
                    rollingResistance = STRESS_TEST_SPINNER_ROLLING_RESISTANCE
                )
            } else {
                ParticleMaterial(density = density, restitution = Random.nextDouble(), hardness = hardness)
            }
            particles += Particle(
                lifecycle = ParticleLifecycle(delay = delay, lifetime = 200.0 + Random.nextDouble() * 600.0),
                appearance = ParticleAppearance(color = particleColor, alpha = particleAlpha),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(spawnX, spawnY, if (Random.nextBoolean()) Particle.BOUNDED_Z else 0.0),
                    origin = ParticleVector(spawnX, spawnY),
                    angle = if (isSpinner) Random.nextDouble() * PI * 2 else 0.0
                ),
                material = material,
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed, -1.0 + Random.nextDouble() * 2.0),
                    thrust = ParticleVector(
                        (xAcceleration + xExtraAcceleration) * mass,
                        (yAcceleration + yExtraAcceleration) * mass,
                        zAcceleration * mass
                    ),
                    angularVelocity = angularVelocity
                ),
                id = nextParticleId()
            )
        }
        return particles
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = stressTestGpuSlotGroup

    override fun gpuKind(particle: Particle): Float = STRESS_TEST_GPU_KIND

    override fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {
        val isWithinBoundary = particle.transform.position.z > physicsSettingsService.boundaryZ &&
                particle.transform.position.z <= physicsSettingsService.boundaryZ + physicsSettingsService.boundaryDepth
        val wallBand = if (isWithinBoundary) STRESS_TEST_WALL_BAND else 0f
        targetBuffer[parameterIndex] = particle.mass().toFloat()
        targetBuffer[parameterIndex + 1] = particle.material.restitution.toFloat() + wallBand
        targetBuffer[parameterIndex + 2] = (particle.dynamics.thrust.x / particle.inertialMass()).toFloat()
        targetBuffer[parameterIndex + 3] = (particle.dynamics.thrust.y / particle.inertialMass()).toFloat()
        targetBuffer[parameterIndex + 4] = physicsSettingsService.drag.toFloat()
        targetBuffer[parameterIndex + 5] = physicsSettingsService.maxParticleSpeed.toFloat()
        targetBuffer[parameterIndex + 6] = particle.lifecycle.delay.toFloat()
    }
}
