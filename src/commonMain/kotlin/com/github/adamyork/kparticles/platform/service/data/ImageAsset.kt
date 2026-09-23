package com.github.adamyork.kparticles.platform.service.data


/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class ImageAsset(val width: Int, val height: Int, val imageAndBytes: ImageAndBytes) {
	companion object {
		fun getTmpImageAsset(): ImageAsset =
			ImageAsset(1, 1, ImageAndBytes.getTmpImageAndBytes())
	}
}
