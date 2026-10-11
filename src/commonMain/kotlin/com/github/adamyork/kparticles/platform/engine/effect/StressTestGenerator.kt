package com.github.adamyork.kparticles.platform.engine.effect

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.*
import kotlin.math.*
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class StressTestGenerator(
    private val platformInterop: PlatformInterop
) : BaseGenerator(ParticleEffect.STRESS_TEST) {

    private companion object {
        private const val STRESS_TEST_PARTICLE_COUNT_CPU: Int = 256
        private const val STRESS_TEST_PARTICLE_COUNT_GPU: Int = 2048
        private const val STRESS_TEST_COLOR_PALETTE_SIZE: Int = 50
        private val STRESS_TEST_ALPHA_OPTIONS = doubleArrayOf(0.25, 0.5, 1.0)
        private const val STRESS_TEST_ATTRACTION_PER_MASS: Double = 10.0
    }

    private var nextStressTestGpuSlot: Int = 0

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
            val destinationX = viewPort.x + Random.nextDouble() * viewPort.width
            val destinationY = viewPort.y + Random.nextDouble() * viewPort.height

            val width = 4.0 + Random.nextDouble() * 20.0
            val height = 4.0 + Random.nextDouble() * 20.0
            val radius = 2.0 + Random.nextDouble() * 14.0
            val growthRate = if (Random.nextBoolean()) 0.0 else -0.1 + Random.nextDouble() * 0.4

            val angle = Random.nextDouble() * PI * 2
            val speed = Random.nextDouble() * 6.0
            val xAcceleration = if (Random.nextBoolean()) 0.0 else -0.1 + Random.nextDouble() * 0.2
            val yAcceleration = if (Random.nextBoolean()) 0.0 else -0.1 + Random.nextDouble() * 0.2
            val zAcceleration = if (Random.nextBoolean()) 0.0 else -0.1 + Random.nextDouble() * 0.2

            val drag = if (Random.nextBoolean()) 0.0 else Random.nextDouble() * 0.05
            val mass = Random.nextDouble() * 2.0
            val attraction = mass * STRESS_TEST_ATTRACTION_PER_MASS
            val xForce = if (Random.nextBoolean()) 0.0 else -0.05 + Random.nextDouble() * 0.1
            val yForce = if (Random.nextBoolean()) 0.0 else -0.05 + Random.nextDouble() * 0.1
            val delay = if (Random.nextBoolean()) 0.0 else Random.nextDouble() * 60.0
            val particleColor = colorPalette[Random.nextInt(colorPalette.size)]
            val particleAlpha = STRESS_TEST_ALPHA_OPTIONS[Random.nextInt(STRESS_TEST_ALPHA_OPTIONS.size)]

            particles += Particle(
                id = Random.nextInt().toString(16),
                effect = ParticleEffect.STRESS_TEST,
                shape = if (Random.nextBoolean()) ParticleShape.CIRCLE else ParticleShape.RECT,
                age = 0.0,
                delay = delay,
                lifetime = 200.0 + Random.nextDouble() * 600.0,
                color = particleColor,
                startColor = particleColor,
                endColor = particleColor,
                alpha = particleAlpha,
                endAlpha = particleAlpha,
                initialAlpha = particleAlpha,
                alphaMultiplier = 0.0,
                width = width,
                height = height,
                maxWidth = width + Random.nextDouble() * 20.0,
                maxHeight = height + Random.nextDouble() * 20.0,
                radius = radius,
                maxRadius = radius + Random.nextDouble() * 10.0,
                growthRate = growthRate,
                x = spawnX,
                y = spawnY,
                z = 0.0,
                originX = spawnX.roundToInt(),
                originY = spawnY.roundToInt(),
                originZ = 0,
                destinationX = destinationX.roundToInt(),
                destinationY = destinationY.roundToInt(),
                destinationZ = 0,
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                zVelocity = -1.0 + Random.nextDouble() * 2.0,
                maxXVelocity = 4.0 + Random.nextDouble() * 8.0,
                maxYVelocity = 4.0 + Random.nextDouble() * 8.0,
                maxZVelocity = 4.0 + Random.nextDouble() * 8.0,
                xAcceleration = xAcceleration,
                yAcceleration = yAcceleration,
                zAcceleration = zAcceleration,
                drag = drag,
                mass = mass,
                restitution = Random.nextDouble(),
                attraction = attraction,
                canCollide = Random.nextBoolean(),
                visible = true,
                viewportBound = Random.nextBoolean(),
                xForce = xForce,
                yForce = yForce,
                gpuReservedSlot = nextStressTestGpuSlot
            )
            nextStressTestGpuSlot = (nextStressTestGpuSlot + 1) % MAX_ACTIVE_STRESS_TEST_PARTICLES
        }
        return particles
    }
}
