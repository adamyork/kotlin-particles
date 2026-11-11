package com.github.adamyork.kparticles.platform.harness

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.engine.ParticleEngineDiagnostics
import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.GpuParticleStateWriter
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ParticleEngines
import com.github.adamyork.particles.engine.ParticlePhysicsEngine
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import com.github.adamyork.particles.service.RuntimeService
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticleEngineConfig {

    @ParticleScope
    @Provides
    fun provideParticleEngines(
        registry: ParticleEffectRegistry,
        physicsSettingsService: ParticlePhysicsSettingsService,
        runtimeService: RuntimeService,
        styleShaders: List<GpuStyleShader>
    ): ParticleEngines = ParticleEngines.create(registry, physicsSettingsService, runtimeService, styleShaders, metricsEnabled = true)

    @Provides
    fun provideParticlePhysicsEngine(engines: ParticleEngines): ParticlePhysicsEngine = engines.physicsEngine

    @Provides
    fun provideParticleEngineDiagnostics(engines: ParticleEngines): ParticleEngineDiagnostics = engines.particleEngineDiagnostics

    @Provides
    fun provideGpuDataPacketWriter(engines: ParticleEngines): GpuDataPacketWriter = engines.gpuDataPacketWriter

    @Provides
    fun provideGpuParticleStateWriter(engines: ParticleEngines): GpuParticleStateWriter = engines.gpuParticleStateWriter

}
