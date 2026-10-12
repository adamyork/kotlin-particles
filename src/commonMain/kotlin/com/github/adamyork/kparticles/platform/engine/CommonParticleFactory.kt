package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
import com.github.adamyork.kparticles.platform.service.AssetService
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Inject
class CommonParticleFactory(
    effectGenerators: Set<ParticleEffectGenerator>
) : ParticleFactory {

    private class GpuSlotReservation(val firstSlot: Int, val slotCount: Int)

    private class GpuSlotLayout(
        val maxParticles: Int,
        val reservations: Map<GpuSlotGroup, GpuSlotReservation>,
        val ringBufferCapacity: Int
    )

    private val effectGeneratorsByEffect: Map<ParticleEffect, ParticleEffectGenerator> =
        effectGenerators.associateBy { it.effect }

    private val gpuSlotGroups: List<GpuSlotGroup> = effectGenerators.flatMap { it.gpuSlotGroups }.distinct()

    private var cachedGpuSlotLayout: GpuSlotLayout? = null

    override fun populateColorMap(assetService: AssetService) {
        for (effectGenerator in effectGeneratorsByEffect.values) {
            effectGenerator.populateColor(assetService)
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
        val effectGenerator = effectGeneratorsByEffect[type]
            ?: throw EngineException("No particle effect generator registered for $type")
        return effectGenerator.create(request)
    }

    override fun gpuSlotRange(effect: ParticleEffect, maxParticles: Int): IntRange {
        val slotGroup = effectGeneratorsByEffect[effect]?.gpuSlotGroups?.firstOrNull() ?: return IntRange.EMPTY
        val reservation = getOrCreateGpuSlotLayout(maxParticles.coerceAtLeast(0)).reservations[slotGroup]
            ?: return IntRange.EMPTY
        return reservation.firstSlot until (reservation.firstSlot + reservation.slotCount)
    }

    override fun createGpuParticleComputeBuffer(maxParticles: Int): FloatArray {
        return FloatArray(maxParticles * ParticleEffectGenerator.GPU_FLOATS_PER_PARTICLE)
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
        val floatsPerParticle = ParticleEffectGenerator.GPU_FLOATS_PER_PARTICLE
        if (clampedMaxParticles == 0) {
            return ParticleWriteResult(activeCount = 0, dirtySlotRanges = emptyList(), writtenSlots = emptyList())
        }
        val dirtySlotFlags = BooleanArray(clampedMaxParticles)
        val writtenSlotFlags = BooleanArray(clampedMaxParticles)
        for (slotIndex in previouslyWrittenSlots) {
            if (slotIndex !in 0 until clampedMaxParticles) continue
            dirtySlotFlags[slotIndex] = true
            val baseIndex = slotIndex * floatsPerParticle
            targetBuffer.fill(0f, baseIndex, (baseIndex + floatsPerParticle).coerceAtMost(targetBuffer.size))
        }
        val slotLayout = getOrCreateGpuSlotLayout(clampedMaxParticles)
        val ringBufferCapacity = slotLayout.ringBufferCapacity
        var slot = if (ringBufferCapacity > 0) startSlot.mod(ringBufferCapacity) else 0
        for (particle in mapParticles) {
            if (activeCount >= clampedMaxParticles) break
            val effectGenerator = effectGeneratorsByEffect[particle.effect] ?: continue
            if (!effectGenerator.shouldWriteGpuParticle(particle)) continue
            val slotGroup = effectGenerator.gpuSlotGroup(particle)
            val reservation = if (slotGroup != null) slotLayout.reservations[slotGroup] else null
            val slotIndex = if (reservation != null && reservation.slotCount > 0) {
                reservation.firstSlot + particle.gpuReservedSlot.mod(reservation.slotCount)
            } else {
                slot
            }
            val baseIndex = slotIndex * floatsPerParticle
            if (baseIndex + (floatsPerParticle - 1) >= targetBuffer.size) {
                break
            }
            effectGenerator.writeGpuParticle(particle, targetBuffer, baseIndex)
            dirtySlotFlags[slotIndex] = true
            writtenSlotFlags[slotIndex] = true
            activeCount++
            if (slotGroup == null && ringBufferCapacity > 0) {
                slot = (slot + 1) % ringBufferCapacity
            }
        }
        return ParticleWriteResult(
            activeCount = activeCount,
            dirtySlotRanges = collectSlotRanges(dirtySlotFlags),
            writtenSlots = collectSlots(writtenSlotFlags)
        )
    }

    private fun getOrCreateGpuSlotLayout(maxParticles: Int): GpuSlotLayout {
        val cachedLayout = cachedGpuSlotLayout
        if (cachedLayout != null && cachedLayout.maxParticles == maxParticles) {
            return cachedLayout
        }
        val reservations = HashMap<GpuSlotGroup, GpuSlotReservation>()
        var remainingSlots = maxParticles
        for (slotGroup in gpuSlotGroups) {
            val reservedSlots = slotGroup.capacity.coerceAtMost(remainingSlots).coerceAtLeast(0)
            remainingSlots -= reservedSlots
            reservations[slotGroup] = GpuSlotReservation(firstSlot = remainingSlots, slotCount = reservedSlots)
        }
        val slotLayout = GpuSlotLayout(maxParticles, reservations, remainingSlots)
        cachedGpuSlotLayout = slotLayout
        return slotLayout
    }

    private fun collectSlotRanges(slotFlags: BooleanArray): List<IntRange> {
        val slotRanges = mutableListOf<IntRange>()
        var rangeStart = -1
        for (slotIndex in slotFlags.indices) {
            if (slotFlags[slotIndex]) {
                if (rangeStart == -1) {
                    rangeStart = slotIndex
                }
            } else if (rangeStart != -1) {
                slotRanges.add(rangeStart..<slotIndex)
                rangeStart = -1
            }
        }
        if (rangeStart != -1) {
            slotRanges.add(rangeStart..slotFlags.lastIndex)
        }
        return slotRanges
    }

    private fun collectSlots(slotFlags: BooleanArray): List<Int> {
        val slots = mutableListOf<Int>()
        for (slotIndex in slotFlags.indices) {
            if (slotFlags[slotIndex]) {
                slots.add(slotIndex)
            }
        }
        return slots
    }
}
