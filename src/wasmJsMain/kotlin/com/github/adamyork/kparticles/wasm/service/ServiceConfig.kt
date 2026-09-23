package com.github.adamyork.kparticles.wasm.service

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.CommonPhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.CommonRuntimeService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ServiceConfig {

    @AppScope
    @Provides
    fun provideAssetService(impl: WasmJsAssetService): AssetService = impl

    @AppScope
    @Provides
    fun providePhysicsSettingsService(impl: CommonPhysicsSettingsService): PhysicsSettingsService = impl

    @AppScope
    @Provides
    fun provideHttpClient(): HttpClient = HttpClient(Js) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }
    }

    @AppScope
    @Provides
    fun provideRuntimeService(impl: CommonRuntimeService): RuntimeService = impl


}
