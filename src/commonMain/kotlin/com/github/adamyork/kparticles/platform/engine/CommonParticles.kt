package com.github.adamyork.kparticles.platform.engine

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.Direction
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.data.ParticleShape
import com.github.adamyork.kparticles.platform.engine.data.ParticleType
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.*
import kotlin.random.Random

@Inject
class CommonParticles(
    private val physicsSettingsService: PhysicsSettingsService
) : Particles {

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
    }

    private var colorMap: Map<ParticleType, Color> = emptyMap()

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
            canCollide = false,
            isVisible = true
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
                canCollide = false,
                isVisible = true
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
                canCollide = false,
                isVisible = true
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
                canCollide = false,
                isVisible = true
            )
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
                canCollide = false,
                isVisible = true
            )
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
                canCollide = false,
                isVisible = true
            )
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
