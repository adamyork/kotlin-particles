package com.github.adamyork.kparticles.platform.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.adamyork.kparticles.platform.engine.EngineException
import com.github.adamyork.kparticles.platform.engine.data.CommonImage
import com.github.adamyork.kparticles.platform.gui.data.ScreenDimensions

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class UiDrawLayer(
    protected val screenDimensionsService: ScreenDimensionsService
) {

    abstract var foregroundPaint: Any
    abstract var foregroundBitmap: Any?

    @Composable
    fun Build(
        isRunning: Boolean,
        onFpsLabelChanged: (String) -> Unit = {}
    ) {
        val screenDimensions = remember { screenDimensionsService.getScreenDimensions() }
        Box(modifier = Modifier.size(width = screenDimensions.width.dp, height = screenDimensions.height.dp)) {
            OverlayLayer()
        }

        LaunchedEffect(isRunning) {
            onFpsLabelChanged(if (isRunning) "FPS: running" else "FPS: paused")
        }
    }

    @Composable
    private fun LayerCanvas(
        bitmap: ImageBitmap?,
        screenDimensions: ScreenDimensions,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        isSplash: Boolean = false
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .clip(RectangleShape)
        ) {
            bitmap?.let { image ->
                if (isSplash) {
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(
                            (screenDimensions.width * density).toInt(),
                            (screenDimensions.height * density).toInt()
                        )
                    )
                } else {
                    val viewportWidth = screenDimensions.width.coerceAtMost(image.width)
                    val viewportHeight = screenDimensions.height.coerceAtMost(image.height)
                    val maxSrcX = (image.width - viewportWidth).coerceAtLeast(0)
                    val maxSrcY = (image.height - viewportHeight).coerceAtLeast(0)
                    val srcX = offsetX.toInt().coerceIn(0, maxSrcX)
                    val srcY = offsetY.toInt().coerceIn(0, maxSrcY)
                    val dstWidth = (screenDimensions.width * density).toInt()
                    val dstHeight = (screenDimensions.height * density).toInt()
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(viewportWidth, viewportHeight),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(dstWidth, dstHeight)
                    )
                }
            }
        }
    }

    @Composable
    protected open fun ForegroundLayerCanvas(image: Any?) {
        throw RuntimeException("must implement")
    }

    @Composable
    protected open fun OverlayLayer() {
        // Optional platform-specific overlay layer (for example, a GPU particle surface).
    }


    open fun drawForeground(image: CommonImage) {
        throw EngineException("must implement")
    }


    open fun clearAllLayers() {
        foregroundBitmap = null
    }

}
