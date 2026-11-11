package com.github.adamyork.kparticles.android.gui

import androidx.compose.runtime.Composable
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.harness.Engine
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.TestBed
import com.github.adamyork.kparticles.platform.gui.UiController
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.kparticles.platform.assets.CollectibleImageStore
import com.github.adamyork.particles.service.RuntimeService
import me.tatarka.inject.annotations.Inject
import com.github.adamyork.particles.engine.ParticleEngineDiagnostics

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AndroidTestBed(
    private val assetService: AssetService,
    private val engine: Engine,
    private val particleEffectRegistry: ParticleEffectRegistry,
    private val runtimeService: RuntimeService,
    private val screenDimensionsService: ScreenDimensionsService,
    private val platformInterop: PlatformInterop,
    private val particleEngineDiagnostics: ParticleEngineDiagnostics,
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

    private val screen = AndroidUiMain(
        controller = controller,
        runtimeService = runtimeService,
        screenDimensionsService = screenDimensionsService,
        platformInterop = platformInterop,
        particleEngineDiagnostics = particleEngineDiagnostics
    )

    @Composable
    override fun Build() {
        screen.Build()
    }
}
