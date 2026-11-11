package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.shader.BuiltInStyleShaders
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.service.CommonParticlePhysicsSettingsService
import com.github.adamyork.particles.service.CommonRuntimeService
import com.github.adamyork.particles.service.EngineTiming
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import com.github.adamyork.particles.service.RuntimeService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class ParticleEngines private constructor(
    public val registry: ParticleEffectRegistry,
    public val physicsSettingsService: ParticlePhysicsSettingsService,
    public val runtimeService: RuntimeService,
    public val particleEngineDiagnostics: ParticleEngineDiagnostics,
    public val physicsEngine: ParticlePhysicsEngine,
    public val gpuDataPacketWriter: GpuDataPacketWriter,
    public val gpuParticleStateWriter: GpuParticleStateWriter
) {

    public companion object {

        public fun create(
            registry: ParticleEffectRegistry,
            physicsSettingsService: ParticlePhysicsSettingsService,
            runtimeService: RuntimeService,
            styleShaders: List<GpuStyleShader> = BuiltInStyleShaders.DEFAULTS,
            metricsEnabled: Boolean = false
        ): ParticleEngines {
            val particleEngineDiagnostics: ParticleEngineDiagnostics =
                if (metricsEnabled) CommonParticleEngineDiagnostics(registry) else NoOpParticleEngineDiagnostics()
            val spatialGrid = CommonSpatialGrid(physicsSettingsService)
            val particlePhysics = CommonParticlePhysics({ runtimeService }, physicsSettingsService, particleEngineDiagnostics, registry)
            val collision = CommonParticleCollision(particlePhysics, spatialGrid, physicsSettingsService)
            return ParticleEngines(
                registry = registry,
                physicsSettingsService = physicsSettingsService,
                runtimeService = runtimeService,
                particleEngineDiagnostics = particleEngineDiagnostics,
                physicsEngine = CommonParticlePhysicsEngine(particlePhysics, collision, registry, runtimeService, particleEngineDiagnostics),
                gpuDataPacketWriter = GpuDataPacketWriter(
                    physicsSettingsService,
                    styleShaders.mapNotNull { styleShader -> styleShader.uniformSource }
                ),
                gpuParticleStateWriter = CommonGpuParticleStateWriter(registry)
            )
        }

        public fun createDefault(
            engineTiming: EngineTiming,
            styleShaders: List<GpuStyleShader> = BuiltInStyleShaders.DEFAULTS,
            metricsEnabled: Boolean = false
        ): ParticleEngines {
            val physicsSettingsService = CommonParticlePhysicsSettingsService()
            return create(
                ParticleEffectRegistry(),
                physicsSettingsService,
                CommonRuntimeService(engineTiming, physicsSettingsService),
                styleShaders,
                metricsEnabled
            )
        }

    }

    public fun <CanvasType> cpuRenderEngine(styleRenderers: CpuStyleRenderers<CanvasType>): ParticleRenderEngine<CanvasType> =
        CommonParticleRenderEngine(registry, styleRenderers, particleEngineDiagnostics)

    public fun <CanvasType> gpuRenderEngine(drawFunction: ParticleDrawFunction<CanvasType>): ParticleRenderEngine<CanvasType> =
        GpuParticleRenderEngine(registry, drawFunction)

}
