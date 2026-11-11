package com.github.adamyork.kparticles.android.effects

import android.graphics.*
import androidx.core.graphics.createBitmap
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.style.CpuRendererConstants
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import kotlin.math.*
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AndroidBubbleRenderer : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = EffectStyles.BUBBLE
    override val drawOrder: Int = CpuDrawOrder.COMPLEX_SHAPES
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawBubbles
    private fun drawBubbles(drawFunctionData: DrawFunctionData<Canvas>) {
        warmUpBubbleBitmaps()
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in drawFunctionData.generators) {
            val particles = generator.particles
            for (particleIndex in particles.indices) {
                val particle = particles[particleIndex]
                if (!particle.cullingCheck(viewPort)) continue
                if (particle.lifecycle.isVisible()) {
                    drawBubble(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                }
            }
        }
    }

    private val spritePaint: Paint = Paint().apply {
        isFilterBitmap = false
    }

    private val bubbleFillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bubbleRimPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = CpuRendererConstants.BUBBLE_RIM_STROKE_WIDTH
        color = CpuRendererConstants.BUBBLE_RIM_COLOR
    }

    private val bubbleHighlightPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bubbleBitmaps = HashMap<Int, Bitmap>()

    private var nextWarmupRadius = 1

    private fun warmUpBubbleBitmaps() {
        var createdBitmapCount = 0
        while (createdBitmapCount < CpuRendererConstants.BUBBLE_WARMUP_IMAGES_PER_FRAME &&
            nextWarmupRadius <= CpuRendererConstants.BUBBLE_WARMUP_MAXIMUM_RADIUS
        ) {
            if (!bubbleBitmaps.containsKey(nextWarmupRadius)) {
                bubbleBitmaps[nextWarmupRadius] = createBubbleBitmap(nextWarmupRadius.toFloat())
                createdBitmapCount++
            }
            nextWarmupRadius++
        }
    }

    private fun drawBubble(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val centerX = particle.transform.position.x.toFloat() - viewPortOffsetX
        val centerY = particle.transform.position.y.toFloat() - viewPortOffsetY
        val radiusKey = particle.collisionRadius().roundToInt().coerceAtLeast(1)
        val bubbleBitmap = bubbleBitmaps[radiusKey] ?: createBubbleBitmap(radiusKey.toFloat()).also { createdBitmap ->
            if (bubbleBitmaps.size >= CpuRendererConstants.BUBBLE_IMAGE_CACHE_LIMIT) {
                bubbleBitmaps.values.forEach { cachedBitmap -> cachedBitmap.recycle() }
                bubbleBitmaps.clear()
            }
            bubbleBitmaps[radiusKey] = createdBitmap
        }
        canvas.drawBitmap(
            bubbleBitmap,
            round(centerX - bubbleBitmap.width / 2f),
            round(centerY - bubbleBitmap.height / 2f),
            spritePaint
        )
    }

    private fun createBubbleBitmap(radius: Float): Bitmap {
        val size = ceil(radius * 2f).toInt() + CpuRendererConstants.BUBBLE_IMAGE_PADDING
        val center = size / 2f
        val bitmap = createBitmap(size, size)
        val bitmapCanvas = Canvas(bitmap)
        bubbleFillPaint.shader = LinearGradient(
            center - radius,
            center - radius,
            center + radius,
            center + radius,
            CpuRendererConstants.BUBBLE_FILL_TOP_LEFT_COLOR,
            CpuRendererConstants.BUBBLE_FILL_BOTTOM_RIGHT_COLOR,
            Shader.TileMode.CLAMP
        )
        bitmapCanvas.drawCircle(center, center, radius, bubbleFillPaint)
        bubbleFillPaint.shader = null
        bitmapCanvas.drawCircle(center, center, radius - (CpuRendererConstants.BUBBLE_RIM_STROKE_WIDTH / 2f), bubbleRimPaint)
        val highlightRadius = radius * CpuRendererConstants.BUBBLE_HIGHLIGHT_RADIUS_RATIO
        val highlightOffset = radius * CpuRendererConstants.BUBBLE_HIGHLIGHT_OFFSET_RATIO
        bubbleHighlightPaint.shader = RadialGradient(
            center - highlightOffset,
            center - highlightOffset,
            highlightRadius,
            CpuRendererConstants.BUBBLE_HIGHLIGHT_COLOR,
            CpuRendererConstants.BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR,
            Shader.TileMode.CLAMP
        )
        bitmapCanvas.drawCircle(
            center - highlightOffset,
            center - highlightOffset,
            highlightRadius,
            bubbleHighlightPaint
        )
        bubbleHighlightPaint.shader = null
        return bitmap
    }

}
