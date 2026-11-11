package com.github.adamyork.kparticles.android.gui

import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.gui.ControlsPlacement
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.UiController
import com.github.adamyork.kparticles.platform.gui.UiDrawLayer
import com.github.adamyork.kparticles.platform.gui.UiMain
import com.github.adamyork.particles.service.RuntimeService
import com.github.adamyork.particles.engine.ParticleEngineDiagnostics

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class AndroidUiMain(
    controller: UiController,
    runtimeService: RuntimeService,
    screenDimensionsService: ScreenDimensionsService,
    platformInterop: PlatformInterop,
    particleEngineDiagnostics: ParticleEngineDiagnostics,
) : UiMain(controller, runtimeService, screenDimensionsService, platformInterop, particleEngineDiagnostics) {
    override var uiDrawLayer: UiDrawLayer = AndroidUiDrawLayer(screenDimensionsService)
    override val controlsPlacement: ControlsPlacement = ControlsPlacement.OVERLAY
}
