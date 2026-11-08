package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.shader.BuiltInStyleShaders
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.service.CommonPhysicsSettingsService
import com.github.adamyork.particles.service.CommonRuntimeService
import com.github.adamyork.particles.service.EngineTiming
import com.github.adamyork.particles.service.PhysicsSettingsService
import com.github.adamyork.particles.service.RuntimeService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class ParticleEngines private constructor(
    public val registry: ParticleEffectRegistry,
    public val physicsSettingsService: PhysicsSettingsService,
    public val runtimeService: RuntimeService,
    public val engineDiagnostics: EngineDiagnostics,
    public val physicsEngine: ParticlePhysicsEngine,
    public val gpuDataPacketWriter: GpuDataPacketWriter,
    public val gpuParticleStateWriter: GpuParticleStateWriter
) {

    public fun <CanvasType> cpuRenderEngine(styleRenderers: CpuStyleRenderers<CanvasType>): ParticleRenderEngine<CanvasType> =
        CommonParticleRenderEngine(registry, styleRenderers, engineDiagnostics)

    public fun <CanvasType> gpuRenderEngine(drawFunction: ParticleDrawFunction<CanvasType>): ParticleRenderEngine<CanvasType> =
        GpuParticleRenderEngine(registry, drawFunction)

    public companion object {

        public fun create(
            registry: ParticleEffectRegistry,
            physicsSettingsService: PhysicsSettingsService,
            runtimeService: RuntimeService,
            styleShaders: List<GpuStyleShader> = BuiltInStyleShaders.DEFAULTS,
            engineDiagnostics: EngineDiagnostics = EngineDiagnostics(registry)
        ): ParticleEngines {
            val spatialGrid = CommonSpatialGrid(physicsSettingsService, engineDiagnostics)
            val particlePhysics = CommonParticlePhysics({ runtimeService }, physicsSettingsService, engineDiagnostics, registry)
            val collision = CommonParticleCollision(particlePhysics, spatialGrid, physicsSettingsService, engineDiagnostics)
            return ParticleEngines(
                registry = registry,
                physicsSettingsService = physicsSettingsService,
                runtimeService = runtimeService,
                engineDiagnostics = engineDiagnostics,
                physicsEngine = CommonParticlePhysicsEngine(particlePhysics, collision, registry, runtimeService, engineDiagnostics),
                gpuDataPacketWriter = GpuDataPacketWriter(
                    physicsSettingsService,
                    styleShaders.mapNotNull { styleShader -> styleShader.uniformSource }
                ),
                gpuParticleStateWriter = CommonGpuParticleStateWriter(registry)
            )
        }

        public fun createDefault(
            engineTiming: EngineTiming,
            styleShaders: List<GpuStyleShader> = BuiltInStyleShaders.DEFAULTS
        ): ParticleEngines {
            val physicsSettingsService = CommonPhysicsSettingsService()
            return create(
                ParticleEffectRegistry(),
                physicsSettingsService,
                CommonRuntimeService(engineTiming, physicsSettingsService),
                styleShaders
            )
        }

    }

}
