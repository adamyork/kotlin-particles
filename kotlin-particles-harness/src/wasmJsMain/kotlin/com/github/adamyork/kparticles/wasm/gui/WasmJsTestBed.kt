package com.github.adamyork.kparticles.wasm.gui

import androidx.compose.runtime.Composable
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.kparticles.platform.harness.Engine
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.TestBed
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.kparticles.platform.assets.CollectibleImageStore
import com.github.adamyork.particles.service.RuntimeService
import com.github.adamyork.kparticles.platform.gui.UiController
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class WasmJsTestBed(
    private val assetService: AssetService,
    private val engine: Engine,
    private val particleEffectRegistry: ParticleEffectRegistry,
    private val runtimeService: RuntimeService,
    private val screenDimensionsService: ScreenDimensionsService,
    private val particleLayer: WasmJsUiParticleLayer,
    private val platformInterop: PlatformInterop,
    private val engineDiagnostics: EngineDiagnostics,
    private val collectibleImageStore: CollectibleImageStore
) : TestBed {

    private val controller = UiController(
        assetService = assetService,
        engine = engine,
        particleEffectRegistry = particleEffectRegistry,
        runtimeService = runtimeService,
        screenDimensionsService = screenDimensionsService,
        collectibleImageStore = collectibleImageStore
    )

    private val screen = WasmUiMain(
        controller = controller,
        runtimeService = runtimeService,
        screenDimensionsService = screenDimensionsService,
        platformInterop = platformInterop,
        particleLayer = particleLayer,
        engineDiagnostics = engineDiagnostics
    )

    @Composable
    override fun Build() {
        screen.Build()
    }
}
