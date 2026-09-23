package com.github.adamyork.kparticles.platform.service.data

import androidx.compose.ui.graphics.ImageBitmap
import com.github.adamyork.kparticles.platform.service.AbstractPlatformAssetService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ImageAndBytes(val bytes: ByteArray, val imageBitmap: ImageBitmap) {

    companion object {
        fun getTmpImageAndBytes(): ImageAndBytes =
            ImageAndBytes(byteArrayOf(), AbstractPlatformAssetService.getTmpImageBitmap())
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is ImageAndBytes &&
                bytes.contentEquals(other.bytes) &&
                imageBitmap == other.imageBitmap
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + imageBitmap.hashCode()
        return result
    }

}
