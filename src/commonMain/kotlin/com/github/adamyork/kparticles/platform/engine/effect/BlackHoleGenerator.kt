package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import kotlin.math.*
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class BlackHoleGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : BaseGenerator(ParticleEffect.BLACK_HOLE) {

    private companion object {
        private const val BLACK_HOLE_CORE_RADIUS: Double = 5.0
        private const val BLACK_HOLE_CORE_DELAY: Double = 60.0
        private const val BLACK_HOLE_CORE_LIFETIME: Double = 2048.0
        private const val BLACK_HOLE_CORE_MASS: Double = 100.0
        private const val BLACK_HOLE_EXPLOSION_PARTICLE_COUNT: Int = 256
        private const val BLACK_HOLE_EXPLOSION_RADIUS: Double = 2.0
        private const val BLACK_HOLE_EXPLOSION_MIN_SPEED: Double = 1.5
        private const val BLACK_HOLE_EXPLOSION_SPEED_RANGE: Double = 4.5
        private const val BLACK_HOLE_EXPLOSION_LIFETIME: Double = BLACK_HOLE_CORE_LIFETIME
        private const val BLACK_HOLE_EXPLOSION_MASS: Double = 0.05
        private const val BLACK_HOLE_EXPLOSION_DRAG: Double = 0.02
        private const val BLACK_HOLE_EXPLOSION_ATTRACTION: Double = 1.0
    }

    private var nextBlackHoleCoreGpuSlot: Int = 0
    private var nextBlackHoleExplosionGpuSlot: Int = 0

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val viewPort = request.viewPort
        val particles = mutableListOf<Particle>()
        val centerX = viewPort.x + viewPort.width / 2.0
        val centerY = viewPort.y + viewPort.height / 2.0
        val coreAttraction = viewPort.width.toDouble()


        particles += Particle(
            id = Random.nextInt().toString(16),
            effect = ParticleEffect.BLACK_HOLE,
            shape = ParticleShape.CIRCLE,
            age = 0.0,
            delay = BLACK_HOLE_CORE_DELAY,
            lifetime = BLACK_HOLE_CORE_LIFETIME,
            color = Color.Black,
            startColor = Color.Black,
            endColor = Color.Black,
            alpha = 1.0,
            endAlpha = 1.0,
            initialAlpha = 1.0,
            alphaMultiplier = 0.0,
            width = BLACK_HOLE_CORE_RADIUS * 2.0,
            height = BLACK_HOLE_CORE_RADIUS * 2.0,
            maxWidth = DEFAULT_MAX_WIDTH,
            maxHeight = DEFAULT_MAX_HEIGHT,
            radius = BLACK_HOLE_CORE_RADIUS,
            maxRadius = DEFAULT_MAX_RADIUS,
            growthRate = 0.0,
            x = centerX,
            y = centerY,
            z = 0.0,
            originX = centerX.roundToInt(),
            originY = centerY.roundToInt(),
            originZ = 0,
            destinationX = centerX.roundToInt(),
            destinationY = centerY.roundToInt(),
            destinationZ = 0,
            xVelocity = 0.0,
            yVelocity = 0.0,
            zVelocity = 0.0,
            maxXVelocity = DEFAULT_MAX_VELOCITY,
            maxYVelocity = DEFAULT_MAX_VELOCITY,
            maxZVelocity = DEFAULT_MAX_VELOCITY,
            xAcceleration = 0.0,
            yAcceleration = 0.0,
            zAcceleration = 0.0,
            drag = 0.0,
            mass = BLACK_HOLE_CORE_MASS,
            restitution = 0.0,
            attraction = coreAttraction,
            canCollide = true,
            visible = true,
            viewportBound = true,
            xForce = 0.0,
            yForce = -physicsSettingsService.gravity * min(1.0, BLACK_HOLE_CORE_MASS),
            gpuReservedSlot = nextBlackHoleCoreGpuSlot
        )
        nextBlackHoleCoreGpuSlot = (nextBlackHoleCoreGpuSlot + 1) % MAX_ACTIVE_BLACK_HOLE_CORE_PARTICLES

        repeat(BLACK_HOLE_EXPLOSION_PARTICLE_COUNT) {
            val angle = Random.nextDouble() * PI * 2
            val speed = BLACK_HOLE_EXPLOSION_MIN_SPEED + Random.nextDouble() * BLACK_HOLE_EXPLOSION_SPEED_RANGE
            val diameter = BLACK_HOLE_EXPLOSION_RADIUS * 2.0
            val color = randomColor()
            particles += Particle(
                id = Random.nextInt().toString(16),
                effect = ParticleEffect.BLACK_HOLE,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = BLACK_HOLE_EXPLOSION_LIFETIME,
                color = color,
                startColor = color,
                endColor = color,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = BLACK_HOLE_EXPLOSION_RADIUS,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                originZ = 0,
                destinationX = centerX.roundToInt(),
                destinationY = centerY.roundToInt(),
                destinationZ = 0,
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                zVelocity = 0.0,
                maxXVelocity = DEFAULT_MAX_VELOCITY,
                maxYVelocity = DEFAULT_MAX_VELOCITY,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = BLACK_HOLE_EXPLOSION_DRAG,
                mass = BLACK_HOLE_EXPLOSION_MASS,
                restitution = 0.0,
                attraction = BLACK_HOLE_EXPLOSION_ATTRACTION,
                canCollide = false,
                visible = true,
                viewportBound = false,
                xForce = 0.0,
                yForce = -physicsSettingsService.gravity * min(1.0, BLACK_HOLE_EXPLOSION_MASS),
                gpuReservedSlot = nextBlackHoleExplosionGpuSlot
            )
            nextBlackHoleExplosionGpuSlot =
                (nextBlackHoleExplosionGpuSlot + 1) % MAX_ACTIVE_BLACK_HOLE_EXPLOSION_PARTICLES
        }
        return particles
    }
}
