package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import kotlin.math.max

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class GpuDataPacketWriter(
    private val physicsSettingsService: ParticlePhysicsSettingsService,
    private val styleUniformSources: List<GpuStyleUniformSource>
) {

    public companion object {
        public const val SIMULATION_PACKET_FLOATS: Int = 40
        public const val RENDER_PACKET_FLOATS: Int = 24
        public const val CONTACT_VELOCITY_RELAXATION_EXPONENT: Float = 0.75f
        public const val LIBRARY_RENDER_FLOATS: Int = 6
    }

    init {
        val styleFloatCount = styleUniformSources.sumOf { styleUniformSource -> styleUniformSource.floatCount }
        if (LIBRARY_RENDER_FLOATS + styleFloatCount > RENDER_PACKET_FLOATS) {
            throw EngineException(
                "Style uniforms need ${LIBRARY_RENDER_FLOATS + styleFloatCount} floats but the render packet holds $RENDER_PACKET_FLOATS"
            )
        }
    }

    public fun writeSimulationDataPacket(
        targetBuffer: FloatArray,
        deltaTime: Double,
        worldTime: Double,
        spriteTicksPerFrame: Double,
        maximumParticles: Int,
        viewPort: ParticleViewPort,
        subframeIndex: Int
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
        targetBuffer[writeIndex++] = physicsSettingsService.dragCoefficient.toFloat()
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
        targetBuffer[writeIndex++] = physicsSettingsService.shatterToughness.toFloat()
        targetBuffer[writeIndex++] = physicsSettingsService.positionCorrectionSlop.toFloat()
        targetBuffer[writeIndex++] = 0f
        targetBuffer[writeIndex++] = physicsSettingsService.positionCorrectionFactor.toFloat()
        targetBuffer[writeIndex++] = CONTACT_VELOCITY_RELAXATION_EXPONENT
        targetBuffer[writeIndex++] = subframeIndex.toFloat()
        targetBuffer[writeIndex] = physicsSettingsService.windVelocity.toFloat()
    }

    public fun writeRenderDataPacket(
        targetBuffer: FloatArray,
        viewPort: ParticleViewPort,
        canvasWidth: Int,
        canvasHeight: Int,
        renderScale: Float,
        spriteTicksPerFrame: Double
    ) {
        var writeIndex = 0
        targetBuffer[writeIndex++] = viewPort.x.toFloat()
        targetBuffer[writeIndex++] = viewPort.y.toFloat()
        targetBuffer[writeIndex++] = canvasWidth.toFloat()
        targetBuffer[writeIndex++] = canvasHeight.toFloat()
        targetBuffer[writeIndex++] = renderScale
        targetBuffer[writeIndex++] = spriteTicksPerFrame.toFloat()
        for (sourceIndex in styleUniformSources.indices) {
            val styleUniformSource = styleUniformSources[sourceIndex]
            styleUniformSource.writeUniforms(targetBuffer, writeIndex)
            writeIndex += styleUniformSource.floatCount
        }
        targetBuffer.fill(0f, writeIndex, RENDER_PACKET_FLOATS)
    }
}
