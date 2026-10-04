package com.github.adamyork.kparticles.android.gui

import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.UiController
import com.github.adamyork.kparticles.platform.gui.UiDrawLayer
import com.github.adamyork.kparticles.platform.gui.UiMain
import com.github.adamyork.kparticles.platform.service.RuntimeService

class AndroidUiMain(
    controller: UiController,
    runtimeService: RuntimeService,
    screenDimensionsService: ScreenDimensionsService,
    platformInterop: PlatformInterop,
) : UiMain(controller, runtimeService, screenDimensionsService, platformInterop) {
    override var uiDrawLayer: UiDrawLayer = AndroidUiDrawLayer(screenDimensionsService)
}
