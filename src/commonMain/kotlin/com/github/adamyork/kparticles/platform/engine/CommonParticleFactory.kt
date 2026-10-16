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

    private val effectGeneratorList: List<ParticleEffectGenerator> = effectGenerators.toList()

    private val effectGeneratorsByEffect: Map<ParticleEffect, ParticleEffectGenerator> =
        effectGenerators.associateBy { it.effect }

    private val gpuSlotGroups: List<GpuSlotGroup> = effectGenerators.flatMap { it.gpuSlotGroups }.distinct()

    private var cachedGpuSlotLayout: GpuSlotLayout? = null

    override fun generators(): List<ParticleEffectGenerator> = effectGeneratorList

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
    ) {
        val rightEdge = viewPort.x + viewPort.width.toDouble()
        val request = ParticleEffectRequest(x, y, viewPort, destinationX ?: rightEdge, destinationY ?: 0.0, direction)
        val effectGenerator = effectGeneratorsByEffect[type]
            ?: throw EngineException("No particle effect generator registered for $type")
        effectGenerator.spawn(request)
        for (companionEffect in effectGenerator.companionEffects) {
            effectGeneratorsByEffect[companionEffect]?.spawn(request)
        }
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

    override fun createGpuParticleStateBuffer(maxParticles: Int): FloatArray {
        return FloatArray(maxParticles * ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE)
    }

    override fun writeGpuParticleSpawnBuffer(
        targetBuffer: FloatArray,
        maxParticles: Int,
        startSlot: Int,
        previouslyWrittenSlots: List<Int>
    ): ParticleWriteResult {
        return writeSpawnBuffer(
            targetBuffer,
            maxParticles,
            startSlot,
            previouslyWrittenSlots,
            ParticleEffectGenerator.GPU_FLOATS_PER_PARTICLE,
            false
        ) { effectGenerator, particle, buffer, baseIndex ->
            effectGenerator.writeGpuParticle(particle, buffer, baseIndex)
        }
    }

    override fun writeGpuParticleStateSpawnBuffer(
        targetBuffer: FloatArray,
        maxParticles: Int,
        startSlot: Int,
        previouslyWrittenSlots: List<Int>
    ): ParticleWriteResult {
        return writeSpawnBuffer(
            targetBuffer,
            maxParticles,
            startSlot,
            previouslyWrittenSlots,
            ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE,
            true
        ) { effectGenerator, particle, buffer, baseIndex ->
            effectGenerator.writeGpuState(particle, buffer, baseIndex)
        }
    }

    private fun writeSpawnBuffer(
        targetBuffer: FloatArray,
        maxParticles: Int,
        startSlot: Int,
        previouslyWrittenSlots: List<Int>,
        floatsPerParticle: Int,
        shouldDrainWrittenParticles: Boolean,
        writeParticle: (ParticleEffectGenerator, Particle, FloatArray, Int) -> Unit
    ): ParticleWriteResult {
        var activeCount = 0
        val clampedMaxParticles = maxParticles.coerceAtLeast(0)
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
        writeLoop@ for (effectGenerator in effectGeneratorList) {
            var writtenParticleCount = 0
            for (particle in effectGenerator.particles) {
                if (activeCount >= clampedMaxParticles) break
                if (!shouldDrainWrittenParticles && !effectGenerator.shouldWriteGpuParticle(particle)) {
                    writtenParticleCount++
                    continue
                }
                val slotGroup = effectGenerator.gpuSlotGroup(particle)
                val reservation = if (slotGroup != null) slotLayout.reservations[slotGroup] else null
                val slotIndex = if (reservation != null && reservation.slotCount > 0) {
                    reservation.firstSlot + particle.id.mod(reservation.slotCount.toLong()).toInt()
                } else {
                    slot
                }
                val baseIndex = slotIndex * floatsPerParticle
                if (baseIndex + (floatsPerParticle - 1) >= targetBuffer.size) {
                    break
                }
                writeParticle(effectGenerator, particle, targetBuffer, baseIndex)
                dirtySlotFlags[slotIndex] = true
                writtenSlotFlags[slotIndex] = true
                activeCount++
                writtenParticleCount++
                if (slotGroup == null && ringBufferCapacity > 0) {
                    slot = (slot + 1) % ringBufferCapacity
                }
            }
            if (shouldDrainWrittenParticles && writtenParticleCount > 0) {
                effectGenerator.particles.subList(0, writtenParticleCount).clear()
            }
            if (activeCount >= clampedMaxParticles) break@writeLoop
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
