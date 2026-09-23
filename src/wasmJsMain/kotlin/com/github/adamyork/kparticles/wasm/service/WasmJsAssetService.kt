package com.github.adamyork.kparticles.wasm.service

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.service.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.service.data.ImageAndBytes
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.Image


/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class WasmJsAssetService(
    httpClient: HttpClient
) : AbstractPlatformAssetService(httpClient) {

    override suspend fun loadBufferedImageAsync(file: String): ImageBitmap {
        logger.debug { "HTTP GET: $file" }
        val response = httpClient.get(file)
        check(response.status.isSuccess()) { "Failed to load image from URL: $file (status=${response.status})" }
        val skiaImage = Image.makeFromEncoded(response.body<ByteArray>())
        try {
            return skiaImage.toComposeImageBitmap()
        } finally {
            skiaImage.close()
        }
    }

    override suspend fun loadParticleShader(): String {
        logger.debug { "HTTP GET: particles.wgsl" }
        val response = httpClient.get("particles.wgsl")
        check(response.status.isSuccess()) { "Failed to load particle shader (status=${response.status})" }
        val source = response.body<ByteArray>().decodeToString()
        particleShaderSource = source
        return source
    }


    override suspend fun fetchImageAndBytes(path: String, width: Int, height: Int): ImageAsset {
        logger.debug { "HTTP GET: $path" }
        val response = httpClient.get(path)
        val bytes = response.body<ByteArray>()
        val skiaImage = Image.makeFromEncoded(bytes)
        try {
            val bitmap = skiaImage.toComposeImageBitmap()
            return ImageAsset(width, height, ImageAndBytes(bytes, bitmap))
        } finally {
            skiaImage.close()
        }
    }

}
