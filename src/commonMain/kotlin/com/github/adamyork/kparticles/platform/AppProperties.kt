package com.github.adamyork.kparticles.platform

import kotlinx.serialization.Serializable

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Serializable
data class AppProperties(
    val engine: EngineConfig,
    val viewport: ViewportConfig,
    val map: MapConfig,
    val particle: ParticleConfig
)

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Serializable
data class EngineConfig(
    val tickTargetPerSec: Int,
    val spriteAnimationFramePerSec: Int = 12
)

@Serializable
data class ViewportConfig(val x: Int, val y: Int, val width: Int, val height: Int)

@Serializable
data class MapConfig(val item: ItemConfig)

@Serializable
data class ItemConfig(
    val asset: Map<String, AssetDimensions>,
    private val position: Map<String, ItemPosition>
) {
    val positions: List<ItemPosition> by lazy {
        position.entries.sortedBy { it.key.toInt() }.map { it.value }
    }
}

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Serializable
data class AssetDimensions(val width: Int, val height: Int, val path: String)

@Serializable
data class ItemPosition(val x: Int, val y: Int, val type: String, val ref: String)

@Serializable
data class ParticleConfig(val player: MovementCollision, val enemy: Projectile)

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Serializable
data class MovementCollision(val movement: ColorWrapper, val collision: ColorWrapper)

@Serializable
data class ColorWrapper(val color: RGBA)

@Serializable
data class RGBA(val r: Int, val g: Int, val b: Int, val a: Int)

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Serializable
data class Projectile(val projectile: ColorWrapper)

