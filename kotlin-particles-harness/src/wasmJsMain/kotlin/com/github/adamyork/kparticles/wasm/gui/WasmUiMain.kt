package com.github.adamyork.kparticles.wasm.gui

import androidx.compose.ui.unit.dp
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.engine.ParticleEngineDiagnostics
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.particles.service.RuntimeService
import com.github.adamyork.kparticles.platform.gui.UiController
import com.github.adamyork.kparticles.platform.gui.UiDrawLayer
import com.github.adamyork.kparticles.platform.gui.UiMain

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class WasmUiMain(
    controller: UiController,
    runtimeService: RuntimeService,
    screenDimensionsService: ScreenDimensionsService,
    platformInterop: PlatformInterop,
    particleLayer: WasmJsUiParticleLayer,
    particleEngineDiagnostics: ParticleEngineDiagnostics
) : UiMain(controller, runtimeService, screenDimensionsService, platformInterop, particleEngineDiagnostics) {
    override var uiDrawLayer: UiDrawLayer = WasmJsUiDrawLayer(screenDimensionsService, particleLayer)
    override val centerHudWithinViewport: Boolean = true
    override val hudTopInset = 20.dp
    override val hudOverlayTopPadding = 0.dp
}
