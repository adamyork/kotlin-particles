package com.github.adamyork.kparticles.android.gui

import androidx.compose.runtime.Composable
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.Engine
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.TestBed
import com.github.adamyork.kparticles.platform.gui.UiController
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class AndroidTestBed(
    private val assetService: AssetService,
    private val engine: Engine,
    private val particleFactory: ParticleFactory,
    private val runtimeService: RuntimeService,
    private val screenDimensionsService: ScreenDimensionsService,
    private val platformInterop: PlatformInterop
) : TestBed {

    private val controller = UiController(
        assetService = assetService,
        engine = engine,
        particleFactory = particleFactory,
        runtimeService = runtimeService,
        screenDimensionsService = screenDimensionsService
    )

    private val screen = AndroidUiMain(
        controller = controller,
        runtimeService = runtimeService,
        screenDimensionsService = screenDimensionsService,
        platformInterop = platformInterop
    )

    @Composable
    override fun Build() {
        screen.Build()
    }
}
