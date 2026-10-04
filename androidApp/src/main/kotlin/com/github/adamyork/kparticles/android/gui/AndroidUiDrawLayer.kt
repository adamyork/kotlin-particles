package com.github.adamyork.kparticles.android.gui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntSize
import com.github.adamyork.kparticles.android.engine.data.AndroidImage
import com.github.adamyork.kparticles.platform.engine.data.CommonImage
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.platform.gui.UiDrawLayer

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class AndroidUiDrawLayer(screenDimensionsService: ScreenDimensionsService) : UiDrawLayer(
    screenDimensionsService
) {

    override var foregroundPaint: Any = Paint().apply {}

    override var foregroundBitmap: Any? by mutableStateOf(null)

    @Composable
    override fun ForegroundLayerCanvas(image: Any?) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RectangleShape)
        ) {
            image?.let { foreground ->
                val imageBitmap = when (foreground) {
                    is AndroidImage -> foreground.bitmap.asImageBitmap()
                    is Bitmap -> foreground.asImageBitmap()
                    is ImageBitmap -> foreground
                    else -> return@let
                }
                drawIntoCanvas { canvas ->
                    val targetWidth = size.width.toInt()
                    val targetHeight = size.height.toInt()
                    val nativeBitmap = when (foreground) {
                        is AndroidImage -> foreground.bitmap
                        is Bitmap -> foreground
                        else -> null
                    }
                    if (nativeBitmap != null) {
                        val src = Rect(0, 0, nativeBitmap.width, nativeBitmap.height)
                        val dst = Rect(0, 0, targetWidth, targetHeight)
                        canvas.nativeCanvas.drawBitmap(nativeBitmap, src, dst, null)
                    } else {
                        drawImage(
                            image = imageBitmap,
                            dstSize = IntSize(targetWidth, targetHeight)
                        )
                    }
                }
            }
        }
    }

    override fun drawForeground(image: CommonImage) {
        foregroundBitmap = image
    }

    @Composable
    override fun OverlayLayer() {
        AndroidGpuParticleOverlay().Build()
    }

}
