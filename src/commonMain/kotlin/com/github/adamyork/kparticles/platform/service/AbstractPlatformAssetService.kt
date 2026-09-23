package com.github.adamyork.kparticles.platform.service

import androidx.compose.ui.graphics.Color
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

abstract class AbstractPlatformAssetService(
    protected val httpClient: HttpClient
) : AssetService {

    companion object {
        fun getTmpImageBitmap(): ImageBitmap = ImageBitmap(1, 1)
        private val COLOR_MAP = mapOf(
            "green" to Color.Green, "white" to Color.White, "blue" to Color.Blue,
            "darkgray" to Color.DarkGray, "red" to Color.Red, "gray" to Color.Gray,
            "lightgray" to Color.LightGray, "yellow" to Color.Yellow,
            "magenta" to Color.Magenta, "black" to Color.Black
        )
    }

    protected val logger = KotlinLogging.logger {}

    override lateinit var appProperties: AppProperties
    override lateinit var applicationYamlFile: String
    override var particleShaderSource: String = ""
    override var particleComputeShaderSource: String = ""
    override var particleVertexShaderSource: String = ""
    override var particleFragmentShaderSource: String = ""

    protected var itemInfoMap: HashMap<Int, MapElementYamlEntry> = HashMap()

    protected abstract suspend fun fetchImageAndBytes(path: String, width: Int, height: Int): ImageAsset

    override suspend fun loadParticleGlShaders() {
        // Default no-op for platforms that do not use the OpenGL ES shader triplet.
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
