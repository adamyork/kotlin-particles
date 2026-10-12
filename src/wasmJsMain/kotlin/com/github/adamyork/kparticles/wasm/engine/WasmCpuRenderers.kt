package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.ParticleDrawFunction
import com.github.adamyork.kparticles.platform.engine.Renderers
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.wasm.engine.data.WasmJsImage
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class WasmCpuRenderers : Renderers<Canvas> {

    private companion object {
        const val BUBBLE_FILL_TOP_LEFT_COLOR: Int = 0x66FFFFFF.toInt()
        const val BUBBLE_FILL_BOTTOM_RIGHT_COLOR: Int = 0x0DFFFFFF.toInt()
        const val BUBBLE_RIM_COLOR: Int = 0xCCFFFFFF.toInt()
        const val BUBBLE_RIM_STROKE_WIDTH: Float = 2f
        const val BUBBLE_HIGHLIGHT_COLOR: Int = 0xE6FFFFFF.toInt()
        const val BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR: Int = 0x00FFFFFF
        const val BUBBLE_HIGHLIGHT_RADIUS_RATIO: Float = 0.28f
        const val BUBBLE_HIGHLIGHT_OFFSET_RATIO: Float = 0.38f
    }

    private val particlePaint = Paint().apply { isAntiAlias = false; mode = PaintMode.FILL }
    private val spritePaint = Paint().apply { isAntiAlias = true }
    private val projectileBlobPaint = Paint().apply { isAntiAlias = true; mode = PaintMode.FILL }
    private val bubbleFillPaint = Paint().apply { isAntiAlias = true }
    private val bubbleRimPaint = Paint().apply {
        isAntiAlias = true
        mode = PaintMode.STROKE
        color = BUBBLE_RIM_COLOR
        strokeWidth = BUBBLE_RIM_STROKE_WIDTH
    }
    private val bubbleHighlightPaint = Paint().apply { isAntiAlias = true }

    private val simpleShapeDrawFunction: ParticleDrawFunction<Canvas> = ::drawSimpleShapes
    private val complexShapeDrawFunction: ParticleDrawFunction<Canvas> = ::drawComplexShapes
    private val spriteDrawFunction: ParticleDrawFunction<Canvas> = ::drawSprites
    private val animatedSpriteDrawFunction: ParticleDrawFunction<Canvas> = ::drawAnimatedSprites

    override fun getForType(renderType: RenderType): ParticleDrawFunction<Canvas> = when (renderType) {
        RenderType.SIMPLE_SHAPE -> simpleShapeDrawFunction
        RenderType.COMPLEX_SHAPE -> complexShapeDrawFunction
        RenderType.SPRITE -> spriteDrawFunction
        RenderType.ANIMATED_SPRITE -> animatedSpriteDrawFunction
    }

    private fun drawSimpleShapes(drawFunctionData: DrawFunctionData<Canvas>) {
        val particles = drawFunctionData.particles
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        val groups = HashMap<Int, MutableList<Particle>>(16)
        var particleIndex = 0
        while (particleIndex < particles.size) {
            val particle = particles[particleIndex]
            val alpha = (particle.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
            val color = Color.makeARGB(
                alpha,
                (particle.color.red * 255).toInt(),
                (particle.color.green * 255).toInt(),
                (particle.color.blue * 255).toInt()
            )
            val bucket = groups[color]
            if (bucket == null) {
                groups[color] = arrayListOf(particle)
            } else {
                bucket.add(particle)
            }
            particleIndex++
        }
        val groupIterator = groups.entries.iterator()
        while (groupIterator.hasNext()) {
            val entry = groupIterator.next()
            val particleList = entry.value
            val builder = PathBuilder()
            var groupParticleIndex = 0
            while (groupParticleIndex < particleList.size) {
                val particle = particleList[groupParticleIndex]
                val x = particle.x.toFloat() - viewPortOffsetX
                val y = particle.y.toFloat() - viewPortOffsetY
                if (particle.shape == ParticleShape.CIRCLE) {
                    val radius = particle.radius.toFloat()
                    val diameter = radius * 2f
                    builder.addOval(Rect.makeXYWH(x - radius, y - radius, diameter, diameter))
                } else {
                    val width = particle.width.toFloat()
                    val height = particle.height.toFloat()
                    builder.addRect(Rect.makeXYWH(x - width / 2f, y - height / 2f, width, height))
                }
                groupParticleIndex++
            }
            val batchPath = builder.detach()
            particlePaint.color = entry.key
            canvas.drawPath(batchPath, particlePaint)
            batchPath.close()
        }
    }

    private fun drawComplexShapes(drawFunctionData: DrawFunctionData<Canvas>) {
        val particles = drawFunctionData.particles
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        var particleIndex = 0
        while (particleIndex < particles.size) {
            val particle = particles[particleIndex]
            when (particle.effect) {
                ParticleEffect.BUBBLE -> if (particle.visible) {
                    drawBubble(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                }

                ParticleEffect.BLOB_PROJECTILE -> drawProjectileBlob(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                else -> Unit
            }
            particleIndex++
        }
    }

    private fun drawSprites(drawFunctionData: DrawFunctionData<Canvas>) {
        drawSpriteFrames(drawFunctionData, false)
    }

    private fun drawAnimatedSprites(drawFunctionData: DrawFunctionData<Canvas>) {
        drawSpriteFrames(drawFunctionData, true)
    }

    private fun drawSpriteFrames(drawFunctionData: DrawFunctionData<Canvas>, animated: Boolean) {
        val spriteSheet = drawFunctionData.spriteSheet ?: return
        val particles = drawFunctionData.particles
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val spriteImage = (spriteSheet.image as WasmJsImage).image
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        val sourceWidth = spriteSheet.frameWidth.toFloat()
        val sourceHeight = spriteSheet.frameHeight.toFloat()
        var particleIndex = 0
        while (particleIndex < particles.size) {
            val particle = particles[particleIndex]
            val localX = particle.x.toFloat() - viewPortOffsetX
            val localY = particle.y.toFloat() - viewPortOffsetY
            val frameOffsetX = (if (animated) particle.frame else 0) * sourceWidth
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
            particleIndex++
        }
    }

    private fun drawBubble(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val centerX = particle.x.toFloat() - viewPortOffsetX
        val centerY = particle.y.toFloat() - viewPortOffsetY
        val radius = particle.radius.toFloat()
        val fillShader = Shader.makeLinearGradient(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            intArrayOf(BUBBLE_FILL_TOP_LEFT_COLOR, BUBBLE_FILL_BOTTOM_RIGHT_COLOR)
        )
        try {
            bubbleFillPaint.shader = fillShader
            canvas.drawCircle(centerX, centerY, radius, bubbleFillPaint)
        } finally {
            fillShader.close()
        }
        val rimRadius = radius - (BUBBLE_RIM_STROKE_WIDTH / 2f)
        canvas.drawCircle(centerX, centerY, rimRadius, bubbleRimPaint)
        val highlightRadius = radius * BUBBLE_HIGHLIGHT_RADIUS_RATIO
        val highlightOffset = radius * BUBBLE_HIGHLIGHT_OFFSET_RATIO
        val highlightCenterX = centerX - highlightOffset
        val highlightCenterY = centerY - highlightOffset
        val highlightShader = Shader.makeRadialGradient(
            highlightCenterX,
            highlightCenterY,
            highlightRadius,
            intArrayOf(BUBBLE_HIGHLIGHT_COLOR, BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR)
        )
        try {
            bubbleHighlightPaint.shader = highlightShader
            canvas.drawCircle(highlightCenterX, highlightCenterY, highlightRadius, bubbleHighlightPaint)
        } finally {
            highlightShader.close()
        }
    }

    private fun drawProjectileBlob(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val centerX = particle.x.toFloat() - viewPortOffsetX
        val centerY = particle.y.toFloat() - viewPortOffsetY
        val baseRadius = particle.radius.toFloat()
        val age = particle.age.toFloat()
        val lobePointXs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
        val lobePointYs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
        var lobeIndex = 0
        while (lobeIndex < BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT) {
            val lobeAngle =
                (lobeIndex.toFloat() / BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT.toFloat()) * (2f * PI.toFloat())
            val lobeFrequency =
                BlobProjectileGenerator.PROJECTILE_BLOB_BASE_FREQUENCY + (lobeIndex * BlobProjectileGenerator.PROJECTILE_BLOB_FREQUENCY_STEP)
            val lobePhase = lobeIndex * BlobProjectileGenerator.PROJECTILE_BLOB_PHASE_STEP
            val lobeWobble = sin((age * lobeFrequency) + lobePhase)
            val lobeRadius = baseRadius * (1f + (BlobProjectileGenerator.PROJECTILE_BLOB_AMPLITUDE_RATIO * lobeWobble))
            lobePointXs[lobeIndex] = centerX + (cos(lobeAngle) * lobeRadius)
            lobePointYs[lobeIndex] = centerY + (sin(lobeAngle) * lobeRadius)
            lobeIndex++
        }
        val blobPath = buildSmoothClosedBlobPath(lobePointXs, lobePointYs)
        val alpha = (particle.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
        projectileBlobPaint.color = Color.makeARGB(
            alpha,
            (particle.color.red * 255).toInt(),
            (particle.color.green * 255).toInt(),
            (particle.color.blue * 255).toInt()
        )
        canvas.drawPath(blobPath, projectileBlobPaint)
        blobPath.close()
    }

    private fun buildSmoothClosedBlobPath(lobePointXs: FloatArray, lobePointYs: FloatArray): Path {
        val builder = PathBuilder()
        val lobeCount = lobePointXs.size
        builder.moveTo(lobePointXs[0], lobePointYs[0])
        var lobeIndex = 0
        while (lobeIndex < lobeCount) {
            val previousIndex = (lobeIndex - 1 + lobeCount) % lobeCount
            val nextIndex = (lobeIndex + 1) % lobeCount
            val afterNextIndex = (lobeIndex + 2) % lobeCount
            val controlPoint1X =
                lobePointXs[lobeIndex] + (lobePointXs[nextIndex] - lobePointXs[previousIndex]) / BlobProjectileGenerator.PROJECTILE_BLOB_SPLINE_TENSION
            val controlPoint1Y =
                lobePointYs[lobeIndex] + (lobePointYs[nextIndex] - lobePointYs[previousIndex]) / BlobProjectileGenerator.PROJECTILE_BLOB_SPLINE_TENSION
            val controlPoint2X =
                lobePointXs[nextIndex] - (lobePointXs[afterNextIndex] - lobePointXs[lobeIndex]) / BlobProjectileGenerator.PROJECTILE_BLOB_SPLINE_TENSION
            val controlPoint2Y =
                lobePointYs[nextIndex] - (lobePointYs[afterNextIndex] - lobePointYs[lobeIndex]) / BlobProjectileGenerator.PROJECTILE_BLOB_SPLINE_TENSION
            builder.cubicTo(
                controlPoint1X,
                controlPoint1Y,
                controlPoint2X,
                controlPoint2Y,
                lobePointXs[nextIndex],
                lobePointYs[nextIndex]
            )
            lobeIndex++
        }
        builder.closePath()
        return builder.detach()
    }

}
