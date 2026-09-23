package com.github.adamyork.kparticles.platform.service

import androidx.compose.ui.graphics.ImageBitmap
import com.github.adamyork.kparticles.platform.AppProperties
import com.github.adamyork.kparticles.platform.service.data.ImageAsset

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface AssetService {

    var applicationYamlFile: String
    var appProperties: AppProperties
    var particleShaderSource: String
    var particleComputeShaderSource: String
    var particleVertexShaderSource: String
    var particleFragmentShaderSource: String

    suspend fun initialize(listener: LoadingProgressListener)

    suspend fun loadBufferedImageAsync(file: String): ImageBitmap

    suspend fun loadItem(id: Int): ImageAsset

    suspend fun loadParticleShader(): String

    suspend fun loadParticleGlShaders()


}
