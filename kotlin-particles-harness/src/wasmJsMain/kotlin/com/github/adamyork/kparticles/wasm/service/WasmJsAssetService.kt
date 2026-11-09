package com.github.adamyork.kparticles.wasm.service

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.resources.Res as CoreRes
import com.github.adamyork.kparticles.platform.assets.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.assets.data.ImageAndBytes
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.Image

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */

@ParticleScope
@Inject
class WasmJsAssetService(
    httpClient: HttpClient
) : AbstractPlatformAssetService(httpClient) {

    override suspend fun loadParticleShader(): String {
        val source = CoreRes.readBytes("files/particles.wgsl").decodeToString()
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
