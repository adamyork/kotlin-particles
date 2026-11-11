package com.github.adamyork.kparticles.simulation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class FakeImageBitmap(
    override val width: Int,
    override val height: Int
) : ImageBitmap {

    private companion object {
        const val CORNER_SIZE: Int = 4
        const val CHANNEL_MAXIMUM: Int = 255
        const val CHANNEL_MASK: Int = 0xFF
        const val OPAQUE_ALPHA: Int = 0xFF
        const val ALPHA_SHIFT: Int = 24
        const val RED_SHIFT: Int = 16
        const val GREEN_SHIFT: Int = 8
    }

    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

    override val colorSpace: ColorSpace = ColorSpaces.Srgb

    override val hasAlpha: Boolean = true

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int
    ) {
        for (row in 0 until height) {
            for (column in 0 until width) {
                buffer[bufferOffset + row * stride + column] = pixelAt(startX + column, startY + row)
            }
        }
    }

    override fun prepareToDraw() = Unit

    private fun pixelAt(column: Int, row: Int): Int {
        val isTransparentCorner = (column < CORNER_SIZE || column >= width - CORNER_SIZE) &&
            (row < CORNER_SIZE || row >= height - CORNER_SIZE)
        if (isTransparentCorner) return 0
        val red = (column * CHANNEL_MAXIMUM / width) and CHANNEL_MASK
        val green = (row * CHANNEL_MAXIMUM / height) and CHANNEL_MASK
        val blue = ((column + row) * CHANNEL_MAXIMUM / (width + height)) and CHANNEL_MASK
        return (OPAQUE_ALPHA shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }
}
