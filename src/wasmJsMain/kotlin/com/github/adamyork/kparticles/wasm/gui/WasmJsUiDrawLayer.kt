package com.github.adamyork.kparticles.wasm.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.unit.dp
import com.github.adamyork.kparticles.platform.engine.data.CommonImage
import com.github.adamyork.kparticles.platform.gui.ScreenDimensionsService
import com.github.adamyork.kparticles.wasm.engine.data.WasmJsImage
import com.github.adamyork.kparticles.platform.gui.UiDrawLayer
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Image as SkiaImage

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class WasmJsUiDrawLayer(
    screenDimensionsService: ScreenDimensionsService,
    private val particleLayer: WasmJsUiParticleLayer
) : UiDrawLayer(
    screenDimensionsService
) {

    override var foregroundPaint: Any = Paint().apply {
        isAntiAlias = true
    }

    override var foregroundBitmap: Any? by mutableStateOf(null)

    @Composable
    override fun ForegroundLayerCanvas(image: Any?) {
        val screenDimensions = screenDimensionsService.getScreenDimensions()
        var overlayInitialized by remember { mutableStateOf(false) }
        if (!overlayInitialized) {
            particleLayer.initializeOverlayCanvas(
                expectedWidth = screenDimensions.width,
                expectedHeight = screenDimensions.height
            )
            overlayInitialized = true
        }
        Canvas(
            modifier = Modifier.fillMaxSize()
                .border(width = 1.dp, color = Color.White, shape = RectangleShape)
                .clip(RectangleShape)
        ) {
            image?.let { foreground ->
                drawIntoCanvas { canvas ->
                    canvas.skiaCanvas.drawImageRect(
                        image = foreground as SkiaImage,
                        src = Rect.makeXYWH(0f, 0f, foreground.width.toFloat(), foreground.height.toFloat()),
                        dst = Rect.makeXYWH(
                            0f,
                            0f,
                            foreground.width.toFloat() * density,
                            foreground.height.toFloat() * density
                        ),
                        samplingMode = SamplingMode.LINEAR,
                        paint = foregroundPaint as Paint,
                        strict = true
                    )
                }
            }
        }
    }

    override fun drawForeground(image: CommonImage) {
        (foregroundBitmap as SkiaImage?)?.close()
        foregroundBitmap = (image as WasmJsImage).image
    }

    override fun clearAllLayers() {
        val existingForeground = foregroundBitmap as SkiaImage?
        super.clearAllLayers()
        existingForeground?.close()
    }

}
