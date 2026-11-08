package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.data.ParticleImageSource
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
import org.jetbrains.skia.Canvas
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.CpuStyleRenderers
import com.github.adamyork.particles.wasm.engine.WasmGpuRenderers
import com.github.adamyork.particles.wasm.engine.WasmImageRenderer
import com.github.adamyork.particles.wasm.engine.WasmShapeRenderer

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface EngineConfig {

    @ParticleScope
    @Provides
    fun provideEngine(
        platformInterop: PlatformInterop,
        wasmJsEngine: WasmJsEngine,
        wasmJsGpuEngine: WasmJsGpuEngine
    ): Engine = if (platformInterop.isGpuEngineSupported()) wasmJsGpuEngine else wasmJsEngine

    @Provides
    @IntoSet
    fun provideShapeRenderer(impl: WasmShapeRenderer): CpuStyleRenderer<Canvas> = impl

    @ParticleScope
    @Provides
    fun provideWasmShapeRenderer(engineDiagnostics: EngineDiagnostics): WasmShapeRenderer = WasmShapeRenderer(engineDiagnostics)

    @Provides
    @IntoSet
    fun provideImageRenderer(imageSource: ParticleImageSource): CpuStyleRenderer<Canvas> = WasmImageRenderer(imageSource)

    @ParticleScope
    @Provides
    fun provideGpuRenderers(
        gpuDataPacketWriter: GpuDataPacketWriter
    ): WasmGpuRenderers = WasmGpuRenderers(gpuDataPacketWriter)

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
        gpuRenderers: WasmGpuRenderers,
        engineDiagnostics: EngineDiagnostics
    ): ParticleRenderEngine<Canvas> = if (platformInterop.isGpuEngineSupported()) {
        GpuParticleRenderEngine(particleEffectRegistry, gpuRenderers.drawFunction)
    } else {
        CommonParticleRenderEngine(particleEffectRegistry, styleRenderers, engineDiagnostics)
    }

}
