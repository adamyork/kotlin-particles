package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.*
import me.tatarka.inject.annotations.Provides
import org.jetbrains.skia.Canvas

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface EngineConfig {

    @AppScope
    @Provides
    fun provideEngine(
        platformInterop: PlatformInterop,
        wasmJsEngine: WasmJsEngine,
        wasmJsGpuEngine: WasmJsGpuEngine
    ): Engine = if (platformInterop.isGpuEngineSupported()) wasmJsGpuEngine else wasmJsEngine

    @AppScope
    @Provides
    fun provideParticlePhysics(impl: CommonParticlePhysics): ParticlePhysics = impl

    @AppScope
    @Provides
    fun provideParticleFactory(impl: CommonParticleFactory): ParticleFactory = impl

    @AppScope
    @Provides
    fun provideCollision(impl: WasmJsParticleCollision): ParticleCollision = impl

    @AppScope
    @Provides
    fun provideSpatialGrid(impl: CommonSpatialGrid): SpatialGrid = impl

    @AppScope
    @Provides
    fun provideContactDetector(impl: CommonContactDetector): ContactDetector = impl

    @AppScope
    @Provides
    fun provideContactSolver(impl: CommonContactSolver): ContactSolver = impl

    @AppScope
    @Provides
    fun provideRenderers(impl: WasmCpuRenderers): Renderers<Canvas> = impl

}
