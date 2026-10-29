package com.github.adamyork.kparticles.android.gui

import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.gui.ControlsPlacement
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.UiController
import com.github.adamyork.kparticles.platform.gui.UiDrawLayer
import com.github.adamyork.kparticles.platform.gui.UiMain
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.engine.EngineDiagnostics

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class AndroidUiMain(
    controller: UiController,
    runtimeService: RuntimeService,
    screenDimensionsService: ScreenDimensionsService,
    platformInterop: PlatformInterop,
    engineDiagnostics: EngineDiagnostics,
) : UiMain(controller, runtimeService, screenDimensionsService, platformInterop, engineDiagnostics) {
    override var uiDrawLayer: UiDrawLayer = AndroidUiDrawLayer(screenDimensionsService)
    override val controlsPlacement: ControlsPlacement = ControlsPlacement.OVERLAY
}
