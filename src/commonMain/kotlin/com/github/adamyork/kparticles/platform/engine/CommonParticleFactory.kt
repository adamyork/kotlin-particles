package com.github.adamyork.kparticles.platform.engine

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.*
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Inject
class CommonParticleFactory(
    private val physicsSettingsService: PhysicsSettingsService,
    private val platformInterop: PlatformInterop
) : ParticleFactory {

    companion object {
        const val GPU_COMPUTE_FLOATS_PER_PARTICLE: Int = 16

        private const val DEFAULT_MAX_WIDTH: Double = 10.0
        private const val DEFAULT_MAX_HEIGHT: Double = 10.0
        private const val DEFAULT_MAX_RADIUS: Double = 5.0
        private const val DEFAULT_MAX_VELOCITY: Double = 10.0

        private const val GUIDED_LAUNCH_INITIAL_SPEED: Double = 2.8
        private const val GUIDED_LAUNCH_MAX_SPEED: Double = 5.25
        private const val GUIDED_LAUNCH_THRUST: Double = 0.16
        private const val GUIDED_LAUNCH_VELOCITY_HEADROOM: Double = 0.2

        private const val DUST_PUFF_COUNT: Int = 16
        private const val DUST_CLUSTER_RADIUS: Double = 12.0
        private const val DUST_CENTER_PUFF_RADIUS: Double = 9.0
        private const val DUST_PUFF_MIN_RADIUS: Double = 6.0
        private const val DUST_PUFF_RADIUS_RANGE: Double = 5.0
        private const val DUST_MIN_TRAVEL_DISTANCE: Double = 8.0
        private const val DUST_TRAVEL_DISTANCE_RANGE: Double = 4.0
        private const val DUST_MIN_SPEED: Double = 0.06
        private const val DUST_SPEED_RANGE: Double = 0.04
        private const val DUST_LIFETIME: Double = 128.0
        private const val DUST_INITIAL_ALPHA: Double = 0.85
        private const val DUST_END_ALPHA: Double = 0.0

        private const val COLLISION_PARTICLE_COUNT: Int = 256
        private const val COLLISION_EXPLOSION_ANGLE_SPAN: Double = PI
        private const val COLLISION_MIN_RADIUS: Double = 2.0
        private const val COLLISION_RADIUS_RANGE: Double = 2.5
        private const val COLLISION_MIN_SPEED: Double = 4.0
        private const val COLLISION_SPEED_RANGE: Double = 6.0
        private const val COLLISION_LIFETIME: Double = 240.0
        private const val COLLISION_INITIAL_ALPHA: Double = 0.95
        private const val COLLISION_END_ALPHA: Double = 1.0
        private const val COLLISION_MASS_SCALE: Double = 0.04
        private const val COLLISION_RESTITUTION: Double = 0.8
        private const val COLLISION_DRAG: Double = 0.01

        private const val PROJECTILE_RADIUS: Double = 16.0
        private const val PROJECTILE_LIFETIME: Double = 512.0
        private const val PROJECTILE_MASS: Double = 0.15
        private const val PROJECTILE_DRAG: Double = 0.001

        private const val FIREWORK_BURST_PARTICLE_COUNT: Int = 100
        private const val FIREWORK_BURST_MIN_SPEED: Double = 2.0
        private const val FIREWORK_BURST_SPEED_RANGE: Double = 6.0
        private const val FIREWORK_BURST_RADIUS: Double = 3.0
        private const val FIREWORK_BURST_LIFETIME: Double = 120.0
        private const val FIREWORK_BURST_MASS: Double = 0.02
        private const val FIREWORK_BURST_Y_ACCELERATION: Double = 0.05
        private const val FIREWORK_BURST_DRAG: Double = 0.02

        private const val FIREWORK_TAIL_PARTICLE_COUNT: Int = 256
        private const val FIREWORK_TAIL_MASS: Double = 0.02
        private const val FIREWORK_TAIL_DESTINATION_BAND_START_RATIO: Double = 0.15
        private const val FIREWORK_TAIL_DESTINATION_BAND_SIZE_RATIO: Double = 0.2
        private const val FIREWORK_TAIL_STAGGER_TIMING_RATIO: Double = 0.5
        private const val FIREWORK_TAIL_DELAY_JITTER_RANGE: Double = 20.0
        private const val FIREWORK_TAIL_MIN_RADIUS: Double = 8.0
        private const val FIREWORK_TAIL_RADIUS_RANGE: Double = 8.0
        private const val FIREWORK_TAIL_VELOCITY_HEADROOM: Double = 1.2

        private const val ITEM_RETURN_RADIUS: Double = 16.0
        private const val ITEM_RETURN_LIFETIME: Double = 512.0
        private const val ITEM_RETURN_MASS: Double = 0.15
        private const val ITEM_RETURN_DRAG: Double = 0.001

        private const val COLLIDING_BITS_PARTICLE_COUNT: Int = 100
        private const val COLLIDING_BITS_MIN_SIZE: Double = 8.0
        private const val COLLIDING_BITS_SIZE_RANGE: Double = 24.0
        private const val COLLIDING_BITS_MIN_LIFETIME: Double = 128.0
        private const val COLLIDING_BITS_LIFETIME_RANGE: Double = 384.0
        private const val COLLIDING_BITS_MIN_SPEED: Double = 2.0
        private const val COLLIDING_BITS_SPEED_RANGE: Double = 6.0
        private const val COLLIDING_BITS_MASS: Double = 0.12
        private const val COLLIDING_BITS_RESTITUTION: Double = 0.6

        private const val GOBBLER_PARTICLE_COUNT: Int = 16
        private const val GOBBLER_MIN_SIZE: Double = 8.0
        private const val GOBBLER_SIZE_RANGE: Double = 24.0
        private const val GOBBLER_LIFETIME: Double = 1024.0
        private const val GOBBLER_MIN_SPEED: Double = 0.8
        private const val GOBBLER_SPEED_RANGE: Double = 2.4
        private const val GOBBLER_RESTITUTION: Double = 1.0

        private const val GOBBLER_DRAG: Double = 0.002

        private const val GOBBLER_MAX_VELOCITY: Double = 6.0

        private const val GOBBLER_MASS_PER_RADIUS_SQUARED: Double = 1.0 / 20480.0

        private const val GOBBLER_ATTRACTION_PER_MASS: Double = 480.0

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

        private const val STRESS_TEST_PARTICLE_COUNT_CPU: Int = 256
        private const val STRESS_TEST_PARTICLE_COUNT_GPU: Int = 2048

        private const val STRESS_TEST_COLOR_PALETTE_SIZE: Int = 50

        private val STRESS_TEST_ALPHA_OPTIONS = doubleArrayOf(0.25, 0.5, 1.0)
        private const val STRESS_TEST_ATTRACTION_PER_MASS: Double = 10.0
        private const val MAX_ACTIVE_PROJECTILES: Int = 1
        private const val MAX_ACTIVE_MAP_ITEM_RETURN_PARTICLES: Int = 1
        private const val MAX_ACTIVE_COLLIDING_BITS_PARTICLES: Int = 512
        private const val MAX_ACTIVE_GOBBLER_PARTICLES: Int = 64
        private const val MAX_ACTIVE_BLACK_HOLE_CORE_PARTICLES: Int = 8
        private const val MAX_ACTIVE_BLACK_HOLE_EXPLOSION_PARTICLES: Int = 1024
        private const val MAX_ACTIVE_FIREWORK_BURST_PARTICLES: Int = 512
        private const val MAX_ACTIVE_FIREWORK_TAIL_PARTICLES: Int = 1024
        private const val MAX_ACTIVE_STRESS_TEST_PARTICLES: Int = 2048
    }

    private var colorMap: Map<ParticleType, Color> = emptyMap()
    private var nextCollidingBitsGpuSlot: Int = 0
    private var nextBlackHoleCoreGpuSlot: Int = 0
    private var nextBlackHoleExplosionGpuSlot: Int = 0
    private var nextGobblerGpuSlot: Int = 0
    private var nextStressTestGpuSlot: Int = 0
    private var nextFireworkBurstGpuSlot: Int = 0
    private var nextFireworkTailGpuSlot: Int = 0

    override fun populateColorMap(assetService: AssetService) {
        colorMap = mapOf(
            ParticleType.DUST to Color(
                assetService.appProperties.particle.player.movement.color.r.toFloat() / 255f,
                assetService.appProperties.particle.player.movement.color.g.toFloat() / 255f,
                assetService.appProperties.particle.player.movement.color.b.toFloat() / 255f,
                assetService.appProperties.particle.player.movement.color.a.toFloat() / 255f
            ),
            ParticleType.COLLISION to Color(
                assetService.appProperties.particle.player.collision.color.r.toFloat() / 255f,
                assetService.appProperties.particle.player.collision.color.g.toFloat() / 255f,
                assetService.appProperties.particle.player.collision.color.b.toFloat() / 255f,
                assetService.appProperties.particle.player.collision.color.a.toFloat() / 255f
            ),
            ParticleType.PROJECTILE to Color(
                assetService.appProperties.particle.enemy.projectile.color.r.toFloat() / 255f,
                assetService.appProperties.particle.enemy.projectile.color.g.toFloat() / 255f,
                assetService.appProperties.particle.enemy.projectile.color.b.toFloat() / 255f,
                assetService.appProperties.particle.enemy.projectile.color.a.toFloat() / 255f
            )
        )
    }

    override fun create(
        type: ParticleType,
        x: Double,
        y: Double,
        viewPort: ViewPort,
        destinationX: Double?,
        destinationY: Double?,
        direction: Direction
    ): MutableList<Particle> {
        val rightEdge = viewPort.x + viewPort.width.toDouble()
        val particles = when (type) {
            ParticleType.DUST -> createDustParticles(x, y)
            ParticleType.COLLISION -> createCollisionParticles(x, y, direction)
            ParticleType.PROJECTILE -> createProjectileParticles(x, y, destinationX ?: rightEdge, destinationY ?: 0.0)
            ParticleType.FIREWORK_BURST -> createFireworkBurstParticles(x, y)
            ParticleType.FIREWORK_TAIL -> createFireworkTailParticles(viewPort)
            ParticleType.ITEM_RETURN -> createItemReturnParticles(x, y, destinationX ?: rightEdge, destinationY ?: 0.0)
            ParticleType.COLLIDING_BITS -> createCollidingBitsParticles(viewPort)
            ParticleType.GOBBLER -> createGobblerParticles(viewPort)
            ParticleType.BLACK_HOLE -> createBlackHoleParticles(viewPort)
            ParticleType.STRESS_TEST -> createStressTestParticles(viewPort)
        }
        return particles
    }

    override fun createGpuParticleComputeBuffer(maxParticles: Int): FloatArray {
        return FloatArray(maxParticles * GPU_COMPUTE_FLOATS_PER_PARTICLE)
    }

    private fun createDustParticles(centerX: Double, centerY: Double): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        repeat(DUST_PUFF_COUNT) { puffIndex ->
            val angle = (puffIndex.toDouble() / DUST_PUFF_COUNT) * PI * 2
            val distanceFromCenter = Random.nextDouble() * (DUST_CLUSTER_RADIUS * 0.6) + (DUST_CLUSTER_RADIUS * 0.3)
            val offsetX = cos(angle) * distanceFromCenter
            val offsetY = sin(angle) * distanceFromCenter
            val radius = Random.nextDouble() * DUST_PUFF_RADIUS_RANGE + DUST_PUFF_MIN_RADIUS
            particles += createDustPuff(centerX + offsetX, centerY + offsetY, radius)
        }
        particles += createDustPuff(centerX, centerY, DUST_CENTER_PUFF_RADIUS)
        return particles
    }

    private fun createDustPuff(x: Double, y: Double, radius: Double): Particle {
        val destinationAngle = Random.nextDouble() * PI * 2
        val destinationDistance = DUST_MIN_TRAVEL_DISTANCE + Random.nextDouble() * DUST_TRAVEL_DISTANCE_RANGE
        val destinationX = x + cos(destinationAngle) * destinationDistance
        val destinationY = y + sin(destinationAngle) * destinationDistance
        val deltaX = destinationX - x
        val deltaY = destinationY - y
        val length = hypot(deltaX, deltaY).takeIf { it > 0 } ?: 1.0
        val speed = DUST_MIN_SPEED + Random.nextDouble() * DUST_SPEED_RANGE
        val dustPuffColor = colorMap[ParticleType.DUST] ?: Color.White
        val diameter = radius * 2.0
        return Particle(
            id = Random.nextInt().toString(16),
            type = ParticleType.DUST,
            shape = ParticleShape.CIRCLE,
            age = 0.0,
            delay = 0.0,
            lifetime = DUST_LIFETIME,
            color = dustPuffColor,
            startColor = dustPuffColor,
            endColor = dustPuffColor,
            alpha = DUST_INITIAL_ALPHA,
            endAlpha = DUST_END_ALPHA,
            initialAlpha = DUST_INITIAL_ALPHA,
            alphaMultiplier = 0.0,
            width = diameter,
            height = diameter,
            maxWidth = DEFAULT_MAX_WIDTH,
            maxHeight = DEFAULT_MAX_HEIGHT,
            radius = radius,
            maxRadius = DEFAULT_MAX_RADIUS,
            growthRate = 0.0,
            x = x,
            y = y,
            z = 0.0,
            originX = x.roundToInt(),
            originY = y.roundToInt(),
            originZ = 0,
            destinationX = destinationX.roundToInt(),
            destinationY = destinationY.roundToInt(),
            destinationZ = 0,
            xVelocity = (deltaX / length) * speed,
            yVelocity = (deltaY / length) * speed,
            zVelocity = 0.0,
            maxXVelocity = DEFAULT_MAX_VELOCITY,
            maxYVelocity = DEFAULT_MAX_VELOCITY,
            maxZVelocity = DEFAULT_MAX_VELOCITY,
            xAcceleration = 0.0,
            yAcceleration = 0.0,
            zAcceleration = 0.0,
            drag = 0.0,
            mass = 0.0,
            restitution = 0.0,
            attraction = 0.0,
            canCollide = false,
            visible = true,
            viewportBound = false
        )
    }

    private fun createCollisionParticles(
        centerX: Double,
        centerY: Double,
        direction: Direction
    ): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        val explodeAngle = when (direction) {
            Direction.RIGHT -> 0.0
            Direction.LEFT -> PI
        }
        val collisionParticleColor = colorMap[ParticleType.COLLISION] ?: Color.White
        val angleStep = COLLISION_EXPLOSION_ANGLE_SPAN / (COLLISION_PARTICLE_COUNT - 1)
        val startAngle = explodeAngle - COLLISION_EXPLOSION_ANGLE_SPAN / 2.0
        repeat(COLLISION_PARTICLE_COUNT) { particleIndex ->
            val radius = Random.nextDouble() * COLLISION_RADIUS_RANGE + COLLISION_MIN_RADIUS
            val diameter = radius * 2.0
            val velocityAngle = startAngle + angleStep * particleIndex
            val speed = Random.nextDouble() * COLLISION_SPEED_RANGE + COLLISION_MIN_SPEED
            particles += Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.COLLISION,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = COLLISION_LIFETIME,
                color = collisionParticleColor,
                startColor = collisionParticleColor,
                endColor = collisionParticleColor,
                alpha = COLLISION_INITIAL_ALPHA,
                endAlpha = COLLISION_END_ALPHA,
                initialAlpha = COLLISION_INITIAL_ALPHA,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = radius,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                originZ = 0,
                destinationX = 0,
                destinationY = 0,
                destinationZ = 0,
                xVelocity = cos(velocityAngle) * speed,
                yVelocity = sin(velocityAngle) * speed,
                zVelocity = 0.0,
                maxXVelocity = DEFAULT_MAX_VELOCITY,
                maxYVelocity = DEFAULT_MAX_VELOCITY,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = COLLISION_DRAG,
                mass = radius * COLLISION_MASS_SCALE,
                restitution = COLLISION_RESTITUTION,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false
            )
        }

        return particles
    }

    private fun createProjectileParticles(
        centerX: Double,
        centerY: Double,
        destinationX: Double,
        destinationY: Double
    ): MutableList<Particle> {
        val launchAngle = atan2(destinationY - centerY, destinationX - centerX)
        val projectileParticleColor = colorMap[ParticleType.PROJECTILE] ?: Color.White
        val diameter = PROJECTILE_RADIUS * 2.0
        return mutableListOf(
            Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.PROJECTILE,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = PROJECTILE_LIFETIME,
                color = projectileParticleColor,
                startColor = projectileParticleColor,
                endColor = projectileParticleColor,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = PROJECTILE_RADIUS,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                originZ = 0,
                destinationX = destinationX.roundToInt(),
                destinationY = destinationY.roundToInt(),
                destinationZ = 0,
                xVelocity = cos(launchAngle) * GUIDED_LAUNCH_INITIAL_SPEED,
                yVelocity = sin(launchAngle) * GUIDED_LAUNCH_INITIAL_SPEED,
                zVelocity = 0.0,
                maxXVelocity = abs(cos(launchAngle) * GUIDED_LAUNCH_MAX_SPEED) + GUIDED_LAUNCH_VELOCITY_HEADROOM,
                maxYVelocity = abs(sin(launchAngle) * GUIDED_LAUNCH_MAX_SPEED) + GUIDED_LAUNCH_VELOCITY_HEADROOM,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = cos(launchAngle) * GUIDED_LAUNCH_THRUST,
                yAcceleration = sin(launchAngle) * GUIDED_LAUNCH_THRUST,
                zAcceleration = 0.0,
                drag = PROJECTILE_DRAG,
                mass = PROJECTILE_MASS,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false
            )
        )
    }

    private fun createFireworkBurstParticles(centerX: Double, centerY: Double): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        val (startColor, endColor) = createDistinctColorPair()
        repeat(FIREWORK_BURST_PARTICLE_COUNT) {
            val angle = Random.nextDouble() * PI * 2
            val speed = Random.nextDouble() * FIREWORK_BURST_SPEED_RANGE + FIREWORK_BURST_MIN_SPEED
            val diameter = FIREWORK_BURST_RADIUS * 2.0
            particles += Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.FIREWORK_BURST,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = FIREWORK_BURST_LIFETIME,
                color = startColor,
                startColor = startColor,
                endColor = endColor,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = FIREWORK_BURST_RADIUS,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = 0,
                originY = 0,
                originZ = 0,
                destinationX = 0,
                destinationY = 0,
                destinationZ = 0,
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                zVelocity = 0.0,
                maxXVelocity = DEFAULT_MAX_VELOCITY,
                maxYVelocity = DEFAULT_MAX_VELOCITY,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = FIREWORK_BURST_Y_ACCELERATION,
                zAcceleration = 0.0,
                drag = FIREWORK_BURST_DRAG,
                mass = FIREWORK_BURST_MASS,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false,
                gpuReservedSlot = nextFireworkBurstGpuSlot
            )
            nextFireworkBurstGpuSlot = (nextFireworkBurstGpuSlot + 1) % MAX_ACTIVE_FIREWORK_BURST_PARTICLES
        }
        return particles
    }

    private fun createFireworkTailParticles(viewPort: ViewPort): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        val deceleration = physicsSettingsService.gravity * FIREWORK_TAIL_MASS
        val delayOrder = MutableList(FIREWORK_TAIL_PARTICLE_COUNT) { it }
        shuffle(delayOrder)
        val viewPortWidth = viewPort.width.toDouble()
        val viewPortHeight = viewPort.height.toDouble()
        val originY = viewPort.y + viewPortHeight
        val destinationBandStart = viewPortHeight * FIREWORK_TAIL_DESTINATION_BAND_START_RATIO
        val destinationBandSize = viewPortHeight * FIREWORK_TAIL_DESTINATION_BAND_SIZE_RATIO
        val averageDistance = originY - (viewPort.y + destinationBandStart + destinationBandSize / 2.0)
        val averageLaunchVelocity = sqrt(2 * deceleration * averageDistance)
        val averageTimeToApex = averageLaunchVelocity / deceleration
        val staggerFrames = averageTimeToApex * FIREWORK_TAIL_STAGGER_TIMING_RATIO
        repeat(FIREWORK_TAIL_PARTICLE_COUNT) { particleIndex ->
            val (startColor, endColor) = createDistinctColorPair()
            val xPosition = viewPort.x + (particleIndex.toDouble() / (FIREWORK_TAIL_PARTICLE_COUNT - 1)) * viewPortWidth
            val randomDestinationY = viewPort.y + destinationBandStart + Random.nextDouble() * destinationBandSize
            val distanceToDestination = originY - randomDestinationY
            val launchVelocity = sqrt(2 * deceleration * distanceToDestination)
            val timeToDestination = launchVelocity / deceleration
            val radius = FIREWORK_TAIL_MIN_RADIUS + Random.nextDouble() * FIREWORK_TAIL_RADIUS_RANGE
            val diameter = radius * 2.0
            val delay = max(
                0.0,
                round(delayOrder[particleIndex] * staggerFrames + (Random.nextDouble() - 0.5) * FIREWORK_TAIL_DELAY_JITTER_RANGE)
            )
            particles += Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.FIREWORK_TAIL,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = delay,
                lifetime = timeToDestination,
                color = startColor,
                startColor = startColor,
                endColor = endColor,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = radius,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = xPosition,
                y = originY,
                z = 0.0,
                originX = xPosition.roundToInt(),
                originY = originY.roundToInt(),
                originZ = 0,
                destinationX = xPosition.roundToInt(),
                destinationY = randomDestinationY.roundToInt(),
                destinationZ = 0,
                xVelocity = 0.0,
                yVelocity = -launchVelocity,
                zVelocity = 0.0,
                maxXVelocity = DEFAULT_MAX_VELOCITY,
                maxYVelocity = launchVelocity * FIREWORK_TAIL_VELOCITY_HEADROOM,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = 0.0,
                mass = FIREWORK_TAIL_MASS,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false,
                gpuReservedSlot = nextFireworkTailGpuSlot
            )
            nextFireworkTailGpuSlot = (nextFireworkTailGpuSlot + 1) % MAX_ACTIVE_FIREWORK_TAIL_PARTICLES
        }

        return particles
    }

    private fun createItemReturnParticles(
        centerX: Double,
        centerY: Double,
        destinationX: Double,
        destinationY: Double
    ): MutableList<Particle> {
        val launchAngle = atan2(destinationY - centerY, destinationX - centerX)
        val diameter = ITEM_RETURN_RADIUS * 2.0

        return mutableListOf(
            Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.ITEM_RETURN,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = ITEM_RETURN_LIFETIME,
                color = Color.White,
                startColor = Color.White,
                endColor = Color.White,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = diameter,
                height = diameter,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = ITEM_RETURN_RADIUS,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX.roundToInt(),
                originY = centerY.roundToInt(),
                originZ = 0,
                destinationX = destinationX.roundToInt(),
                destinationY = destinationY.roundToInt(),
                destinationZ = 0,
                xVelocity = cos(launchAngle) * GUIDED_LAUNCH_INITIAL_SPEED,
                yVelocity = sin(launchAngle) * GUIDED_LAUNCH_INITIAL_SPEED,
                zVelocity = 0.0,
                maxXVelocity = abs(cos(launchAngle) * GUIDED_LAUNCH_MAX_SPEED) + GUIDED_LAUNCH_VELOCITY_HEADROOM,
                maxYVelocity = abs(sin(launchAngle) * GUIDED_LAUNCH_MAX_SPEED) + GUIDED_LAUNCH_VELOCITY_HEADROOM,
                maxZVelocity = DEFAULT_MAX_VELOCITY,
                xAcceleration = cos(launchAngle) * GUIDED_LAUNCH_THRUST,
                yAcceleration = sin(launchAngle) * GUIDED_LAUNCH_THRUST,
                zAcceleration = 0.0,
                drag = ITEM_RETURN_DRAG,
                mass = ITEM_RETURN_MASS,
                restitution = 0.0,
                attraction = 0.0,
                canCollide = false,
                visible = true,
                viewportBound = false
            )
        )
    }

    private fun createCollidingBitsParticles(
        viewPort: ViewPort
    ): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        repeat(COLLIDING_BITS_PARTICLE_COUNT) {
            val isCircle = Random.nextBoolean()
            val angle = Random.nextDouble() * PI * 2
            val speed = COLLIDING_BITS_MIN_SPEED + Random.nextDouble() * COLLIDING_BITS_SPEED_RANGE
            val lifetime = COLLIDING_BITS_MIN_LIFETIME + Random.nextDouble() * COLLIDING_BITS_LIFETIME_RANGE
            val startX = viewPort.x + Random.nextDouble() * viewPort.width
            val startY = viewPort.y + Random.nextDouble() * viewPort.height
            val destinationX = viewPort.x + Random.nextDouble() * viewPort.width
            val destinationY = viewPort.y + Random.nextDouble() * viewPort.height
            val radius: Double
            val width: Double
            val height: Double
            if (isCircle) {
                radius = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE
                width = radius * 2.0
                height = width
            } else {
                radius = 0.0
                width = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE
                height = COLLIDING_BITS_MIN_SIZE + Random.nextDouble() * COLLIDING_BITS_SIZE_RANGE
            }
            particles += Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.COLLIDING_BITS,
                shape = if (isCircle) ParticleShape.CIRCLE else ParticleShape.RECT,
                age = 0.0,
                delay = 0.0,
                lifetime = lifetime,
                color = Color.White,
                startColor = Color.White,
                endColor = Color.White,
                alpha = 1.0,
                endAlpha = 1.0,
                initialAlpha = 1.0,
                alphaMultiplier = 0.0,
                width = width,
                height = height,
                maxWidth = DEFAULT_MAX_WIDTH,
                maxHeight = DEFAULT_MAX_HEIGHT,
                radius = radius,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = startX,
                y = startY,
                z = 0.0,
                originX = startX.roundToInt(),
                originY = startY.roundToInt(),
                originZ = 0,
                destinationX = destinationX.roundToInt(),
                destinationY = destinationY.roundToInt(),
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
                drag = 0.0,
                mass = COLLIDING_BITS_MASS,
                restitution = COLLIDING_BITS_RESTITUTION,
                attraction = 0.0,
                canCollide = true,
                visible = true,
                viewportBound = true,
                gpuReservedSlot = nextCollidingBitsGpuSlot
            )
            nextCollidingBitsGpuSlot = (nextCollidingBitsGpuSlot + 1) % MAX_ACTIVE_COLLIDING_BITS_PARTICLES
        }
        return particles
    }

    private fun createGobblerParticles(viewPort: ViewPort): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        repeat(GOBBLER_PARTICLE_COUNT) {
            val angle = Random.nextDouble() * PI * 2
            val speed = GOBBLER_MIN_SPEED + Random.nextDouble() * GOBBLER_SPEED_RANGE
            val startX = viewPort.x + Random.nextDouble() * viewPort.width
            val startY = viewPort.y + Random.nextDouble() * viewPort.height
            val destinationX = viewPort.x + Random.nextDouble() * viewPort.width
            val destinationY = viewPort.y + Random.nextDouble() * viewPort.height
            val radius = GOBBLER_MIN_SIZE + Random.nextDouble() * GOBBLER_SIZE_RANGE
            val diameter = radius * 2.0
            val mass = radius * radius * GOBBLER_MASS_PER_RADIUS_SQUARED
            val attraction = mass * GOBBLER_ATTRACTION_PER_MASS
            val color = randomColor()
            particles += Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.GOBBLER,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = GOBBLER_LIFETIME,
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
                radius = radius,
                maxRadius = DEFAULT_MAX_RADIUS,
                growthRate = 0.0,
                x = startX,
                y = startY,
                z = 0.0,
                originX = startX.roundToInt(),
                originY = startY.roundToInt(),
                originZ = 0,
                destinationX = destinationX.roundToInt(),
                destinationY = destinationY.roundToInt(),
                destinationZ = 0,
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                zVelocity = 0.0,
                maxXVelocity = GOBBLER_MAX_VELOCITY,
                maxYVelocity = GOBBLER_MAX_VELOCITY,
                maxZVelocity = GOBBLER_MAX_VELOCITY,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = GOBBLER_DRAG,
                mass = mass,
                restitution = GOBBLER_RESTITUTION,
                attraction = attraction,
                canCollide = true,
                visible = true,
                viewportBound = true,
                xForce = 0.0,
                yForce = -physicsSettingsService.gravity * min(1.0, mass),
                gpuReservedSlot = nextGobblerGpuSlot
            )
            nextGobblerGpuSlot = (nextGobblerGpuSlot + 1) % MAX_ACTIVE_GOBBLER_PARTICLES
        }
        return particles
    }

    private fun createBlackHoleParticles(viewPort: ViewPort): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        val centerX = viewPort.x + viewPort.width / 2.0
        val centerY = viewPort.y + viewPort.height / 2.0
        val coreAttraction = viewPort.width.toDouble()


        particles += Particle(
            id = Random.nextInt().toString(16),
            type = ParticleType.BLACK_HOLE,
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
                type = ParticleType.BLACK_HOLE,
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

    private fun createStressTestParticles(viewPort: ViewPort): MutableList<Particle> {
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
                type = ParticleType.STRESS_TEST,
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

    override fun writeGpuParticleSpawnBuffer(
        mapParticles: List<Particle>,
        targetBuffer: FloatArray,
        maxParticles: Int,
        startSlot: Int,
        previouslyWrittenSlots: List<Int>
    ): ParticleWriteResult {
        var activeCount = 0
        val clampedMaxParticles = maxParticles.coerceAtLeast(0)
        val floatsPerParticle = GPU_COMPUTE_FLOATS_PER_PARTICLE
        if (clampedMaxParticles == 0) {
            return ParticleWriteResult(activeCount = 0, dirtySlotRanges = emptyList(), writtenSlots = emptyList())
        }

        val dirtySlotFlags = BooleanArray(clampedMaxParticles)
        val writtenSlotFlags = BooleanArray(clampedMaxParticles)
        for (slotIndex in previouslyWrittenSlots) {
            if (slotIndex !in 0 until clampedMaxParticles) continue
            dirtySlotFlags[slotIndex] = true
            val baseIndex = slotIndex * floatsPerParticle
            var floatOffset = 0
            while (floatOffset < floatsPerParticle && (baseIndex + floatOffset) < targetBuffer.size) {
                targetBuffer[baseIndex + floatOffset] = 0f
                floatOffset++
            }
        }

        val reservedProjectileSlots = MAX_ACTIVE_PROJECTILES.coerceAtMost(clampedMaxParticles)
        val remainingAfterProjectile = (clampedMaxParticles - reservedProjectileSlots).coerceAtLeast(0)
        val reservedMapItemReturnSlots =
            MAX_ACTIVE_MAP_ITEM_RETURN_PARTICLES.coerceAtMost(remainingAfterProjectile)
        val remainingAfterMapItemReturn = (remainingAfterProjectile - reservedMapItemReturnSlots).coerceAtLeast(0)
        val reservedCollidingBitsSlots =
            MAX_ACTIVE_COLLIDING_BITS_PARTICLES.coerceAtMost(remainingAfterMapItemReturn)
        val remainingAfterCollidingBits = (remainingAfterMapItemReturn - reservedCollidingBitsSlots).coerceAtLeast(0)
        val reservedGobblerSlots = MAX_ACTIVE_GOBBLER_PARTICLES.coerceAtMost(remainingAfterCollidingBits)
        val remainingAfterGobbler = (remainingAfterCollidingBits - reservedGobblerSlots).coerceAtLeast(0)
        val reservedBlackHoleCoreSlots =
            MAX_ACTIVE_BLACK_HOLE_CORE_PARTICLES.coerceAtMost(remainingAfterGobbler)
        val remainingAfterBlackHoleCore = (remainingAfterGobbler - reservedBlackHoleCoreSlots).coerceAtLeast(0)
        val reservedBlackHoleExplosionSlots =
            MAX_ACTIVE_BLACK_HOLE_EXPLOSION_PARTICLES.coerceAtMost(remainingAfterBlackHoleCore)
        val remainingAfterBlackHoleExplosion =
            (remainingAfterBlackHoleCore - reservedBlackHoleExplosionSlots).coerceAtLeast(0)
        val reservedFireworkBurstSlots =
            MAX_ACTIVE_FIREWORK_BURST_PARTICLES.coerceAtMost(remainingAfterBlackHoleExplosion)
        val remainingAfterFireworkBurst = (remainingAfterBlackHoleExplosion - reservedFireworkBurstSlots).coerceAtLeast(0)
        val reservedFireworkTailSlots =
            MAX_ACTIVE_FIREWORK_TAIL_PARTICLES.coerceAtMost(remainingAfterFireworkBurst)
        val remainingAfterFireworkTail = (remainingAfterFireworkBurst - reservedFireworkTailSlots).coerceAtLeast(0)
        val reservedStressTestSlots = MAX_ACTIVE_STRESS_TEST_PARTICLES.coerceAtMost(remainingAfterFireworkTail)
        val firstMapItemReturnSlot = clampedMaxParticles - reservedMapItemReturnSlots
        val firstProjectileSlot = firstMapItemReturnSlot - reservedProjectileSlots
        val firstCollidingBitsSlot = firstProjectileSlot - reservedCollidingBitsSlots
        val firstGobblerSlot = firstCollidingBitsSlot - reservedGobblerSlots
        val firstBlackHoleCoreSlot = firstGobblerSlot - reservedBlackHoleCoreSlots
        val firstBlackHoleExplosionSlot = firstBlackHoleCoreSlot - reservedBlackHoleExplosionSlots
        val firstFireworkBurstSlot = firstBlackHoleExplosionSlot - reservedFireworkBurstSlots
        val firstFireworkTailSlot = firstFireworkBurstSlot - reservedFireworkTailSlots
        val firstStressTestSlot = firstFireworkTailSlot - reservedStressTestSlots
        val ringBufferCapacity = firstStressTestSlot.coerceAtLeast(0)

        var slot = if (ringBufferCapacity > 0) startSlot.mod(ringBufferCapacity) else 0
        for (particle in mapParticles) {
            if (activeCount >= clampedMaxParticles) break

            val isProjectile = particle.type == ParticleType.PROJECTILE
            val isMapItemReturn = particle.type == ParticleType.ITEM_RETURN
            val isFireworkTail = particle.type == ParticleType.FIREWORK_TAIL
            val isFireworkBurst = particle.type == ParticleType.FIREWORK_BURST
            val isCollidingBits = particle.type == ParticleType.COLLIDING_BITS
            val isGobbler = particle.type == ParticleType.GOBBLER
            val isBlackHoleCore = particle.type == ParticleType.BLACK_HOLE && particle.delay > 0.0
            val isBlackHoleExplosion = particle.type == ParticleType.BLACK_HOLE && particle.delay <= 0.0
            val isStressTest = particle.type == ParticleType.STRESS_TEST
            val slotIndex = if (isProjectile && reservedProjectileSlots > 0) {
                val projectileSlotOffset = 0
                firstProjectileSlot + projectileSlotOffset
            } else if (isMapItemReturn && reservedMapItemReturnSlots > 0) {
                val mapItemReturnSlotOffset = 0
                firstMapItemReturnSlot + mapItemReturnSlotOffset
            } else if (isCollidingBits && reservedCollidingBitsSlots > 0) {
                firstCollidingBitsSlot + particle.gpuReservedSlot.mod(reservedCollidingBitsSlots)
            } else if (isGobbler && reservedGobblerSlots > 0) {
                firstGobblerSlot + particle.gpuReservedSlot.mod(reservedGobblerSlots)
            } else if (isBlackHoleCore && reservedBlackHoleCoreSlots > 0) {
                firstBlackHoleCoreSlot + particle.gpuReservedSlot.mod(reservedBlackHoleCoreSlots)
            } else if (isBlackHoleExplosion && reservedBlackHoleExplosionSlots > 0) {
                firstBlackHoleExplosionSlot + particle.gpuReservedSlot.mod(reservedBlackHoleExplosionSlots)
            } else if (isFireworkBurst && reservedFireworkBurstSlots > 0) {
                firstFireworkBurstSlot + particle.gpuReservedSlot.mod(reservedFireworkBurstSlots)
            } else if (isFireworkTail && reservedFireworkTailSlots > 0) {
                firstFireworkTailSlot + particle.gpuReservedSlot.mod(reservedFireworkTailSlots)
            } else if (isStressTest && reservedStressTestSlots > 0) {
                firstStressTestSlot + particle.gpuReservedSlot.mod(reservedStressTestSlots)
            } else {
                slot
            }
            val baseIndex = slotIndex * floatsPerParticle
            if (baseIndex + (floatsPerParticle - 1) >= targetBuffer.size) {
                break
            }

            val isDust = particle.type == ParticleType.DUST
            val guidedLaunchDeltaX = (particle.destinationX - particle.originX).toFloat()
            val guidedLaunchDeltaY = (particle.destinationY - particle.originY).toFloat()
            val guidedLaunchLength =
                sqrt((guidedLaunchDeltaX * guidedLaunchDeltaX) + (guidedLaunchDeltaY * guidedLaunchDeltaY))
            val guidedLaunchUnitX = if (guidedLaunchLength > 0f) guidedLaunchDeltaX / guidedLaunchLength else 1f
            val guidedLaunchUnitY = if (guidedLaunchLength > 0f) guidedLaunchDeltaY / guidedLaunchLength else 0f
            val velocityX = when {
                isProjectile || isMapItemReturn -> guidedLaunchUnitX * GUIDED_LAUNCH_INITIAL_SPEED.toFloat()
                else -> particle.xVelocity.toFloat()
            }
            val velocityY = when {
                isProjectile || isMapItemReturn -> guidedLaunchUnitY * GUIDED_LAUNCH_INITIAL_SPEED.toFloat()
                else -> particle.yVelocity.toFloat()
            }
            val size = max(particle.width, particle.height).toFloat()
            val fireworkBurstPackedEndColor = if (isFireworkBurst) {
                val endColorRed = (particle.endColor.red * 255f).roundToInt().coerceIn(0, 255)
                val endColorGreen = (particle.endColor.green * 255f).roundToInt().coerceIn(0, 255)
                val endColorBlue = (particle.endColor.blue * 255f).roundToInt().coerceIn(0, 255)
                ((endColorRed * 65536) + (endColorGreen * 256) + endColorBlue).toFloat()
            } else {
                0f
            }
            val usesCenterAnchor = particle.shape == ParticleShape.CIRCLE
            val spawnX = if (usesCenterAnchor) particle.x.toFloat() + (size * 0.5f) else particle.x.toFloat()
            val spawnY = if (usesCenterAnchor) particle.y.toFloat() + (size * 0.5f) else particle.y.toFloat()
            var writeIndex = baseIndex
            targetBuffer[writeIndex++] = spawnX
            targetBuffer[writeIndex++] = spawnY
            targetBuffer[writeIndex++] = velocityX
            targetBuffer[writeIndex++] = velocityY
            targetBuffer[writeIndex++] = particle.age.toFloat().coerceAtLeast(0f)
            targetBuffer[writeIndex++] = particle.lifetime.toFloat().coerceAtLeast(1f)
            targetBuffer[writeIndex++] = size
            targetBuffer[writeIndex++] = 1f
            targetBuffer[writeIndex++] = particle.color.red.coerceIn(0f, 1f)
            targetBuffer[writeIndex++] = particle.color.green.coerceIn(0f, 1f)
            targetBuffer[writeIndex++] = particle.color.blue.coerceIn(0f, 1f)
            targetBuffer[writeIndex++] = particle.color.alpha.coerceIn(0f, 1f)
            val particleKind = when {
                particle.type == ParticleType.DUST -> 1f
                particle.type == ParticleType.PROJECTILE -> 2f
                particle.type == ParticleType.ITEM_RETURN -> 3f
                particle.type == ParticleType.FIREWORK_TAIL -> 4f
                particle.type == ParticleType.FIREWORK_BURST -> 5f
                particle.type == ParticleType.COLLISION -> 6f
                particle.type == ParticleType.COLLIDING_BITS -> 7f
                particle.type == ParticleType.GOBBLER -> 8f
                isBlackHoleCore -> 9f
                isBlackHoleExplosion -> 10f
                isStressTest -> 11f
                else -> 0f
            }
            targetBuffer[writeIndex++] = particleKind
            targetBuffer[writeIndex++] = if (particle.shape == ParticleShape.CIRCLE) 1f else 0f
            targetBuffer[writeIndex++] = when {
                isProjectile || isMapItemReturn -> guidedLaunchUnitX
                isBlackHoleCore -> particle.mass.toFloat()
                isFireworkBurst -> fireworkBurstPackedEndColor
                isFireworkTail -> particle.maxYVelocity.toFloat()
                isStressTest -> if (particle.canCollide) particle.mass.toFloat() else -particle.mass.toFloat()
                else -> 0f
            }
            targetBuffer[writeIndex++] = when {
                isDust -> particle.endAlpha.toFloat()
                isProjectile || isMapItemReturn -> guidedLaunchUnitY
                isBlackHoleCore -> particle.attraction.toFloat()
                isFireworkTail -> particle.delay.toFloat()
                isStressTest -> {
                    val viewportBoundBand = if (particle.viewportBound) 2f else 0f
                    particle.restitution.toFloat() + viewportBoundBand
                }
                else -> 0f
            }
            dirtySlotFlags[slotIndex] = true
            writtenSlotFlags[slotIndex] = true
            activeCount++
            if (!isProjectile && !isMapItemReturn && !isCollidingBits && !isGobbler &&
                !isBlackHoleCore && !isBlackHoleExplosion && !isFireworkBurst && !isFireworkTail &&
                !isStressTest && ringBufferCapacity > 0
            ) {
                slot = (slot + 1) % ringBufferCapacity
            }
        }

        val dirtySlotRanges = mutableListOf<IntRange>()
        var rangeStart = -1
        for (slotIndex in dirtySlotFlags.indices) {
            if (dirtySlotFlags[slotIndex]) {
                if (rangeStart == -1) {
                    rangeStart = slotIndex
                }
            } else if (rangeStart != -1) {
                dirtySlotRanges.add(rangeStart..<slotIndex)
                rangeStart = -1
            }
        }
        if (rangeStart != -1) {
            dirtySlotRanges.add(rangeStart..dirtySlotFlags.lastIndex)
        }

        val writtenSlots = mutableListOf<Int>()
        for (slotIndex in writtenSlotFlags.indices) {
            if (!writtenSlotFlags[slotIndex]) continue
            writtenSlots.add(slotIndex)
        }

        return ParticleWriteResult(
            activeCount = activeCount,
            dirtySlotRanges = dirtySlotRanges,
            writtenSlots = writtenSlots
        )
    }

    private fun createDistinctColorPair(): Pair<Color, Color> {
        val startColor = randomColor()
        var endColor = randomColor()
        while (endColor == startColor) {
            endColor = randomColor()
        }
        return startColor to endColor
    }

    private fun randomColor(): Color {
        return Color(
            red = Random.nextFloat(),
            green = Random.nextFloat(),
            blue = Random.nextFloat(),
            alpha = 1.0f
        )
    }

    private fun <T> shuffle(items: MutableList<T>) {
        for (currentIndex in items.lastIndex downTo 1) {
            val randomIndex = Random.nextInt(currentIndex + 1)
            val swappedItem = items[currentIndex]
            items[currentIndex] = items[randomIndex]
            items[randomIndex] = swappedItem
        }
    }
}
