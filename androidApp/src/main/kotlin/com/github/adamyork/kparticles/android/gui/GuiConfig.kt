package com.github.adamyork.kparticles.android.gui

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.gui.CommonScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.TestBed
import com.github.adamyork.sparrow.platform.gui.CommonTestBedColorScheme
import com.github.adamyork.sparrow.platform.gui.TestBedColorScheme
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface GuiConfig {

    val testBed: TestBed
    val testBedColorScheme: TestBedColorScheme
    val screenDimensionsService: ScreenDimensionsService

    @AppScope
    @Provides
    fun provideGameLayer(impl: AndroidTestBed): TestBed = impl

    @AppScope
    @Provides
    fun provideTestBedColorScheme(impl: CommonTestBedColorScheme): TestBedColorScheme = impl

    @AppScope
    @Provides
    fun provideScreenDimensionsService(impl: CommonScreenDimensionsService): ScreenDimensionsService = impl

}
