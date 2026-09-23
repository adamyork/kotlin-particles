package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.CommonParticles
import com.github.adamyork.kparticles.platform.engine.CommonPhysics
import com.github.adamyork.kparticles.platform.engine.Engine
import com.github.adamyork.kparticles.platform.engine.Particles
import com.github.adamyork.kparticles.platform.engine.Physics
import me.tatarka.inject.annotations.Provides

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
    fun providePhysics(impl: CommonPhysics): Physics = impl

    @AppScope
    @Provides
    fun provideParticles(impl: CommonParticles): Particles = impl


}
