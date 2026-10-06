package com.github.adamyork.kparticles.android.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal data class AndroidPendingGpuFrame(
    val activeParticleCount: Int,
    val viewPortWidth: Float,
    val viewPortHeight: Float,
    val deltaTimeSeconds: Float,
    val gravity: Float,
    val tickRate: Float,
    val simulationSpeed: Float,
    val gravityBoost: Float,
    val lifetimeDecay: Float
)
