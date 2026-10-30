package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.max

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class GpuDataPacketWriter(
    private val physicsSettingsService: PhysicsSettingsService
) {

    companion object {
        const val SIMULATION_PACKET_FLOATS: Int = 40
        const val RENDER_PACKET_FLOATS: Int = 24
        const val CONTACT_VELOCITY_RELAXATION_EXPONENT: Float = 0.75f
    }

    fun writeSimulationDataPacket(
        targetBuffer: FloatArray,
        deltaTime: Double,
        worldTime: Double,
        spriteTicksPerFrame: Double,
        maximumParticles: Int,
        viewPort: ViewPort,
        subframeIndex: Int
    ) {
        val cellSize = max(1.0, physicsSettingsService.spatialGridCellSize)
        floatArrayOf(
            deltaTime.toFloat(),
            worldTime.toFloat(),
            physicsSettingsService.gravity.toFloat(),
            physicsSettingsService.mediumDensity.toFloat(),
            physicsSettingsService.boundaryZ.toFloat(),
            physicsSettingsService.boundaryDepth.toFloat(),
            physicsSettingsService.wallRestitution.toFloat(),
            physicsSettingsService.friction.toFloat(),
            physicsSettingsService.drag.toFloat(),
            physicsSettingsService.dragCoefficient.toFloat(),
            physicsSettingsService.minActiveVelocity.toFloat(),
            spriteTicksPerFrame.toFloat(),
            physicsSettingsService.flowAmplitude.toFloat(),
            physicsSettingsService.flowFrequency.toFloat(),
            physicsSettingsService.flowWavelength.toFloat(),
            maximumParticles.toFloat(),
            viewPort.x.toFloat(),
            viewPort.y.toFloat(),
            viewPort.width.toFloat(),
            viewPort.height.toFloat(),
            physicsSettingsService.collisionPlaneThickness.toFloat(),
            physicsSettingsService.gravityPlaneThickness.toFloat(),
            cellSize.toFloat(),
            max(1, (viewPort.width / cellSize).toInt() + 1).toFloat(),
            max(1, (viewPort.height / cellSize).toInt() + 1).toFloat(),
            physicsSettingsService.gravitationalConstant.toFloat(),
            physicsSettingsService.gravitationalSoftening.toFloat(),
            physicsSettingsService.gravitySourceCount.toFloat(),
            physicsSettingsService.gravityMinimumAcceleration.toFloat(),
            physicsSettingsService.accretionMassRatio.toFloat(),
            physicsSettingsService.shatterMinimumFragmentRadius.toFloat(),
            physicsSettingsService.shatterMinimumFragmentCount.toFloat(),
            physicsSettingsService.shatterMaximumFragmentCount.toFloat(),
            physicsSettingsService.shatterToughness.toFloat(),
            physicsSettingsService.positionCorrectionSlop.toFloat(),
            0f,
            physicsSettingsService.positionCorrectionFactor.toFloat(),
            CONTACT_VELOCITY_RELAXATION_EXPONENT,
            subframeIndex.toFloat(),
            0f
        ).copyInto(targetBuffer)
    }

    fun writeRenderDataPacket(
        targetBuffer: FloatArray,
        viewPort: ViewPort,
        canvasWidth: Int,
        canvasHeight: Int,
        renderScale: Float,
        spriteFrameWidth: Int,
        spriteFrameHeight: Int,
        spriteSheetFrameCount: Int,
        spriteTicksPerFrame: Double
    ) {
        floatArrayOf(
            viewPort.x.toFloat(),
            viewPort.y.toFloat(),
            canvasWidth.toFloat(),
            canvasHeight.toFloat(),
            renderScale,
            spriteFrameWidth.toFloat(),
            spriteFrameHeight.toFloat(),
            spriteSheetFrameCount.toFloat(),
            spriteTicksPerFrame.toFloat(),
            physicsSettingsService.dripTrailRadiusRatio.toFloat(),
            physicsSettingsService.dripNeckLengthRatio.toFloat(),
            physicsSettingsService.dripGlossIntensity.toFloat(),
            physicsSettingsService.dripGlossStripeOffsetRatio.toFloat(),
            physicsSettingsService.dripGlossStripeWidthRatio.toFloat(),
            physicsSettingsService.dripGlossSpecularRadiusRatio.toFloat(),
            physicsSettingsService.dripGlossSpecularOffsetRatio.toFloat(),
            BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT.toFloat(),
            BlobProjectileGenerator.PROJECTILE_BLOB_AMPLITUDE_RATIO,
            BlobProjectileGenerator.PROJECTILE_BLOB_BASE_FREQUENCY,
            BlobProjectileGenerator.PROJECTILE_BLOB_FREQUENCY_STEP,
            BlobProjectileGenerator.PROJECTILE_BLOB_PHASE_STEP,
            0f,
            0f,
            0f
        ).copyInto(targetBuffer)
    }
}
