package com.github.adamyork.kparticles.wasm.service

import com.github.adamyork.particles.service.EngineTiming
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.particles.service.CommonPhysicsSettingsService
import com.github.adamyork.particles.service.CommonRuntimeService
import com.github.adamyork.particles.service.PhysicsSettingsService
import com.github.adamyork.particles.service.RuntimeService
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ServiceConfig {

    @ParticleScope
    @Provides
    fun provideAssetService(impl: WasmJsAssetService): AssetService = impl

    @ParticleScope
    @Provides
    fun providePhysicsSettingsService(): PhysicsSettingsService = CommonPhysicsSettingsService()

    @ParticleScope
    @Provides
    fun provideHttpClient(): HttpClient = HttpClient(Js) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }
    }

    @ParticleScope
    @Provides
    fun provideRuntimeService(
        engineTiming: EngineTiming,
        physicsSettingsService: PhysicsSettingsService
    ): RuntimeService = CommonRuntimeService(engineTiming, physicsSettingsService)


}
