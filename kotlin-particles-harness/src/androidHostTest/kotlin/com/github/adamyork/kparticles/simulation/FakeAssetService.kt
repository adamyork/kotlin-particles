package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.AppProperties
import com.github.adamyork.kparticles.platform.ColorWrapper
import com.github.adamyork.kparticles.platform.EngineConfig
import com.github.adamyork.kparticles.platform.ItemConfig
import com.github.adamyork.kparticles.platform.MapConfig
import com.github.adamyork.kparticles.platform.MovementCollision
import com.github.adamyork.kparticles.platform.ParticleConfig
import com.github.adamyork.kparticles.platform.Projectile
import com.github.adamyork.kparticles.platform.RGBA
import com.github.adamyork.kparticles.platform.ViewportConfig
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.kparticles.platform.assets.LoadingProgressListener
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class FakeAssetService : AssetService {

    companion object {
        const val TICKS_PER_SECOND: Int = 50
    }

    override var applicationYamlFile: String = ""

    override var appProperties: AppProperties = AppProperties(
        engine = EngineConfig(tickTargetPerSec = TICKS_PER_SECOND, spriteAnimationFramePerSec = 12),
        viewport = ViewportConfig(x = 0, y = 768, width = 1024, height = 768),
        map = MapConfig(item = ItemConfig(asset = emptyMap(), position = emptyMap())),
        particle = ParticleConfig(
            player = MovementCollision(
                movement = ColorWrapper(RGBA(230, 234, 218, 255)),
                collision = ColorWrapper(RGBA(255, 120, 40, 255))
            ),
            enemy = Projectile(projectile = ColorWrapper(RGBA(200, 40, 40, 255)))
        )
    )

    override var particleShaderSource: String = ""

    override var particleComputeShaderSource: String = ""

    override var particleVertexShaderSource: String = ""

    override var particleFragmentShaderSource: String = ""

    override suspend fun initialize(listener: LoadingProgressListener) {
    }

    override suspend fun loadItem(id: Int): ImageAsset {
        throw UnsupportedOperationException("items are not available in simulation tests")
    }

    override suspend fun loadParticleShader(): String = ""

    override suspend fun loadParticleGlShaders() {
    }

}
