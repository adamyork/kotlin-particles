package com.github.adamyork.kparticles.wasm.gui

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.gui.CommonScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.TestBed
import com.github.adamyork.kparticles.platform.gui.CommonTestBedColorScheme
import com.github.adamyork.kparticles.platform.gui.TestBedColorScheme
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface GuiConfig {

    val testBed: TestBed
    val testBedColorScheme: TestBedColorScheme
    val screenDimensionsService: ScreenDimensionsService

    @ParticleScope
    @Provides
    fun provideGameLayer(impl: WasmJsTestBed): TestBed = impl

    @ParticleScope
    @Provides
    fun provideTestBedColorScheme(impl: CommonTestBedColorScheme): TestBedColorScheme = impl

    @ParticleScope
    @Provides
    fun provideScreenDimensionsService(impl: CommonScreenDimensionsService): ScreenDimensionsService = impl

}
