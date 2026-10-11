package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.engine.effect.BaseGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
import com.github.adamyork.kparticles.platform.service.AssetService
import kotlin.math.*
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Inject
class CommonParticleFactory(
    effectFactories: Set<ParticleEffectGenerator>
) : ParticleFactory {

    companion object {
        const val GPU_COMPUTE_FLOATS_PER_PARTICLE: Int = 24
        const val PROJECTILE_BLOB_LOBE_COUNT: Int = 7
        const val PROJECTILE_BLOB_AMPLITUDE_RATIO: Float = 0.22f
        const val PROJECTILE_BLOB_BASE_FREQUENCY: Float = 0.05f
        const val PROJECTILE_BLOB_FREQUENCY_STEP: Float = 0.015f
        const val PROJECTILE_BLOB_PHASE_STEP: Float = 1.1f
        const val PROJECTILE_BLOB_SPLINE_TENSION: Float = 6f
    }

    private val effectFactoriesByEffect: Map<ParticleEffect, ParticleEffectGenerator> =
        effectFactories.associateBy { it.effect }

    override fun populateColorMap(assetService: AssetService) {
        for (effectFactory in effectFactoriesByEffect.values) {
            effectFactory.populateColor(assetService)
        }
    }

    override fun create(
        type: ParticleEffect,
        x: Double,
        y: Double,
        viewPort: ViewPort,
        destinationX: Double?,
        destinationY: Double?,
        direction: Direction
    ): MutableList<Particle> {
        val rightEdge = viewPort.x + viewPort.width.toDouble()
        val request = ParticleEffectRequest(x, y, viewPort, destinationX ?: rightEdge, destinationY ?: 0.0, direction)
        val effectFactory = effectFactoriesByEffect[type]
            ?: throw EngineException("No particle effect factory registered for $type")
        return effectFactory.create(request)
    }

    override fun createGpuParticleComputeBuffer(maxParticles: Int): FloatArray {
        return FloatArray(maxParticles * GPU_COMPUTE_FLOATS_PER_PARTICLE)
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

        val reservedProjectileSlots = BaseGenerator.MAX_ACTIVE_PROJECTILES.coerceAtMost(clampedMaxParticles)
        val remainingAfterProjectile = (clampedMaxParticles - reservedProjectileSlots).coerceAtLeast(0)
        val reservedMapItemReturnSlots =
            BaseGenerator.MAX_ACTIVE_MAP_ITEM_RETURN_PARTICLES.coerceAtMost(remainingAfterProjectile)
        val remainingAfterMapItemReturn = (remainingAfterProjectile - reservedMapItemReturnSlots).coerceAtLeast(0)
        val reservedCollidingBitsSlots =
            BaseGenerator.MAX_ACTIVE_COLLIDING_BITS_PARTICLES.coerceAtMost(remainingAfterMapItemReturn)
        val remainingAfterCollidingBits = (remainingAfterMapItemReturn - reservedCollidingBitsSlots).coerceAtLeast(0)
        val reservedGobblerSlots = BaseGenerator.MAX_ACTIVE_GOBBLER_PARTICLES.coerceAtMost(remainingAfterCollidingBits)
        val remainingAfterGobbler = (remainingAfterCollidingBits - reservedGobblerSlots).coerceAtLeast(0)
        val reservedBlackHoleCoreSlots =
            BaseGenerator.MAX_ACTIVE_BLACK_HOLE_CORE_PARTICLES.coerceAtMost(remainingAfterGobbler)
        val remainingAfterBlackHoleCore = (remainingAfterGobbler - reservedBlackHoleCoreSlots).coerceAtLeast(0)
        val reservedBlackHoleExplosionSlots =
            BaseGenerator.MAX_ACTIVE_BLACK_HOLE_EXPLOSION_PARTICLES.coerceAtMost(remainingAfterBlackHoleCore)
        val remainingAfterBlackHoleExplosion =
            (remainingAfterBlackHoleCore - reservedBlackHoleExplosionSlots).coerceAtLeast(0)
        val reservedFireworkBurstSlots =
            BaseGenerator.MAX_ACTIVE_FIREWORK_BURST_PARTICLES.coerceAtMost(remainingAfterBlackHoleExplosion)
        val remainingAfterFireworkBurst = (remainingAfterBlackHoleExplosion - reservedFireworkBurstSlots).coerceAtLeast(0)
        val reservedFireworkTailSlots =
            BaseGenerator.MAX_ACTIVE_FIREWORK_TAIL_PARTICLES.coerceAtMost(remainingAfterFireworkBurst)
        val remainingAfterFireworkTail = (remainingAfterFireworkBurst - reservedFireworkTailSlots).coerceAtLeast(0)
        val reservedStressTestSlots = BaseGenerator.MAX_ACTIVE_STRESS_TEST_PARTICLES.coerceAtMost(remainingAfterFireworkTail)
        val remainingAfterStressTest = (remainingAfterFireworkTail - reservedStressTestSlots).coerceAtLeast(0)
        val reservedBubbleSlots = BaseGenerator.MAX_ACTIVE_BUBBLE_PARTICLES.coerceAtMost(remainingAfterStressTest)
        val firstMapItemReturnSlot = clampedMaxParticles - reservedMapItemReturnSlots
        val firstProjectileSlot = firstMapItemReturnSlot - reservedProjectileSlots
        val firstCollidingBitsSlot = firstProjectileSlot - reservedCollidingBitsSlots
        val firstGobblerSlot = firstCollidingBitsSlot - reservedGobblerSlots
        val firstBlackHoleCoreSlot = firstGobblerSlot - reservedBlackHoleCoreSlots
        val firstBlackHoleExplosionSlot = firstBlackHoleCoreSlot - reservedBlackHoleExplosionSlots
        val firstFireworkBurstSlot = firstBlackHoleExplosionSlot - reservedFireworkBurstSlots
        val firstFireworkTailSlot = firstFireworkBurstSlot - reservedFireworkTailSlots
        val firstStressTestSlot = firstFireworkTailSlot - reservedStressTestSlots
        val firstBubbleSlot = firstStressTestSlot - reservedBubbleSlots
        val ringBufferCapacity = firstBubbleSlot.coerceAtLeast(0)

        var slot = if (ringBufferCapacity > 0) startSlot.mod(ringBufferCapacity) else 0
        @Suppress("DestructuringDeclaration")
        for (particle in mapParticles) {
            if (activeCount >= clampedMaxParticles) break

            val isProjectile = particle.effect == ParticleEffect.PROJECTILE
            val isBlobProjectile = particle.effect == ParticleEffect.BLOB_PROJECTILE
            val isAnyProjectile = isProjectile || isBlobProjectile
            val isAnimatedItemReturn = particle.effect == ParticleEffect.ANIMATED_ITEM_RETURN
            val isMapItemReturn = particle.effect == ParticleEffect.ITEM_RETURN || isAnimatedItemReturn
            val isFireworkTail = particle.effect == ParticleEffect.FIREWORK_TAIL
            val isFireworkBurst = particle.effect == ParticleEffect.FIREWORK_BURST
            val isCollidingBits = particle.effect == ParticleEffect.COLLIDING_BITS
            val isGobbler = particle.effect == ParticleEffect.GOBBLER
            val isBlackHoleCore = particle.effect == ParticleEffect.BLACK_HOLE && particle.delay > 0.0
            val isBlackHoleExplosion = particle.effect == ParticleEffect.BLACK_HOLE && particle.delay <= 0.0
            val isStressTest = particle.effect == ParticleEffect.STRESS_TEST
            val isBubble = particle.effect == ParticleEffect.BUBBLE
            if (isBubble && !particle.visible) {
                continue
            }
            val slotIndex = if (isAnyProjectile && reservedProjectileSlots > 0) {
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
            } else if (isBubble && reservedBubbleSlots > 0) {
                firstBubbleSlot + particle.gpuReservedSlot.mod(reservedBubbleSlots)
            } else {
                slot
            }
            val baseIndex = slotIndex * floatsPerParticle
            if (baseIndex + (floatsPerParticle - 1) >= targetBuffer.size) {
                break
            }

            val isDust = particle.effect == ParticleEffect.DUST
            val isCollision = particle.effect == ParticleEffect.COLLISION
            val guidedLaunchDeltaX = (particle.destinationX - particle.originX).toFloat()
            val guidedLaunchDeltaY = (particle.destinationY - particle.originY).toFloat()
            val guidedLaunchLength =
                sqrt((guidedLaunchDeltaX * guidedLaunchDeltaX) + (guidedLaunchDeltaY * guidedLaunchDeltaY))
            val guidedLaunchUnitX = if (guidedLaunchLength > 0f) guidedLaunchDeltaX / guidedLaunchLength else 1f
            val guidedLaunchUnitY = if (guidedLaunchLength > 0f) guidedLaunchDeltaY / guidedLaunchLength else 0f
            val velocityX = when {
                isAnyProjectile || isMapItemReturn -> guidedLaunchUnitX * BaseGenerator.GUIDED_LAUNCH_INITIAL_SPEED.toFloat()
                else -> particle.xVelocity.toFloat()
            }
            val velocityY = when {
                isAnyProjectile || isMapItemReturn -> guidedLaunchUnitY * BaseGenerator.GUIDED_LAUNCH_INITIAL_SPEED.toFloat()
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
                particle.effect == ParticleEffect.DUST -> 1f
                particle.effect == ParticleEffect.PROJECTILE -> 2f
                particle.effect == ParticleEffect.BLOB_PROJECTILE -> 12f
                particle.effect == ParticleEffect.ITEM_RETURN || particle.effect == ParticleEffect.ANIMATED_ITEM_RETURN -> 3f
                particle.effect == ParticleEffect.FIREWORK_TAIL -> 4f
                particle.effect == ParticleEffect.FIREWORK_BURST -> 5f
                particle.effect == ParticleEffect.COLLISION -> 6f
                particle.effect == ParticleEffect.COLLIDING_BITS -> 7f
                particle.effect == ParticleEffect.GOBBLER -> 8f
                isBlackHoleCore -> 9f
                isBlackHoleExplosion -> 10f
                isStressTest -> 11f
                particle.effect == ParticleEffect.BUBBLE -> 13f
                else -> 0f
            }
            targetBuffer[writeIndex++] = particleKind
            targetBuffer[writeIndex++] = if (particle.shape == ParticleShape.CIRCLE) 1f else 0f
            targetBuffer[writeIndex++] = when {
                isAnyProjectile || isMapItemReturn -> guidedLaunchUnitX
                isBlackHoleCore -> particle.mass.toFloat()
                isFireworkBurst -> fireworkBurstPackedEndColor
                isFireworkTail -> particle.maxYVelocity.toFloat()
                isStressTest -> if (particle.canCollide) particle.mass.toFloat() else -particle.mass.toFloat()
                else -> 0f
            }
            targetBuffer[writeIndex++] = when {
                isDust || isCollision -> particle.endAlpha.toFloat()
                isAnyProjectile || isMapItemReturn -> guidedLaunchUnitY
                isBlackHoleCore -> particle.attraction.toFloat()
                isFireworkTail -> particle.delay.toFloat()
                isStressTest -> {
                    val viewportBoundBand = if (particle.viewportBound) 2f else 0f
                    particle.restitution.toFloat() + viewportBoundBand
                }
                else -> 0f
            }
            targetBuffer[writeIndex++] = when {
                isStressTest -> (particle.xAcceleration + particle.xForce).toFloat()
                isGobbler || isBlackHoleExplosion -> particle.mass.toFloat()
                isMapItemReturn -> particle.maxFrames.toFloat()
                else -> 0f
            }
            targetBuffer[writeIndex++] = when {
                isStressTest -> (particle.yAcceleration + particle.yForce).toFloat()
                isGobbler || isBlackHoleExplosion -> particle.attraction.toFloat()
                else -> 0f
            }
            targetBuffer[writeIndex++] = if (isStressTest) particle.drag.toFloat() else 0f
            targetBuffer[writeIndex++] = if (isStressTest) particle.maxXVelocity.toFloat() else 0f
            targetBuffer[writeIndex++] = if (isStressTest) particle.delay.toFloat() else 0f
            targetBuffer[writeIndex++] = 0f
            targetBuffer[writeIndex++] = 0f
            targetBuffer[writeIndex] = 0f
            dirtySlotFlags[slotIndex] = true
            writtenSlotFlags[slotIndex] = true
            activeCount++
            if (!isAnyProjectile && !isMapItemReturn && !isCollidingBits && !isGobbler &&
                !isBlackHoleCore && !isBlackHoleExplosion && !isFireworkBurst && !isFireworkTail &&
                !isStressTest && !isBubble && ringBufferCapacity > 0
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
}
