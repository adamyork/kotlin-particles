package com.github.adamyork.particles.android.engine

import android.graphics.*
import com.github.adamyork.particles.android.engine.data.AndroidImage
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class AndroidImageRenderer(
    private val imageSource: ParticleImageSource,
    override val renderStyle: ParticleRenderStyle = ParticleRenderStyle.IMAGE
) : CpuStyleRenderer<Canvas> {

    override val drawOrder: Int = CpuDrawOrder.IMAGES
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawSpriteFrames

    private fun frameOf(particle: Particle): Int {
        val animation = particle.animation
        return if (animation != null && animation.maxFrames > 1) animation.frame else 0
    }

    private val spritePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val particleSrcRect = Rect()

    private val particleRectF = RectF()

    private fun drawSpriteFrames(drawFunctionData: DrawFunctionData<Canvas>) {
        val spriteSheet = imageSource.spriteSheet ?: return
        val generators = drawFunctionData.generators
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val spriteImage = spriteSheet.image as? AndroidImage ?: return
        val spriteBitmap: Bitmap = spriteImage.bitmap
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        val sourceWidth = spriteSheet.frameWidth.toFloat()
        val sourceHeight = spriteSheet.frameHeight.toFloat()
        for (generator in generators) {
            val particles = generator.particles
            for (particleIndex in particles.indices) {
                val particle = particles[particleIndex]
                if (!particle.cullingCheck(viewPort)) continue
                val localX = particle.transform.position.x.toFloat() - viewPortOffsetX
                val localY = particle.transform.position.y.toFloat() - viewPortOffsetY
                val frameOffsetX = frameOf(particle) * spriteSheet.frameWidth
                particleSrcRect.set(frameOffsetX, 0, frameOffsetX + spriteSheet.frameWidth, spriteSheet.frameHeight)
                particleRectF.set(
                    localX - sourceWidth / 2f,
                    localY - sourceHeight / 2f,
                    localX + sourceWidth / 2f,
                    localY + sourceHeight / 2f
                )
                canvas.drawBitmap(spriteBitmap, particleSrcRect, particleRectF, spritePaint)
            }
        }
    }

}
