package com.github.adamyork.kparticles.android.service

import android.graphics.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.github.adamyork.kotlin_particles.generated.resources.Res
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.service.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.service.LoadingProgressListener
import com.github.adamyork.kparticles.platform.service.data.ImageAndBytes
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject


/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class AndroidAssetService(
    httpClient: HttpClient,
) : AbstractPlatformAssetService(httpClient) {

    override suspend fun initialize(listener: LoadingProgressListener) {
        val bytes = withContext(Dispatchers.IO) {
            Res.readBytes("files/application.yml")
        }
        finishInit(bytes = bytes, listener = listener)
    }

    override suspend fun loadBufferedImageAsync(file: String): ImageBitmap {
        logger.debug { "HTTP GET: $file" }
        val response = httpClient.get(file)
        check(response.status.isSuccess()) { "Failed to load image from URL: $file (status=${response.status})" }
        val bytes = response.body<ByteArray>()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode byte array into Android Bitmap")
        return bitmap.asImageBitmap()
    }


    override suspend fun loadParticleShader(): String {
        val bytes = withContext(Dispatchers.IO) {
            Res.readBytes("files/particles.wgsl")
        }
        val source = bytes.decodeToString()
        particleShaderSource = source
        return source
    }

    override suspend fun loadParticleGlShaders() {
        val computeBytes = withContext(Dispatchers.IO) {
            Res.readBytes("files/particles.comp.glsl")
        }
        val vertexBytes = withContext(Dispatchers.IO) {
            Res.readBytes("files/particles.vert.glsl")
        }
        val fragmentBytes = withContext(Dispatchers.IO) {
            Res.readBytes("files/particles.frag.glsl")
        }
        particleComputeShaderSource = computeBytes.decodeToString()
        particleVertexShaderSource = vertexBytes.decodeToString()
        particleFragmentShaderSource = fragmentBytes.decodeToString()
    }


    override suspend fun fetchImageAndBytes(path: String, width: Int, height: Int): ImageAsset =
        withContext(Dispatchers.IO) {
            logger.debug { "HTTP GET: $path" }
            val response = httpClient.get(path)
            val bytes = response.body<ByteArray>()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Failed to decode byte array into Android Bitmap")
            val actual = bitmap.asImageBitmap()
            ImageAsset(width, height, ImageAndBytes(bytes, actual))
        }

}
