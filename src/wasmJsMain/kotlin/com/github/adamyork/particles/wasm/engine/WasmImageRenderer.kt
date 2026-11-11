package com.github.adamyork.particles.wasm.engine

import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import com.github.adamyork.particles.wasm.engine.data.WasmJsImage
import org.jetbrains.skia.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class WasmImageRenderer(
    private val imageSource: ParticleImageSource,
    override val renderStyle: ParticleRenderStyle = ParticleRenderStyle.IMAGE
) : CpuStyleRenderer<Canvas> {

    override val drawOrder: Int = CpuDrawOrder.IMAGES
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawSpriteFrames

    private fun frameOf(particle: Particle): Int {
        val animation = particle.animation
        return if (animation != null && animation.maxFrames > 1) animation.frame else 0
    }

    private val spritePaint = Paint().apply { isAntiAlias = true }

    private fun drawSpriteFrames(drawFunctionData: DrawFunctionData<Canvas>) {
        val spriteSheet = imageSource.spriteSheet ?: return
        val generators = drawFunctionData.generators
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val spriteImage = (spriteSheet.image as WasmJsImage).image
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        val sourceWidth = spriteSheet.frameWidth.toFloat()
        val sourceHeight = spriteSheet.frameHeight.toFloat()
        for (generator in generators) {
            val particles = generator.particles
            var particleIndex = 0
            while (particleIndex < particles.size) {
                val particle = particles[particleIndex]
                particleIndex++
                if (!particle.cullingCheck(viewPort)) continue
                val localX = particle.transform.position.x.toFloat() - viewPortOffsetX
                val localY = particle.transform.position.y.toFloat() - viewPortOffsetY
                val frameOffsetX = frameOf(particle) * sourceWidth
                canvas.drawImageRect(
                    image = spriteImage,
                    srcLeft = frameOffsetX,
                    srcTop = 0f,
                    srcRight = frameOffsetX + sourceWidth,
                    srcBottom = sourceHeight,
                    dstLeft = localX - sourceWidth / 2f,
                    dstTop = localY - sourceHeight / 2f,
                    dstRight = localX + sourceWidth / 2f,
                    dstBottom = localY + sourceHeight / 2f,
                    samplingMode = SamplingMode.LINEAR,
                    paint = spritePaint,
                    strict = true
                )
            }
        }
    }

}
