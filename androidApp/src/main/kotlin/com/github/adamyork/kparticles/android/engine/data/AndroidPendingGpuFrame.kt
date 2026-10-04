package com.github.adamyork.kparticles.android.engine.data

internal data class AndroidPendingGpuFrame(
    val activeParticleCount: Int,
    val viewPortWidth: Float,
    val viewPortHeight: Float,
    val deltaTimeSeconds: Float,
    val gravity: Float,
    val burstFrameGrowthMultiplier: Float,
    val burstSpeedCoefficient: Float,
    val projectileSpeed: Float,
    val mapItemReturnSpeed: Float,
    val mapItemReturnMinTravelDist: Float
)

