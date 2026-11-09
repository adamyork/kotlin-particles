package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonGpuParticleStateWriter(
    private val particleEffectRegistry: ParticleEffectRegistry
) : GpuParticleStateWriter {

    private class GpuSlotReservation(val firstSlot: Int, val slotCount: Int)

    private class GpuSlotLayout(
        val maxParticles: Int,
        val reservations: Map<GpuSlotGroup, GpuSlotReservation>,
        val ringBufferCapacity: Int,
        val styleRanges: Map<ParticleRenderStyle, IntRange>
    )

    private val gpuSlotGroups: List<GpuSlotGroup> by lazy {
        val firstStyleCodeByGroup = LinkedHashMap<GpuSlotGroup, Float>()
        for (generator in particleEffectRegistry.generators()) {
            for (slotGroup in generator.gpuSlotGroups) {
                val styleCode = generator.effect.renderStyle.code
                val knownCode = firstStyleCodeByGroup[slotGroup]
                if (knownCode == null || styleCode < knownCode) firstStyleCodeByGroup[slotGroup] = styleCode
            }
        }
        firstStyleCodeByGroup.entries.sortedBy { entry -> entry.value }.map { entry -> entry.key }
    }

    private var cachedGpuSlotLayout: GpuSlotLayout? = null

    override fun getStyleSlotRanges(maxParticles: Int): Map<ParticleRenderStyle, IntRange> =
        getOrCreateGpuSlotLayout(maxParticles.coerceAtLeast(0)).styleRanges

    override fun createGpuParticleStateBuffer(maxParticles: Int): FloatArray {
        return FloatArray(maxParticles * ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE)
    }

    override fun writeGpuParticleStateSpawnBuffer(
        targetBuffer: FloatArray,
        maxParticles: Int,
        startSlot: Int,
        previouslyWrittenSlots: List<Int>
    ): ParticleWriteResult {
        val floatsPerParticle = ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE
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
        writeLoop@ for (effectGenerator in particleEffectRegistry.generators()) {
            var writtenParticleCount = 0
            for (particle in effectGenerator.particles) {
                if (activeCount >= clampedMaxParticles) break
                val slotGroup = effectGenerator.gpuSlotGroup(particle)
                if (slotGroup == null && effectGenerator.gpuSlotGroups.isNotEmpty()) {
                    throw EngineException("Generator ${effectGenerator.effect.id} declares GPU slot groups but returned no group for a particle")
                }
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
                effectGenerator.writeGpuState(particle, targetBuffer, baseIndex)
                dirtySlotFlags[slotIndex] = true
                writtenSlotFlags[slotIndex] = true
                activeCount++
                writtenParticleCount++
                if (slotGroup == null && ringBufferCapacity > 0) {
                    slot = (slot + 1) % ringBufferCapacity
                }
            }
            if (writtenParticleCount > 0) {
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
        val slotLayout = GpuSlotLayout(maxParticles, reservations, remainingSlots, computeStyleRanges(reservations, remainingSlots))
        cachedGpuSlotLayout = slotLayout
        return slotLayout
    }

    private fun computeStyleRanges(
        reservations: Map<GpuSlotGroup, GpuSlotReservation>,
        ringBufferCapacity: Int
    ): Map<ParticleRenderStyle, IntRange> {
        val firstSlots = HashMap<ParticleRenderStyle, Int>()
        val lastSlots = HashMap<ParticleRenderStyle, Int>()
        for (generator in particleEffectRegistry.generators()) {
            val renderStyle = generator.effect.renderStyle
            if (generator.gpuSlotGroups.isEmpty()) {
                if (ringBufferCapacity > 0) include(firstSlots, lastSlots, renderStyle, 0, ringBufferCapacity - 1)
                continue
            }
            for (slotGroup in generator.gpuSlotGroups) {
                val reservation = reservations[slotGroup] ?: continue
                if (reservation.slotCount <= 0) continue
                include(firstSlots, lastSlots, renderStyle, reservation.firstSlot, reservation.firstSlot + reservation.slotCount - 1)
            }
        }
        val styleRanges = LinkedHashMap<ParticleRenderStyle, IntRange>()
        for ((renderStyle, firstSlot) in firstSlots) {
            styleRanges[renderStyle] = firstSlot..lastSlots.getValue(renderStyle)
        }
        return styleRanges
    }

    private fun include(
        firstSlots: MutableMap<ParticleRenderStyle, Int>,
        lastSlots: MutableMap<ParticleRenderStyle, Int>,
        renderStyle: ParticleRenderStyle,
        firstSlot: Int,
        lastSlot: Int
    ) {
        val knownFirst = firstSlots[renderStyle]
        if (knownFirst == null || firstSlot < knownFirst) firstSlots[renderStyle] = firstSlot
        val knownLast = lastSlots[renderStyle]
        if (knownLast == null || lastSlot > knownLast) lastSlots[renderStyle] = lastSlot
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
