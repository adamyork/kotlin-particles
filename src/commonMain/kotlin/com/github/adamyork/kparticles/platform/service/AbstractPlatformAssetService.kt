package com.github.adamyork.kparticles.platform.service

import androidx.compose.ui.graphics.ImageBitmap
import com.charleskorn.kaml.Yaml
import com.github.adamyork.kparticles.platform.AppProperties
import com.github.adamyork.kparticles.platform.service.data.AssetServiceReferenceException
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import com.github.adamyork.kparticles.platform.service.data.MapElementYamlEntry
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractPlatformAssetService(
    protected val httpClient: HttpClient,
    private val physicsSettingsService: PhysicsSettingsService
) : AssetService {

    companion object {
        fun getTmpImageBitmap(): ImageBitmap = ImageBitmap(1, 1)
    }

    protected val logger = KotlinLogging.logger {}

    override lateinit var appProperties: AppProperties
    override lateinit var applicationYamlFile: String
    override var particleShaderSource: String = ""
    override var particleComputeShaderSource: String = ""
    override var particleVertexShaderSource: String = ""
    override var particleFragmentShaderSource: String = ""

    protected var itemInfoMap: HashMap<Int, MapElementYamlEntry> = HashMap()

    protected fun injectShaderConstants(shaderSource: String): String {
        val shaderConstants = mapOf(
            "SHATTER_MINIMUM_FRAGMENT_RADIUS" to physicsSettingsService.shatterMinimumFragmentRadius,
            "SHATTER_MINIMUM_FRAGMENT_COUNT" to physicsSettingsService.shatterMinimumFragmentCount.toDouble(),
            "SHATTER_MAXIMUM_FRAGMENT_COUNT" to physicsSettingsService.shatterMaximumFragmentCount.toDouble(),
            "SHATTER_MOMENTUM_PER_FRAGMENT" to physicsSettingsService.shatterMomentumPerFragment,
            "SHATTER_SPREAD_SPEED" to physicsSettingsService.shatterSpreadSpeed,
            "SHATTER_MAXIMUM_VELOCITY" to physicsSettingsService.shatterMaxVelocity
        )
        return Regex("\\{\\{([A-Z0-9_]+)\\}\\}").replace(shaderSource) { placeholderMatch ->
            val placeholderName = placeholderMatch.groupValues[1]
            shaderConstants[placeholderName]?.toString()
                ?: throw IllegalStateException("No shader constant registered for $placeholderName")
        }
    }

    protected abstract suspend fun fetchImageAndBytes(path: String, width: Int, height: Int): ImageAsset

    override suspend fun loadParticleGlShaders() {
    }

    override suspend fun initialize(listener: LoadingProgressListener) {
        logger.debug { "HTTP GET: application.yml" }
        val response = httpClient.get("application.yml")
        check(response.status.isSuccess()) { "Failed to load application yml (status=${response.status})" }
        val bytes = response.body<ByteArray>()
        finishInit(bytes, listener)
    }

    protected suspend fun finishInit(bytes: ByteArray, listener: LoadingProgressListener) {
        listener.onTaskCompleted("app_yaml")
        withContext(Dispatchers.Default) {
            val yamlString = bytes.decodeToString()
            appProperties = Yaml.default.decodeFromString(AppProperties.serializer(), yamlString)
            itemInfoMap = appProperties.map.item.positions.mapIndexed { index, pos ->
                val dim = appProperties.map.item.asset[pos.ref]
                    ?: throw AssetServiceReferenceException("no item asset for ${pos.ref}")
                index to MapElementYamlEntry(dim.path, dim.width, dim.height, pos.x, pos.y, pos.type)
            }.toMap(HashMap())
        }
    }

    override suspend fun loadItem(id: Int): ImageAsset {
        val entry = itemInfoMap[id] ?: throw AssetServiceReferenceException("Item ID $id not found")
        return fetchImageAndBytes(entry.path, entry.width, entry.height)
    }
}
