package com.github.adamyork.kparticles.android.engine

import com.github.adamyork.particles.engine.GpuParticleStateWriter
import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.data.ParticleImageSource
import android.graphics.Canvas
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.engine.CommonParticleRenderEngine
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.particles.engine.GpuParticleRenderEngine
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ParticleRenderEngine
import com.github.adamyork.kparticles.platform.harness.Engine
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.CpuStyleRenderers
import com.github.adamyork.particles.android.engine.AndroidGpuRenderers
import com.github.adamyork.particles.android.engine.AndroidImageRenderer
import com.github.adamyork.particles.android.engine.AndroidShapeRenderer

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface EngineConfig {

    @ParticleScope
    @Provides
    fun provideEngine(
        platformInterop: PlatformInterop,
        androidEngine: AndroidEngine,
        androidGpuEngine: AndroidGpuEngine
    ): Engine = if (platformInterop.isGpuEngineSupported()) androidGpuEngine else androidEngine

    @Provides
    @IntoSet
    fun provideShapeRenderer(impl: AndroidShapeRenderer): CpuStyleRenderer<Canvas> = impl

    @ParticleScope
    @Provides
    fun provideAndroidShapeRenderer(): AndroidShapeRenderer = AndroidShapeRenderer()

    @Provides
    @IntoSet
    fun provideImageRenderer(imageSource: ParticleImageSource): CpuStyleRenderer<Canvas> = AndroidImageRenderer(imageSource)

    @ParticleScope
    @Provides
    fun provideGpuRenderers(
        gpuParticleStateWriter: GpuParticleStateWriter,
        gpuDataPacketWriter: GpuDataPacketWriter
    ): AndroidGpuRenderers = AndroidGpuRenderers(gpuParticleStateWriter, gpuDataPacketWriter)

    @ParticleScope
    @Provides
    fun provideCpuStyleRenderers(renderers: Set<CpuStyleRenderer<Canvas>>): CpuStyleRenderers<Canvas> =
        CpuStyleRenderers(renderers)

    @ParticleScope
    @Provides
    fun provideParticleRenderEngine(
        platformInterop: PlatformInterop,
        particleEffectRegistry: ParticleEffectRegistry,
        styleRenderers: CpuStyleRenderers<Canvas>,
        gpuRenderers: AndroidGpuRenderers,
        engineDiagnostics: EngineDiagnostics
    ): ParticleRenderEngine<Canvas> = if (platformInterop.isGpuEngineSupported()) {
        GpuParticleRenderEngine(particleEffectRegistry, gpuRenderers.drawFunction)
    } else {
        CommonParticleRenderEngine(particleEffectRegistry, styleRenderers, engineDiagnostics)
    }

}
