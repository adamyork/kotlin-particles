package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
        private const val STRESS_TEST_MIN_NET_GRAVITY_FRACTION: Double = 0.5
        private const val STRESS_TEST_NET_GRAVITY_FRACTION_RANGE: Double = 0.45
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
        ParticleEffect.ITEM_RETURN,
        ParticleEffect.ANIMATED_ITEM_RETURN,
        ParticleEffect.BUBBLE,
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
            val spawnX = viewPort.x + random.nextDouble() * viewPort.width
            val spawnY = viewPort.y + random.nextDouble() * viewPort.height
            val width = 4.0 + random.nextDouble() * 20.0
            val height = 4.0 + random.nextDouble() * 20.0
            val radius = 2.0 + random.nextDouble() * 14.0
            val isCircle = random.nextBoolean()
            val scaleRate = if (random.nextBoolean()) 0.0 else -0.01 + random.nextDouble() * 0.04
            val angle = random.nextDouble() * PI * 2
            val speed = random.nextDouble() * 6.0
            val xAcceleration = if (random.nextBoolean()) 0.0 else -0.1 + random.nextDouble() * 0.2
            val yAcceleration = if (random.nextBoolean()) 0.0 else -0.1 + random.nextDouble() * 0.2
            val zAcceleration = if (random.nextBoolean()) 0.0 else -0.1 + random.nextDouble() * 0.2
            val netGravityFraction = STRESS_TEST_MIN_NET_GRAVITY_FRACTION + random.nextDouble() * STRESS_TEST_NET_GRAVITY_FRACTION_RANGE
            val xExtraAcceleration = if (random.nextBoolean()) 0.0 else -0.05 + random.nextDouble() * 0.1
            val yExtraAcceleration = if (random.nextBoolean()) 0.0 else -0.05 + random.nextDouble() * 0.1
            val delay = if (random.nextBoolean()) 0.0 else random.nextDouble() * 60.0
            val particleColor = colorPalette[random.nextInt(colorPalette.size)]
            val particleAlpha = STRESS_TEST_ALPHA_OPTIONS[random.nextInt(STRESS_TEST_ALPHA_OPTIONS.size)]
            val dimensions = if (isCircle) {
                CircleDimensions(
                    radius = radius,
                    maxRadius = radius + random.nextDouble() * 10.0,
                    scaleRate = scaleRate
                )
            } else {
                RectDimensions(
                    width = width,
                    height = height,
                    maxWidth = width + random.nextDouble() * 20.0,
                    maxHeight = height + random.nextDouble() * 20.0,
                    scaleRate = scaleRate
                )
            }
            val density = Particle.densityForNetGravity(
                physicsSettingsService.gravity * netGravityFraction,
                physicsSettingsService.gravity,
                physicsSettingsService.mediumDensity
            )
            val mass = density * dimensions.area()
            val isSpinner = !isCircle && random.nextDouble() < STRESS_TEST_SPINNER_CHANCE
            val spinDirection = if (random.nextBoolean()) 1.0 else -1.0
            val angularVelocity = if (isSpinner) {
                spinDirection * (STRESS_TEST_MIN_SPIN + random.nextDouble() * STRESS_TEST_SPIN_RANGE)
            } else {
                0.0
            }
            val hardnessRoll = random.nextDouble()
            val hardness = when {
                hardnessRoll < STRESS_TEST_HARD_CHANCE -> STRESS_TEST_HARD_HARDNESS
                hardnessRoll < STRESS_TEST_HARD_CHANCE + STRESS_TEST_SOFT_CHANCE -> STRESS_TEST_SOFT_HARDNESS
                else -> 0
            }
            val material = if (isSpinner) {
                ParticleMaterial(
                    density = density,
                    restitution = random.nextDouble(),
                    hardness = hardness,
                    friction = STRESS_TEST_SPINNER_FRICTION,
                    staticFriction = STRESS_TEST_SPINNER_STATIC_FRICTION,
                    rollingResistance = STRESS_TEST_SPINNER_ROLLING_RESISTANCE
                )
            } else {
                ParticleMaterial(density = density, restitution = random.nextDouble(), hardness = hardness)
            }
            particles += Particle(
                lifecycle = ParticleLifecycle(delay = delay, lifetime = 200.0 + random.nextDouble() * 600.0),
                appearance = ParticleAppearance(color = particleColor, alpha = particleAlpha),
                dimensions = dimensions,
                transform = ParticleTransform(
                    position = ParticleVector(spawnX, spawnY, if (random.nextBoolean()) Particle.BOUNDED_Z else 0.0),
                    origin = ParticleVector(spawnX, spawnY),
                    angle = if (isSpinner) random.nextDouble() * PI * 2 else 0.0
                ),
                material = material,
                dynamics = ParticleDynamics(
                    velocity = ParticleVector(cos(angle) * speed, sin(angle) * speed, -1.0 + random.nextDouble() * 2.0),
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
}
