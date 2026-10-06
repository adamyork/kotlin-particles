package com.github.adamyork.kparticles.android.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal class AndroidGpuParticleFrame(
    val sourceBuffer: FloatArray,
    val viewPortX: Float,
    val viewPortY: Float,
    val viewPortWidth: Float,
    val viewPortHeight: Float,
    val sizeMultiplier: Int,
    val deltaTimeSeconds: Float,
    val gravity: Float,
    val tickRate: Float,
    val simulationSpeed: Float,
    val gravityBoost: Float,
    val lifetimeDecay: Float
)
