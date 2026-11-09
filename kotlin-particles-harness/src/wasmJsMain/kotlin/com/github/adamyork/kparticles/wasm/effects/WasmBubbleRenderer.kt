package com.github.adamyork.kparticles.wasm.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.style.CpuRendererConstants
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.DiagnosticPhase
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import kotlin.math.*
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class WasmBubbleRenderer(
    private val engineDiagnostics: EngineDiagnostics
) : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = EffectStyles.BUBBLE
    override val drawOrder: Int = CpuDrawOrder.COMPLEX_SHAPES
    override val diagnosticPhase: DiagnosticPhase = DiagnosticPhase.DRAW_COMPLEX_SHAPE
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawBubbles
    private fun drawBubbles(drawFunctionData: DrawFunctionData<Canvas>) {
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in drawFunctionData.generators) {
            val generatorMark = engineDiagnostics.startTimer()
            val particles = generator.particles
            var particleIndex = 0
            while (particleIndex < particles.size) {
                val particle = particles[particleIndex]
                particleIndex++
                if (!particle.cullingCheck(viewPort)) continue
                if (particle.lifecycle.isVisible()) {
                    drawBubble(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                }
            }
            engineDiagnostics.stopEffectTimer(generator.effect, generatorMark)
        }
    }

    private val bubbleFillPaint = Paint().apply { isAntiAlias = true }

    private val bubbleRimPaint = Paint().apply {
        isAntiAlias = true
        mode = PaintMode.STROKE
        color = CpuRendererConstants.BUBBLE_RIM_COLOR
        strokeWidth = CpuRendererConstants.BUBBLE_RIM_STROKE_WIDTH
    }

    private val bubbleHighlightPaint = Paint().apply { isAntiAlias = true }

    private val bubbleBlitPaint = Paint().apply { isAntiAlias = false }

    private val bubbleImages = HashMap<Int, Image>()

    private fun drawBubble(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val centerX = particle.transform.position.x.toFloat() - viewPortOffsetX
        val centerY = particle.transform.position.y.toFloat() - viewPortOffsetY
        val radiusKey = (particle.collisionRadius().toFloat() * 2f).roundToInt()
        val bubbleImage = bubbleImages[radiusKey] ?: createBubbleImage(radiusKey / 2f).also { createdImage ->
            if (bubbleImages.size >= CpuRendererConstants.BUBBLE_IMAGE_CACHE_LIMIT) {
                bubbleImages.values.forEach { cachedImage -> cachedImage.close() }
                bubbleImages.clear()
            }
            bubbleImages[radiusKey] = createdImage
        }
        canvas.drawImageRect(
            image = bubbleImage,
            srcLeft = 0f,
            srcTop = 0f,
            srcRight = bubbleImage.width.toFloat(),
            srcBottom = bubbleImage.height.toFloat(),
            dstLeft = centerX - bubbleImage.width / 2f,
            dstTop = centerY - bubbleImage.height / 2f,
            dstRight = centerX + bubbleImage.width / 2f,
            dstBottom = centerY + bubbleImage.height / 2f,
            samplingMode = SamplingMode.LINEAR,
            paint = bubbleBlitPaint,
            strict = true
        )
    }

    private fun createBubbleImage(radius: Float): Image {
        val size = ceil(radius * 2f).toInt() + CpuRendererConstants.BUBBLE_IMAGE_PADDING
        val center = size / 2f
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(size, size))
        val canvas = surface.canvas
        val fillShader = Shader.makeLinearGradient(
            center - radius,
            center - radius,
            center + radius,
            center + radius,
            Gradient(Gradient.Colors(arrayOf(Color4f(CpuRendererConstants.BUBBLE_FILL_TOP_LEFT_COLOR), Color4f(CpuRendererConstants.BUBBLE_FILL_BOTTOM_RIGHT_COLOR)), null, FilterTileMode.CLAMP))
        )
        try {
            bubbleFillPaint.shader = fillShader
            canvas.drawCircle(center, center, radius, bubbleFillPaint)
        } finally {
            fillShader.close()
        }
        canvas.drawCircle(center, center, radius - (CpuRendererConstants.BUBBLE_RIM_STROKE_WIDTH / 2f), bubbleRimPaint)
        val highlightRadius = radius * CpuRendererConstants.BUBBLE_HIGHLIGHT_RADIUS_RATIO
        val highlightOffset = radius * CpuRendererConstants.BUBBLE_HIGHLIGHT_OFFSET_RATIO
        val highlightShader = Shader.makeRadialGradient(
            center - highlightOffset,
            center - highlightOffset,
            highlightRadius,
            Gradient(Gradient.Colors(arrayOf(Color4f(CpuRendererConstants.BUBBLE_HIGHLIGHT_COLOR), Color4f(CpuRendererConstants.BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR)), null, FilterTileMode.CLAMP))
        )
        try {
            bubbleHighlightPaint.shader = highlightShader
            canvas.drawCircle(center - highlightOffset, center - highlightOffset, highlightRadius, bubbleHighlightPaint)
        } finally {
            highlightShader.close()
        }
        val bubbleImage = surface.makeImageSnapshot()
        surface.close()
        return bubbleImage
    }

}
