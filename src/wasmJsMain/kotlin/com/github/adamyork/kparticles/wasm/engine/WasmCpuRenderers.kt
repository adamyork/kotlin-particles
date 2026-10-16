package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.ParticleDrawFunction
import com.github.adamyork.kparticles.platform.engine.Renderers
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import com.github.adamyork.kparticles.wasm.engine.data.WasmJsImage
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.*
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class WasmCpuRenderers(
    private val physicsSettingsService: PhysicsSettingsService
) : Renderers<Canvas> {

    private companion object {
        const val BUBBLE_FILL_TOP_LEFT_COLOR: Int = 0x66FFFFFF.toInt()
        const val BUBBLE_FILL_BOTTOM_RIGHT_COLOR: Int = 0x0DFFFFFF.toInt()
        const val BUBBLE_RIM_COLOR: Int = 0xCCFFFFFF.toInt()
        const val BUBBLE_RIM_STROKE_WIDTH: Float = 2f
        const val BUBBLE_HIGHLIGHT_COLOR: Int = 0xE6FFFFFF.toInt()
        const val BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR: Int = 0x00FFFFFF
        const val BUBBLE_HIGHLIGHT_RADIUS_RATIO: Float = 0.28f
        const val BUBBLE_HIGHLIGHT_OFFSET_RATIO: Float = 0.38f
        const val DRIP_MINIMUM_LENGTH: Float = 0.0001f
        const val DRIP_STRIPE_ALPHA_SCALE: Float = 0.5f
        const val DRIP_SPECULAR_FLOATS: Int = 3
        const val DRIP_VERTICAL_TOLERANCE: Float = 0.001f
        const val DRIP_SPECULAR_IMAGE_SIZE: Int = 64
        const val BUBBLE_IMAGE_PADDING: Int = 4
        const val BUBBLE_IMAGE_CACHE_LIMIT: Int = 128
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
    private val dripFillPaint = Paint().apply { isAntiAlias = true }
    private val dripGlossStripeFillPaint = Paint().apply { isAntiAlias = true }
    private val dripGlossSpecularPaint = Paint().apply { isAntiAlias = true }
    private val bubbleImages = HashMap<Int, Image>()
    private var dripSpecularBuffer = FloatArray(0)
    private var dripSpecularImage: Image? = null
    private var dripSpecularAlpha = -1

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
        val generators = drawFunctionData.generators
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        val groups = HashMap<Int, MutableList<Particle>>(16)
        for (generator in generators) {
            val particles = generator.particles
            var particleIndex = 0
            while (particleIndex < particles.size) {
                val particle = particles[particleIndex]
                particleIndex++
                if (!particle.cullingCheck(viewPort)) continue
                val alpha = (particle.appearance.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
                val color = Color.makeARGB(
                    alpha,
                    (particle.appearance.color.red * 255).toInt(),
                    (particle.appearance.color.green * 255).toInt(),
                    (particle.appearance.color.blue * 255).toInt()
                )
                val bucket = groups[color]
                if (bucket == null) {
                    groups[color] = arrayListOf(particle)
                } else {
                    bucket.add(particle)
                }
            }
        }
        val groupIterator = groups.entries.iterator()
        while (groupIterator.hasNext()) {
            val entry = groupIterator.next()
            val particleList = entry.value
            val builder = PathBuilder()
            var groupParticleIndex = 0
            while (groupParticleIndex < particleList.size) {
                val particle = particleList[groupParticleIndex]
                val x = particle.transform.position.x.toFloat() - viewPortOffsetX
                val y = particle.transform.position.y.toFloat() - viewPortOffsetY
                when (val dimensions = particle.dimensions) {
                    is CircleDimensions -> {
                        val radius = dimensions.radius.toFloat()
                        val diameter = radius * 2f
                        builder.addOval(Rect.makeXYWH(x - radius, y - radius, diameter, diameter))
                    }
                    is RectDimensions -> {
                        val width = dimensions.width.toFloat()
                        val height = dimensions.height.toFloat()
                        if (particle.transform.angle == 0.0) {
                            builder.addRect(Rect.makeXYWH(x - width / 2f, y - height / 2f, width, height))
                        } else {
                            val halfWidth = width / 2f
                            val halfHeight = height / 2f
                            val cosine = cos(particle.transform.angle).toFloat()
                            val sine = sin(particle.transform.angle).toFloat()
                            builder.moveTo(x + (-halfWidth * cosine + halfHeight * sine), y + (-halfWidth * sine - halfHeight * cosine))
                            builder.lineTo(x + (halfWidth * cosine + halfHeight * sine), y + (halfWidth * sine - halfHeight * cosine))
                            builder.lineTo(x + (halfWidth * cosine - halfHeight * sine), y + (halfWidth * sine + halfHeight * cosine))
                            builder.lineTo(x + (-halfWidth * cosine - halfHeight * sine), y + (-halfWidth * sine + halfHeight * cosine))
                            builder.closePath()
                        }
                    }
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
        val generators = drawFunctionData.generators
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in generators) {
            drawComplexShapeGenerator(generator, viewPort, viewPortOffsetX, viewPortOffsetY, canvas)
        }
    }

    private fun drawComplexShapeGenerator(
        generator: ParticleEffectGenerator,
        viewPort: ViewPort,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val particles = generator.particles
        if (generator.effect == ParticleEffect.DRIPS) {
            drawDrips(particles, viewPort, viewPortOffsetX, viewPortOffsetY, canvas)
            return
        }
        var particleIndex = 0
        while (particleIndex < particles.size) {
            val particle = particles[particleIndex]
            particleIndex++
            if (!particle.cullingCheck(viewPort)) continue
            when (generator.effect) {
                ParticleEffect.BUBBLE -> if (particle.lifecycle.isVisible()) {
                    drawBubble(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                }

                ParticleEffect.BLOB_PROJECTILE -> drawProjectileBlob(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                else -> Unit
            }
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
                val frameOffsetX = (if (animated) (particle.animation?.frame ?: 0) else 0) * sourceWidth
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
            if (bubbleImages.size >= BUBBLE_IMAGE_CACHE_LIMIT) {
                bubbleImages.values.forEach { cachedImage -> cachedImage.close() }
                bubbleImages.clear()
            }
            bubbleImages[radiusKey] = createdImage
        }
        canvas.drawImage(bubbleImage, centerX - bubbleImage.width / 2f, centerY - bubbleImage.height / 2f)
    }

    private fun createBubbleImage(radius: Float): Image {
        val size = ceil(radius * 2f).toInt() + BUBBLE_IMAGE_PADDING
        val center = size / 2f
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(size, size))
        val canvas = surface.canvas
        val fillShader = Shader.makeLinearGradient(
            center - radius,
            center - radius,
            center + radius,
            center + radius,
            intArrayOf(BUBBLE_FILL_TOP_LEFT_COLOR, BUBBLE_FILL_BOTTOM_RIGHT_COLOR)
        )
        try {
            bubbleFillPaint.shader = fillShader
            canvas.drawCircle(center, center, radius, bubbleFillPaint)
        } finally {
            fillShader.close()
        }
        canvas.drawCircle(center, center, radius - (BUBBLE_RIM_STROKE_WIDTH / 2f), bubbleRimPaint)
        val highlightRadius = radius * BUBBLE_HIGHLIGHT_RADIUS_RATIO
        val highlightOffset = radius * BUBBLE_HIGHLIGHT_OFFSET_RATIO
        val highlightShader = Shader.makeRadialGradient(
            center - highlightOffset,
            center - highlightOffset,
            highlightRadius,
            intArrayOf(BUBBLE_HIGHLIGHT_COLOR, BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR)
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

    private fun drawDrips(
        particles: List<Particle>,
        viewPort: ViewPort,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        if (dripSpecularBuffer.size < particles.size * DRIP_SPECULAR_FLOATS) {
            dripSpecularBuffer = FloatArray(particles.size * DRIP_SPECULAR_FLOATS)
        }
        val glossIntensity = physicsSettingsService.dripGlossIntensity.toFloat()
        val stripeColor =
            Color.makeARGB((glossIntensity * DRIP_STRIPE_ALPHA_SCALE * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
        val stripeBuilder = PathBuilder()
        var stripeCount = 0
        var specularCount = 0
        var particleIndex = 0
        while (particleIndex < particles.size) {
            val particle = particles[particleIndex]
            particleIndex++
            if (!particle.cullingCheck(viewPort) || !particle.lifecycle.isVisible()) continue
            val originX = particle.transform.origin.x.toFloat() - viewPortOffsetX
            val originY = particle.transform.origin.y.toFloat() - viewPortOffsetY
            val headX = particle.transform.position.x.toFloat() - viewPortOffsetX
            val headY = particle.transform.position.y.toFloat() - viewPortOffsetY
            val beadRadius = particle.collisionRadius().toFloat()
            val trailRadius = beadRadius * physicsSettingsService.dripTrailRadiusRatio.toFloat()
            val streakLength = hypot(headX - originX, headY - originY)
            val directionX = if (streakLength > DRIP_MINIMUM_LENGTH) (headX - originX) / streakLength else 0f
            val directionY = if (streakLength > DRIP_MINIMUM_LENGTH) (headY - originY) / streakLength else 1f
            val perpendicularX = -directionY
            val neckSetting = max(beadRadius * physicsSettingsService.dripNeckLengthRatio.toFloat(), DRIP_MINIMUM_LENGTH)
            val neckLength = min(streakLength, neckSetting)
            val tailRadius = beadRadius + (trailRadius - beadRadius) * (streakLength / neckSetting).coerceIn(0f, 1f)
            val neckStartX = headX - directionX * neckLength
            val neckStartY = headY - directionY * neckLength
            val isVertical = abs(directionX) <= DRIP_VERTICAL_TOLERANCE
            dripFillPaint.color = Color.makeARGB(
                (particle.appearance.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255),
                (particle.appearance.color.red * 255).toInt().coerceIn(0, 255),
                (particle.appearance.color.green * 255).toInt().coerceIn(0, 255),
                (particle.appearance.color.blue * 255).toInt().coerceIn(0, 255)
            )
            if (isVertical) {
                canvas.drawRect(
                    Rect.makeLTRB(originX - tailRadius, min(originY, neckStartY), originX + tailRadius, max(originY, neckStartY)),
                    dripFillPaint
                )
                canvas.drawCircle(originX, originY, tailRadius, dripFillPaint)
                val neckBuilder = PathBuilder()
                addCapsule(
                    neckBuilder, neckStartX, neckStartY, headX, headY, perpendicularX,
                    directionX, tailRadius, beadRadius
                )
                val neckPath = neckBuilder.detach()
                canvas.drawPath(neckPath, dripFillPaint)
                neckPath.close()
            } else {
                val bodyBuilder = PathBuilder()
                addCapsule(
                    bodyBuilder, originX, originY, neckStartX, neckStartY, perpendicularX,
                    directionX, tailRadius, tailRadius
                )
                addCapsule(
                    bodyBuilder, neckStartX, neckStartY, headX, headY, perpendicularX,
                    directionX, tailRadius, beadRadius
                )
                val bodyPath = bodyBuilder.detach()
                canvas.drawPath(bodyPath, dripFillPaint)
                bodyPath.close()
            }
            if (streakLength - neckLength > 0.5f) {
                val stripeOffset = trailRadius * physicsSettingsService.dripGlossStripeOffsetRatio.toFloat()
                val stripeRadius = trailRadius * physicsSettingsService.dripGlossStripeWidthRatio.toFloat()
                val stripeStartX = originX + perpendicularX * stripeOffset
                val stripeStartY = originY + directionX * stripeOffset
                val stripeEndX = neckStartX + perpendicularX * stripeOffset
                val stripeEndY = neckStartY + directionX * stripeOffset
                if (isVertical) {
                    dripGlossStripeFillPaint.color = stripeColor
                    canvas.drawRect(
                        Rect.makeLTRB(
                            stripeStartX - stripeRadius,
                            min(stripeStartY, stripeEndY),
                            stripeStartX + stripeRadius,
                            max(stripeStartY, stripeEndY)
                        ),
                        dripGlossStripeFillPaint
                    )
                    canvas.drawCircle(stripeStartX, stripeStartY, stripeRadius, dripGlossStripeFillPaint)
                    canvas.drawCircle(stripeEndX, stripeEndY, stripeRadius, dripGlossStripeFillPaint)
                } else {
                    addCapsule(
                        stripeBuilder,
                        stripeStartX,
                        stripeStartY,
                        stripeEndX,
                        stripeEndY,
                        perpendicularX,
                        directionX,
                        stripeRadius,
                        stripeRadius
                    )
                    stripeCount++
                }
            }
            val specularOffset = beadRadius * physicsSettingsService.dripGlossSpecularOffsetRatio.toFloat()
            val specularBase = specularCount * DRIP_SPECULAR_FLOATS
            dripSpecularBuffer[specularBase] = headX - directionX * specularOffset + perpendicularX * specularOffset
            dripSpecularBuffer[specularBase + 1] = headY - directionY * specularOffset + directionX * specularOffset
            dripSpecularBuffer[specularBase + 2] = beadRadius * physicsSettingsService.dripGlossSpecularRadiusRatio.toFloat()
            specularCount++
        }
        val stripePath = stripeBuilder.detach()
        if (stripeCount > 0) {
            dripFillPaint.color = stripeColor
            canvas.drawPath(stripePath, dripFillPaint)
        }
        stripePath.close()
        if (specularCount == 0) return
        val specularImage = specularImageFor((glossIntensity * 255f).toInt().coerceIn(0, 255))
        val sourceSize = specularImage.width.toFloat()
        for (specularIndex in 0 until specularCount) {
            val specularBase = specularIndex * DRIP_SPECULAR_FLOATS
            val specularX = dripSpecularBuffer[specularBase]
            val specularY = dripSpecularBuffer[specularBase + 1]
            val specularRadius = dripSpecularBuffer[specularBase + 2]
            canvas.drawImageRect(
                image = specularImage,
                srcLeft = 0f,
                srcTop = 0f,
                srcRight = sourceSize,
                srcBottom = sourceSize,
                dstLeft = specularX - specularRadius,
                dstTop = specularY - specularRadius,
                dstRight = specularX + specularRadius,
                dstBottom = specularY + specularRadius,
                samplingMode = SamplingMode.LINEAR,
                paint = dripGlossSpecularPaint,
                strict = true
            )
        }
    }

    private fun addCapsule(
        builder: PathBuilder,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        perpendicularX: Float,
        perpendicularY: Float,
        startRadius: Float,
        endRadius: Float
    ) {
        builder.moveTo(startX + perpendicularX * startRadius, startY + perpendicularY * startRadius)
        builder.lineTo(startX - perpendicularX * startRadius, startY - perpendicularY * startRadius)
        builder.lineTo(endX - perpendicularX * endRadius, endY - perpendicularY * endRadius)
        builder.lineTo(endX + perpendicularX * endRadius, endY + perpendicularY * endRadius)
        builder.closePath()
        builder.addOval(Rect.makeXYWH(startX - startRadius, startY - startRadius, startRadius * 2f, startRadius * 2f))
        builder.addOval(Rect.makeXYWH(endX - endRadius, endY - endRadius, endRadius * 2f, endRadius * 2f))
    }

    private fun specularImageFor(alpha: Int): Image {
        val cachedImage = dripSpecularImage
        if (cachedImage != null && dripSpecularAlpha == alpha) return cachedImage
        cachedImage?.close()
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(DRIP_SPECULAR_IMAGE_SIZE, DRIP_SPECULAR_IMAGE_SIZE))
        val center = DRIP_SPECULAR_IMAGE_SIZE / 2f
        val specularShader = Shader.makeRadialGradient(
            center,
            center,
            center,
            intArrayOf(Color.makeARGB(alpha, 255, 255, 255), Color.makeARGB(0, 255, 255, 255))
        )
        try {
            dripGlossSpecularPaint.shader = specularShader
            surface.canvas.drawCircle(center, center, center, dripGlossSpecularPaint)
        } finally {
            dripGlossSpecularPaint.shader = null
            specularShader.close()
        }
        val specularImage = surface.makeImageSnapshot()
        surface.close()
        dripSpecularImage = specularImage
        dripSpecularAlpha = alpha
        return specularImage
    }

    private fun drawProjectileBlob(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val centerX = particle.transform.position.x.toFloat() - viewPortOffsetX
        val centerY = particle.transform.position.y.toFloat() - viewPortOffsetY
        val baseRadius = particle.collisionRadius().toFloat()
        val age = particle.lifecycle.age.toFloat()
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
        val alpha = (particle.appearance.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
        projectileBlobPaint.color = Color.makeARGB(
            alpha,
            (particle.appearance.color.red * 255).toInt(),
            (particle.appearance.color.green * 255).toInt(),
            (particle.appearance.color.blue * 255).toInt()
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
