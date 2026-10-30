package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class SpriteExplodeBackdropGenerator : AbstractGenerator(ParticleEffect.SPRITE_EXPLODE_BACKDROP) {

    private companion object {
        private const val BACKDROP_LIFETIME: Double = 120.0
        private const val BACKDROP_DENSITY: Double = 1.0
        private const val BACKDROP_MAX_ACTIVE_PARTICLES: Int = 8
    }

    private val backdropGpuSlotGroup: GpuSlotGroup = GpuSlotGroup(BACKDROP_MAX_ACTIVE_PARTICLES)

    override val gpuSlotGroups: List<GpuSlotGroup> = listOf(backdropGpuSlotGroup)

    private var imageWidth: Int = 0

    private var imageHeight: Int = 0

    override fun populateItemImage(itemImage: ImageAsset) {
        imageWidth = itemImage.width
        imageHeight = itemImage.height
    }

    override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        if (imageWidth == 0 || imageHeight == 0) return mutableListOf()
        return mutableListOf(
            Particle(
                lifecycle = ParticleLifecycle(lifetime = BACKDROP_LIFETIME, pinnedUntil = BACKDROP_LIFETIME),
                appearance = ParticleAppearance(color = Color.White),
                dimensions = RectDimensions(width = imageWidth.toDouble(), height = imageHeight.toDouble()),
                transform = ParticleTransform(
                    position = ParticleVector(request.x, request.y),
                    origin = ParticleVector(request.x, request.y)
                ),
                material = ParticleMaterial(density = BACKDROP_DENSITY, exertsGravity = false),
                animation = ParticleAnimation(maxFrames = 1),
                id = nextParticleId()
            )
        )
    }

    override fun gpuSlotGroup(particle: Particle): GpuSlotGroup = backdropGpuSlotGroup
}
