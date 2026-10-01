package com.github.adamyork.kparticles.platform.gui

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.gui.data.ScreenDimensions
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class CommonScreenDimensionsService : ScreenDimensionsService {

    private val logger = KotlinLogging.logger {}

    private var screenDimensions: ScreenDimensions? = null

    override fun initialize(screenWidth: Int, screenHeight: Int) {
        if (screenDimensions != null) return
        val resolved = ScreenDimensions.fromScreenResolution(screenWidth, screenHeight)
        screenDimensions = resolved
        logger.info {
            "screen dimensions initialized: input=${screenWidth}x$screenHeight " +
                "resolved=${resolved.width}x${resolved.height} (capped at " +
                "${ScreenDimensions.MAX_WIDTH}x${ScreenDimensions.MAX_HEIGHT})"
        }
    }

    override fun getScreenDimensions(): ScreenDimensions {
        return screenDimensions ?: error("ScreenDimensions has not been initialized")
    }
}

