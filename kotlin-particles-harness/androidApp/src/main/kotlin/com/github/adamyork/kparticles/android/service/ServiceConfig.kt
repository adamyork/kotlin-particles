package com.github.adamyork.kparticles.android.service

import com.github.adamyork.particles.service.EngineTiming
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.particles.service.CommonParticlePhysicsSettingsService
import com.github.adamyork.particles.service.CommonRuntimeService
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import com.github.adamyork.particles.service.RuntimeService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ServiceConfig {

    @ParticleScope
    @Provides
    fun provideAssetService(impl: AndroidAssetService): AssetService = impl

    @ParticleScope
    @Provides
    fun providePhysicsSettingsService(): ParticlePhysicsSettingsService = CommonParticlePhysicsSettingsService()

    @ParticleScope
    @Provides
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
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
        physicsSettingsService: ParticlePhysicsSettingsService
    ): RuntimeService = CommonRuntimeService(engineTiming, physicsSettingsService)


}
