package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.effects.style.DripStyleSettings
import com.github.adamyork.kparticles.platform.effects.style.EffectStyleShaders
import com.github.adamyork.particles.engine.data.CommonImage
import com.github.adamyork.particles.engine.data.ParticleImageSource
import com.github.adamyork.particles.engine.data.ParticleSpriteSheet
import com.github.adamyork.particles.engine.shader.BuiltInStyleShaders
import com.github.adamyork.particles.engine.shader.GpuStyleShader

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
object TestStyleShaders {

    const val FRAME_WIDTH: Int = 21
    const val FRAME_HEIGHT: Int = 33
    const val FRAME_COUNT: Int = 4

    fun imageSource(): ParticleImageSource = ParticleImageSource().apply {
        spriteSheet = ParticleSpriteSheet(object : CommonImage {}, ByteArray(0), FRAME_WIDTH, FRAME_HEIGHT, FRAME_COUNT)
    }

    fun create(
        imageSource: ParticleImageSource = imageSource(),
        dripStyleSettings: DripStyleSettings = DripStyleSettings()
    ): List<GpuStyleShader> =
        listOf(BuiltInStyleShaders.SHAPE, BuiltInStyleShaders.image(imageSource)) + EffectStyleShaders.create(dripStyleSettings)

}
