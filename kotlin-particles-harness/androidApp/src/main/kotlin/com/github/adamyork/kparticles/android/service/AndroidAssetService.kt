package com.github.adamyork.kparticles.android.service

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.github.adamyork.kotlin_particles.generated.resources.Res
import com.github.adamyork.particles.resources.Res as CoreRes
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.assets.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.assets.LoadingProgressListener
import com.github.adamyork.kparticles.platform.assets.data.ImageAndBytes
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject


/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AndroidAssetService(
    httpClient: HttpClient
) : AbstractPlatformAssetService(httpClient) {

    override suspend fun initialize(listener: LoadingProgressListener) {
        val bytes = withContext(Dispatchers.IO) {
            Res.readBytes("files/application.yml")
        }
        finishInit(bytes = bytes, listener = listener)
    }

    override suspend fun loadParticleShader(): String {
        val bytes = withContext(Dispatchers.IO) {
            CoreRes.readBytes("files/particles.wgsl")
        }
        val source = bytes.decodeToString()
        particleShaderSource = source
        return source
    }

    override suspend fun loadParticleGlShaders() {
        val computeBytes = withContext(Dispatchers.IO) {
            CoreRes.readBytes("files/particles.comp.glsl")
        }
        val vertexBytes = withContext(Dispatchers.IO) {
            CoreRes.readBytes("files/particles.vert.glsl")
        }
        val fragmentBytes = withContext(Dispatchers.IO) {
            CoreRes.readBytes("files/particles.frag.glsl")
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
