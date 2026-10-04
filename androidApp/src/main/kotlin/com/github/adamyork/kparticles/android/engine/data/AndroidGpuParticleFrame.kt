package com.github.adamyork.kparticles.android.engine.data

/**
 * Per-frame input payload consumed by the Android OpenGL particle runtime.
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
    val burstFrameGrowthMultiplier: Float,
    val burstSpeedCoefficient: Float,
    val projectileSpeed: Float,
    val mapItemReturnSpeed: Float,
    val mapItemReturnMinTravelDist: Float
)
