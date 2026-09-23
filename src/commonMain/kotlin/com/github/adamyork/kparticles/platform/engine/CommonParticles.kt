package com.github.adamyork.kparticles.platform.engine

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.data.ParticleShape
import com.github.adamyork.kparticles.platform.engine.data.ParticleType
import com.github.adamyork.kparticles.platform.service.AssetService
import me.tatarka.inject.annotations.Inject
import kotlin.math.*
import kotlin.random.Random

@Inject
class CommonParticles : Particles {

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
        mode: String,
        x: Double,
        y: Double,
        destinationX: Double?,
        destinationY: Double?,
        direction: String?
    ): MutableList<Particle> {
        val width = (x * 2).coerceAtLeast(1.0)
        val height = (y * 2).coerceAtLeast(1.0)
        val particles = when (mode) {
            "dust" -> createDustParticles(x, y)
            "collision" -> createCollisionParticles(x, y, direction)
            "projectile" -> createProjectileParticles(x, y, destinationX ?: width, destinationY ?: 0.0)
            "fireworkBurst" -> createFireworkBurstParticles(x, y)
            "fireworkTails" -> createFireworkTailParticles(x, width, height)
            "itemReturn" -> createItemReturnParticles(x, y, destinationX ?: width, destinationY ?: 0.0)
            else -> mutableListOf()
        }
        return particles
    }

    private fun createDustParticles(centerX: Double, centerY: Double): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        val puffCount = 16
        val clusterRadius = 12.0
        repeat(puffCount) { j ->
            val angle = (j.toDouble() / puffCount) * PI * 2
            val dist = Random.nextDouble() * (clusterRadius * 0.6) + (clusterRadius * 0.3)
            val offsetX = cos(angle) * dist
            val offsetY = sin(angle) * dist
            val radius = Random.nextDouble() * 5 + 6
            particles += createDustPuff(centerX + offsetX, centerY + offsetY, radius)
        }
        particles += createDustPuff(centerX, centerY, 9.0)
        return particles
    }

    private fun createDustPuff(x: Double, y: Double, radius: Double): Particle {
        val destinationAngle = Random.nextDouble() * PI * 2
        val destinationDistance = 8 + Random.nextDouble() * 4
        val destinationX = x + cos(destinationAngle) * destinationDistance
        val destinationY = y + sin(destinationAngle) * destinationDistance
        val deltaX = destinationX - x
        val deltaY = destinationY - y
        val length = hypot(deltaX, deltaY).takeIf { it > 0 } ?: 1.0
        val speed = 0.06 + Random.nextDouble() * 0.04
        val dustPuffColor = colorMap[ParticleType.DUST] ?: Color.White
        return Particle(
            id = Random.nextInt().toString(16),
            type = ParticleType.DUST,
            shape = ParticleShape.CIRCLE,
            age = 0.0,
            delay = 0.0,
            lifetime = 128.0,
            color = dustPuffColor,
            startColor = dustPuffColor,
            endColor = dustPuffColor,
            alpha = 0.85,
            endAlpha = 0.0,
            width = 0.0,
            height = 0.0,
            maxWidth = 10.0,
            maxHeight = 10.0,
            radius = radius,
            maxRadius = 5.0,
            growthRate = 0.0,
            x = x,
            y = y,
            z = 0.0,
            originX = x,
            originY = y,
            originZ = 0.0,
            destinationX = destinationX,
            destinationY = destinationY,
            destinationZ = 0.0,
            xVelocity = (deltaX / length) * speed,
            yVelocity = (deltaY / length) * speed,
            zVelocity = 0.0,
            maxXVelocity = 10.0,
            maxYVelocity = 10.0,
            maxZVelocity = 10.0,
            xAcceleration = 0.0,
            yAcceleration = 0.0,
            zAcceleration = 0.0,
            drag = 0.0,
            mass = null,
            restitution = 0.0,
            canCollide = false,
            isVisible = true,
            initialAlpha = null
        )
    }

    private fun createCollisionParticles(centerX: Double, centerY: Double, direction: String?): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        val collisionDirection = if (direction == "right") "right" else "left"
        val explodeAngle = if (collisionDirection == "left") 0.0 else PI
        val collisionParticleColor = colorMap[ParticleType.COLLISION] ?: Color.White
        repeat(256) {
            val radius = Random.nextDouble() * 2.5 + 2
            val spread = (Random.nextDouble() - 0.5) * 1.2
            val velocityAngle = explodeAngle + spread
            val speed = Random.nextDouble() * 6 + 4
            particles += Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.COLLISION,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = 240.0,
                color = collisionParticleColor,
                startColor = collisionParticleColor,
                endColor = collisionParticleColor,
                alpha = 0.95,
                endAlpha = 1.0,
                width = 0.0,
                height = 0.0,
                maxWidth = 10.0,
                maxHeight = 10.0,
                radius = radius,
                maxRadius = 5.0,
                growthRate = 0.0,
                mass = radius,
                restitution = 0.8,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX,
                originY = centerY,
                originZ = 0.0,
                destinationX = 0.0,
                destinationY = 0.0,
                destinationZ = 0.0,
                xVelocity = cos(velocityAngle) * speed,
                yVelocity = sin(velocityAngle) * speed,
                zVelocity = 0.0,
                maxXVelocity = 10.0,
                maxYVelocity = 10.0,
                maxZVelocity = 10.0,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = 0.01,
                canCollide = false,
                isVisible = true,
                initialAlpha = null
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
        val initialSpeed = 2.8
        val maxSpeed = 5.25
        val thrust = 0.16
        val projectileParticleColor = colorMap[ParticleType.PROJECTILE] ?: Color.White
        return mutableListOf(
            Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.PROJECTILE,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = 512.0,
                color = projectileParticleColor,
                startColor = projectileParticleColor,
                endColor = projectileParticleColor,
                alpha = 1.0,
                endAlpha = 1.0,
                width = 0.0,
                height = 0.0,
                maxWidth = 10.0,
                maxHeight = 10.0,
                radius = 16.0,
                maxRadius = 5.0,
                growthRate = 0.0,
                mass = 0.15,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX,
                originY = centerY,
                originZ = 0.0,
                destinationX = destinationX,
                destinationY = destinationY,
                destinationZ = 0.0,
                xVelocity = cos(launchAngle) * initialSpeed,
                yVelocity = sin(launchAngle) * initialSpeed,
                zVelocity = 0.0,
                maxXVelocity = abs(cos(launchAngle) * maxSpeed) + 0.2,
                maxYVelocity = abs(sin(launchAngle) * maxSpeed) + 0.2,
                maxZVelocity = 10.0,
                xAcceleration = cos(launchAngle) * thrust,
                yAcceleration = sin(launchAngle) * thrust,
                zAcceleration = 0.0,
                drag = 0.001,
                restitution = 0.0,
                canCollide = false,
                isVisible = true,
                initialAlpha = null
            )
        )
    }

    private fun createFireworkBurstParticles(centerX: Double, centerY: Double): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        val (startColor, endColor) = createDistinctColorPair()
        repeat(100) {
            val angle = Random.nextDouble() * PI * 2
            val speed = Random.nextDouble() * 6 + 2
            particles += Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.FIREWORK_BURST,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = 120.0,
                color = startColor,
                startColor = startColor,
                endColor = endColor,
                alpha = 1.0,
                endAlpha = 1.0,
                width = 0.0,
                height = 0.0,
                maxWidth = 10.0,
                maxHeight = 10.0,
                radius = 3.0,
                maxRadius = 5.0,
                growthRate = 0.0,
                mass = 1.0,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = 0.0,
                originY = 0.0,
                originZ = 0.0,
                destinationX = 0.0,
                destinationY = 0.0,
                destinationZ = 0.0,
                xVelocity = cos(angle) * speed,
                yVelocity = sin(angle) * speed,
                zVelocity = 0.0,
                maxXVelocity = 10.0,
                maxYVelocity = 10.0,
                maxZVelocity = 10.0,
                xAcceleration = 0.0,
                yAcceleration = 0.05,
                zAcceleration = 0.0,
                drag = 0.02,
                restitution = 0.0,
                canCollide = false,
                isVisible = true,
                initialAlpha = null
            )
        }
        return particles
    }

    private fun createFireworkTailParticles(centerX: Double, width: Double, height: Double): MutableList<Particle> {
        val particles = mutableListOf<Particle>()
        val tailCount = 256
        val staggerFrames = 60.0
        val delayOrder = MutableList(tailCount) { it }
        shuffle(delayOrder)
        repeat(tailCount) { i ->
            val (startColor, endColor) = createDistinctColorPair()
            val xPosition = if (tailCount > 1) (i.toDouble() / (tailCount - 1)) * width else centerX
            val randomDestinationY = (height * 0.35) + Random.nextDouble() * (height * 0.3)
            val radius = 8 + Random.nextDouble() * 8
            val delay = max(0.0, round(delayOrder[i] * staggerFrames + (Random.nextDouble() - 0.5) * 20))
            particles += Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.FIREWORK_TAIL,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = delay,
                lifetime = 260.0,
                color = startColor,
                startColor = startColor,
                endColor = endColor,
                alpha = 1.0,
                endAlpha = 1.0,
                width = 0.0,
                height = 0.0,
                maxWidth = 10.0,
                maxHeight = 10.0,
                radius = radius,
                maxRadius = 5.0,
                growthRate = 0.0,
                mass = 0.25,
                x = xPosition,
                y = height,
                z = 0.0,
                originX = xPosition,
                originY = height,
                originZ = 0.0,
                destinationX = xPosition,
                destinationY = randomDestinationY,
                destinationZ = 0.0,
                xVelocity = 0.0,
                yVelocity = -(Random.nextDouble() * 2 + 5),
                zVelocity = 0.0,
                maxXVelocity = 10.0,
                maxYVelocity = 10.0,
                maxZVelocity = 10.0,
                xAcceleration = 0.0,
                yAcceleration = 0.0,
                zAcceleration = 0.0,
                drag = 0.0,
                restitution = 0.0,
                canCollide = false,
                isVisible = true,
                initialAlpha = null
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
        val initialSpeed = 2.8
        val maxSpeed = 5.25
        val thrust = 0.16

        return mutableListOf(
            Particle(
                id = Random.nextInt().toString(16),
                type = ParticleType.ITEM_RETURN,
                shape = ParticleShape.CIRCLE,
                age = 0.0,
                delay = 0.0,
                lifetime = 512.0,
                color = Color.White,
                startColor = Color.White,
                endColor = Color.White,
                alpha = 1.0,
                endAlpha = 1.0,
                radius = 16.0,
                width = 32.0,
                height = 32.0,
                maxWidth = 10.0,
                maxHeight = 10.0,
                maxRadius = 5.0,
                growthRate = 0.0,
                mass = 0.15,
                x = centerX,
                y = centerY,
                z = 0.0,
                originX = centerX,
                originY = centerY,
                originZ = 0.0,
                destinationX = destinationX,
                destinationY = destinationY,
                destinationZ = 0.0,
                xVelocity = cos(launchAngle) * initialSpeed,
                yVelocity = sin(launchAngle) * initialSpeed,
                zVelocity = 0.0,
                maxXVelocity = abs(cos(launchAngle) * maxSpeed) + 0.2,
                maxYVelocity = abs(sin(launchAngle) * maxSpeed) + 0.2,
                maxZVelocity = 10.0,
                xAcceleration = cos(launchAngle) * thrust,
                yAcceleration = sin(launchAngle) * thrust,
                zAcceleration = 0.0,
                drag = 0.001,
                restitution = 0.0,
                canCollide = false,
                isVisible = true,
                initialAlpha = null
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
        for (i in items.lastIndex downTo 1) {
            val randomIndex = Random.nextInt(i + 1)
            val temp = items[i]
            items[i] = items[randomIndex]
            items[randomIndex] = temp
        }
    }
}
