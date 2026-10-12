package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class GpuSlotGroup(val capacity: Int) {

    private var nextSlot: Int = 0

    fun claimNextSlot(): Int {
        val claimedSlot = nextSlot
        nextSlot = (nextSlot + 1) % capacity
        return claimedSlot
    }
}
