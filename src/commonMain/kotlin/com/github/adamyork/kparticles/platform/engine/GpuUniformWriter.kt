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
class GpuUniformWriter(
    private val physicsSettingsService: PhysicsSettingsService
) {

    companion object {
        const val SIMULATION_UNIFORM_FLOATS: Int = 40
        const val RENDER_UNIFORM_FLOATS: Int = 24
        const val CONTACT_POSITION_RELAXATION_EXPONENT: Float = 0.5f
        const val CONTACT_VELOCITY_RELAXATION_EXPONENT: Float = 0.75f
    }

    fun writeSimulationUniforms(
        targetBuffer: FloatArray,
        deltaTime: Double,
        worldTime: Double,
        spriteTicksPerFrame: Double,
        maximumParticles: Int,
        viewPort: ViewPort
    ) {
        val cellSize = max(1.0, physicsSettingsService.spatialGridCellSize)
        var writeIndex = 0
        targetBuffer[writeIndex++] = deltaTime.toFloat()
        targetBuffer[writeIndex++] = worldTime.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.gravity.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.mediumDensity.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.boundaryZ.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.boundaryDepth.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.wallRestitution.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.friction.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.drag.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.maxParticleSpeed.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.minActiveVelocity.toFloat()
        targetBuffer[writeIndex++] = spriteTicksPerFrame.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.flowAmplitude.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.flowFrequency.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.flowWavelength.toFloat()
        targetBuffer[writeIndex++] = maximumParticles.toFloat()
        targetBuffer[writeIndex++] = viewPort.x.toFloat()
        targetBuffer[writeIndex++] = viewPort.y.toFloat()
        targetBuffer[writeIndex++] = viewPort.width.toFloat()
        targetBuffer[writeIndex++] = viewPort.height.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.collisionPlaneThickness.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.gravityPlaneThickness.toFloat()
        targetBuffer[writeIndex++] = cellSize.toFloat()
        targetBuffer[writeIndex++] = max(1, (viewPort.width / cellSize).toInt() + 1).toFloat()
        targetBuffer[writeIndex++] = max(1, (viewPort.height / cellSize).toInt() + 1).toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.gravitationalConstant.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.gravitationalSoftening.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.gravitySourceCount.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.gravityMinimumAcceleration.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.accretionMassRatio.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.shatterMinimumFragmentRadius.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.shatterMinimumFragmentCount.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.shatterMaximumFragmentCount.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.shatterMomentumPerFragment.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.shatterSpreadSpeed.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.shatterMaxVelocity.toFloat()
        targetBuffer[writeIndex++] = CONTACT_POSITION_RELAXATION_EXPONENT
        targetBuffer[writeIndex++] = CONTACT_VELOCITY_RELAXATION_EXPONENT
        targetBuffer[writeIndex++] = 0f
        targetBuffer[writeIndex] = 0f
    }

    fun writeRenderUniforms(
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
        var writeIndex = 0
        targetBuffer[writeIndex++] = viewPort.x.toFloat()
        targetBuffer[writeIndex++] = viewPort.y.toFloat()
        targetBuffer[writeIndex++] = canvasWidth.toFloat()
        targetBuffer[writeIndex++] = canvasHeight.toFloat()
        targetBuffer[writeIndex++] = renderScale
        targetBuffer[writeIndex++] = spriteFrameWidth.toFloat()
        targetBuffer[writeIndex++] = spriteFrameHeight.toFloat()
        targetBuffer[writeIndex++] = spriteSheetFrameCount.toFloat()
        targetBuffer[writeIndex++] = spriteTicksPerFrame.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.dripTrailRadiusRatio.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.dripNeckLengthRatio.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.dripGlossIntensity.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.dripGlossStripeOffsetRatio.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.dripGlossStripeWidthRatio.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.dripGlossSpecularRadiusRatio.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.dripGlossSpecularOffsetRatio.toFloat()
        targetBuffer[writeIndex++] = BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT.toFloat()
        targetBuffer[writeIndex++] = BlobProjectileGenerator.PROJECTILE_BLOB_AMPLITUDE_RATIO
        targetBuffer[writeIndex++] = BlobProjectileGenerator.PROJECTILE_BLOB_BASE_FREQUENCY
        targetBuffer[writeIndex++] = BlobProjectileGenerator.PROJECTILE_BLOB_FREQUENCY_STEP
        targetBuffer[writeIndex++] = BlobProjectileGenerator.PROJECTILE_BLOB_PHASE_STEP
        targetBuffer[writeIndex++] = 0f
        targetBuffer[writeIndex++] = 0f
        targetBuffer[writeIndex] = 0f
    }
}
