package com.github.adamyork.kparticles.platform.gui

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.gui.data.ScreenDimensions
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CommonScreenDimensionsService : ScreenDimensionsService {

    private var screenDimensions: ScreenDimensions? = null

    override fun initialize(screenWidth: Int, screenHeight: Int) {
        if (screenDimensions != null) return
        screenDimensions = ScreenDimensions.fromScreenResolution(screenWidth, screenHeight)
    }

    override fun getScreenDimensions(): ScreenDimensions {
        return screenDimensions ?: error("ScreenDimensions has not been initialized")
    }
}

