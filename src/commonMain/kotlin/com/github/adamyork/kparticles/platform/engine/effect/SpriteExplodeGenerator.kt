package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import me.tatarka.inject.annotations.Inject
import kotlin.math.*
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class SpriteExplodeGenerator(
    private val physicsSettingsService: PhysicsSettingsService
) : AbstractGenerator(ParticleEffect.SPRITE_EXPLODE) {

    private companion object {
        private const val ALPHA_SHIFT: Int = 24
        private const val RED_SHIFT: Int = 16
        private const val GREEN_SHIFT: Int = 8
        private const val OPAQUE_ALPHA: Double = 255.0
        private const val COLOR_CHANNEL_MASK: Int = 0xFF
        private const val MINIMUM_PIXEL_ALPHA: Double = 16.0
        private const val LAUNCH_DELAY: Double = 120.0
        private const val FLIGHT_LIFETIME: Double = 120.0
        private const val LAUNCH_STAGGER_TICKS: Double = 90.0
        private const val MINIMUM_SPEED_SCALE: Double = 0.4
        private const val MAXIMUM_SPEED_SCALE: Double = 1.8
        private const val MAXIMUM_ANGLE_JITTER: Double = 0.35
        private const val PLANE_SPACING_RATIO: Double = 1.5
        private const val LAUNCH_REACH: Double = 300.0
        private const val LAUNCH_REFERENCE_DISTANCE: Double = 24.0
        private const val LAUNCH_DRAG_LENGTHS: Double = 2.0
        private const val PIXEL_RESTITUTION: Double = 0.6
        private const val LAUNCH_SOFTENING: Double = 2.0
        private const val MINIMUM_DIRECTION_DISTANCE: Double = 0.000001
        private const val PIXEL_MAX_ACTIVE_PARTICLES: Int = 8192
    }

    private val pixelGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(PIXEL_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(pixelGpuSlotGroup)

    override val companionEffects: List<ParticleEffect> = listOf(ParticleEffect.SPRITE_EXPLODE_BACKDROP)

    private var pixelColors: IntArray = IntArray(0)

    private var pixelColumns: Int = 0

    private var pixelRows: Int = 0

    override fun populateItemImage(itemImage: ImageAsset) {
        val imageBitmap = itemImage.imageAndBytes.imageBitmap
        val columns = min(itemImage.width, imageBitmap.width)
        val rows = min(itemImage.height, imageBitmap.height)
        val sampledColors = IntArray(columns * rows)
        imageBitmap.readPixels(sampledColors, 0, 0, columns, rows, 0, columns)
        pixelColors = sampledColors
        pixelColumns = columns
        pixelRows = rows
    }

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val particles = ArrayList<Particle>()
        if (pixelColors.isEmpty()) return particles
        val stride = max(physicsSettingsService.spriteExplodePixelStride, 1)
        val pixelSize = stride.toDouble()
        val mediumDensity = physicsSettingsService.mediumDensity
        val gravity = physicsSettingsService.gravity
        val linearDrag = max(physicsSettingsService.drag, Double.MIN_VALUE)
        val dimensions = RectDimensions(width = pixelSize, height = pixelSize)
        val frontalLength = (dimensions.boundingWidth() + dimensions.boundingHeight()) / 2.0
        val targetDragRate = LAUNCH_DRAG_LENGTHS / LAUNCH_REACH
        val density = max(
            mediumDensity,
            0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength / (dimensions.area() * targetDragRate)
        )
        val quadraticDragRate = 0.5 * mediumDensity * physicsSettingsService.dragCoefficient * frontalLength /
            (density * dimensions.area())
        val reachDivisor = max(quadraticDragRate, Double.MIN_VALUE) * (1.0 - exp(-linearDrag * FLIGHT_LIFETIME))
        val referenceSpeed = linearDrag * expm1(quadraticDragRate * LAUNCH_REACH) / reachDivisor
        val softenedReferenceDistance = sqrt(
            LAUNCH_REFERENCE_DISTANCE * LAUNCH_REFERENCE_DISTANCE + LAUNCH_SOFTENING * LAUNCH_SOFTENING
        )
        val mass = density * dimensions.area()
        val holdThrustY = -mass * Particle.netGravityOf(density, gravity, mediumDensity)
        val planeSpacing = physicsSettingsService.collisionPlaneThickness * PLANE_SPACING_RATIO
        val originX = request.x - pixelColumns / 2.0
        val originY = request.y - pixelRows / 2.0
        for (row in 0 until pixelRows step stride) {
            for (column in 0 until pixelColumns step stride) {
                val blockWidth = min(stride, pixelColumns - column)
                val blockHeight = min(stride, pixelRows - row)
                var alphaSum = 0.0
                var redSum = 0.0
                var greenSum = 0.0
                var blueSum = 0.0
                for (sampleRow in row until row + blockHeight) {
                    for (sampleColumn in column until column + blockWidth) {
                        val argb = pixelColors[sampleRow * pixelColumns + sampleColumn]
                        val sampleAlpha = (argb ushr ALPHA_SHIFT and COLOR_CHANNEL_MASK).toDouble()
                        alphaSum += sampleAlpha
                        redSum += (argb ushr RED_SHIFT and COLOR_CHANNEL_MASK) * sampleAlpha
                        greenSum += (argb ushr GREEN_SHIFT and COLOR_CHANNEL_MASK) * sampleAlpha
                        blueSum += (argb and COLOR_CHANNEL_MASK) * sampleAlpha
                    }
                }
                val alpha = alphaSum / (blockWidth * blockHeight)
                if (alpha < MINIMUM_PIXEL_ALPHA) continue
                val positionX = originX + column + blockWidth / 2.0
                val positionY = originY + row + blockHeight / 2.0
                val deltaX = positionX - request.x
                val deltaY = positionY - request.y
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                val speed = referenceSpeed * sqrt(softenedReferenceDistance / sqrt(distance * distance + LAUNCH_SOFTENING * LAUNCH_SOFTENING))
                val baseAngle = if (distance > MINIMUM_DIRECTION_DISTANCE) atan2(deltaY, deltaX) else -PI / 2.0
                val launchAngle = baseAngle + Random.nextDouble(-MAXIMUM_ANGLE_JITTER, MAXIMUM_ANGLE_JITTER)
                val launchSpeed = speed * Random.nextDouble(MINIMUM_SPEED_SCALE, MAXIMUM_SPEED_SCALE)
                val holdTicks = Random.nextDouble(LAUNCH_STAGGER_TICKS)
                particles += Particle(
                    lifecycle = ParticleLifecycle(
                        delay = LAUNCH_DELAY,
                        lifetime = FLIGHT_LIFETIME + holdTicks,
                        pinnedUntil = LAUNCH_DELAY + holdTicks
                    ),
                    appearance = ParticleAppearance(
                        color = Color(
                            (redSum / alphaSum / OPAQUE_ALPHA).toFloat(),
                            (greenSum / alphaSum / OPAQUE_ALPHA).toFloat(),
                            (blueSum / alphaSum / OPAQUE_ALPHA).toFloat()
                        ),
                        alpha = alpha / OPAQUE_ALPHA
                    ),
                    dimensions = RectDimensions(width = blockWidth.toDouble(), height = blockHeight.toDouble()),
                    transform = ParticleTransform(
                        position = ParticleVector(positionX, positionY, particles.size * planeSpacing),
                        origin = ParticleVector(positionX, positionY)
                    ),
                    material = ParticleMaterial(density = density, restitution = PIXEL_RESTITUTION, exertsGravity = false),
                    dynamics = ParticleDynamics(
                        velocity = ParticleVector(cos(launchAngle) * launchSpeed, sin(launchAngle) * launchSpeed),
                        thrust = ParticleVector(0.0, holdThrustY)
                    ),
                    id = nextParticleId()
                )
            }
        }
        return particles
    }
}
