package com.github.adamyork.kparticles.wasm.gui

import androidx.compose.runtime.Composable
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.Engine
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.TestBed
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.gui.UiController
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class WasmJsTestBed(
    private val assetService: AssetService,
    private val engine: Engine,
    private val particleFactory: ParticleFactory,
    private val runtimeService: RuntimeService,
    private val screenDimensionsService: ScreenDimensionsService,
    private val particleLayer: WasmJsUiParticleLayer,
    private val platformInterop: PlatformInterop,
) : TestBed {

    private val controller = UiController(
        assetService = assetService,
        engine = engine,
        particleFactory = particleFactory,
        runtimeService = runtimeService,
        screenDimensionsService = screenDimensionsService
    )

    private val screen = WasmUiMain(
        controller = controller,
        runtimeService = runtimeService,
        screenDimensionsService = screenDimensionsService,
        platformInterop = platformInterop,
        particleLayer = particleLayer
    )

    @Composable
    override fun Build() {
        screen.Build()
    }
}
