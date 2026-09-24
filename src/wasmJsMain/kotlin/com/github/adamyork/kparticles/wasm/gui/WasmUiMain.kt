package com.github.adamyork.kparticles.wasm.gui

import androidx.compose.ui.unit.dp
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.gui.UiController
import com.github.adamyork.kparticles.platform.gui.UiDrawLayer
import com.github.adamyork.kparticles.platform.gui.UiMain

class WasmUiMain(
    controller: UiController,
    runtimeService: RuntimeService,
    screenDimensionsService: ScreenDimensionsService,
    platformInterop: PlatformInterop,
    particleLayer: WasmJsUiParticleLayer
) : UiMain(controller, runtimeService, screenDimensionsService, platformInterop) {
    override var uiDrawLayer: UiDrawLayer = WasmJsUiDrawLayer(screenDimensionsService, particleLayer)
    override val centerHudWithinViewport: Boolean = true
    override val hudTopInset = 20.dp
    override val hudOverlayTopPadding = 0.dp
}
