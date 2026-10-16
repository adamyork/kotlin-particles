package com.github.adamyork.kparticles.android.engine

import android.graphics.*
import com.github.adamyork.kparticles.android.engine.data.AndroidImage
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.ParticleDrawFunction
import com.github.adamyork.kparticles.platform.engine.Renderers
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class AndroidCpuRenderers(
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
        const val DRIP_SPECULAR_IMAGE_SIZE: Int = 64
        const val BUBBLE_IMAGE_PADDING: Int = 4
        const val BUBBLE_IMAGE_CACHE_LIMIT: Int = 128
        const val RADIANS_TO_DEGREES: Float = (180.0 / PI).toFloat()
    }

    private val particlePaint: Paint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val spritePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val projectileBlobPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val projectileBlobPath = Path()
    private val bubbleFillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleRimPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BUBBLE_RIM_STROKE_WIDTH
        color = BUBBLE_RIM_COLOR
    }
    private val bubbleHighlightPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dripPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dripFillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dripGlossStripePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dripGlossSpecularPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dripNeckPath = Path()

    private val particleSrcRect = Rect()
    private val particleRectF = RectF()
    private val bubbleBitmaps = HashMap<Int, Bitmap>()
    private var dripSpecularBitmap: Bitmap? = null
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
        for (generator in generators) {
            val particles = generator.particles
            for (particleIndex in particles.indices) {
                val particle = particles[particleIndex]
                if (!particle.cullingCheck(viewPort)) continue
                particlePaint.color = particleColorArgb(particle)
                val x = particle.transform.position.x.toFloat() - viewPortOffsetX
                val y = particle.transform.position.y.toFloat() - viewPortOffsetY
                when (val dimensions = particle.dimensions) {
                    is CircleDimensions -> canvas.drawCircle(x, y, dimensions.radius.toFloat(), particlePaint)
                    is RectDimensions -> {
                        val halfWidth = dimensions.width.toFloat() / 2f
                        val halfHeight = dimensions.height.toFloat() / 2f
                        particleRectF.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)
                        if (particle.transform.angle == 0.0) {
                            canvas.drawRect(particleRectF, particlePaint)
                        } else {
                            canvas.save()
                            canvas.rotate(particle.transform.angle.toFloat() * RADIANS_TO_DEGREES, x, y)
                            canvas.drawRect(particleRectF, particlePaint)
                            canvas.restore()
                        }
                    }
                }
            }
        }
    }

    private fun drawComplexShapes(drawFunctionData: DrawFunctionData<Canvas>) {
        val generators = drawFunctionData.generators
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in generators) {
            val particles = generator.particles
            for (particleIndex in particles.indices) {
                val particle = particles[particleIndex]
                if (!particle.cullingCheck(viewPort)) continue
                when (generator.effect) {
                    ParticleEffect.BUBBLE -> if (particle.lifecycle.isVisible()) {
                        drawBubble(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                    }

                    ParticleEffect.BLOB_PROJECTILE -> drawProjectileBlob(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                    ParticleEffect.DRIPS -> if (particle.lifecycle.isVisible()) {
                        drawDrip(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                    }

                    else -> Unit
                }
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
                val frameOffsetX = (if (animated) (particle.animation?.frame ?: 0) else 0) * spriteSheet.frameWidth
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

    private fun drawBubble(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val centerX = particle.transform.position.x.toFloat() - viewPortOffsetX
        val centerY = particle.transform.position.y.toFloat() - viewPortOffsetY
        val radiusKey = (particle.collisionRadius().toFloat() * 2f).roundToInt()
        val bubbleBitmap = bubbleBitmaps[radiusKey] ?: createBubbleBitmap(radiusKey / 2f).also { createdBitmap ->
            if (bubbleBitmaps.size >= BUBBLE_IMAGE_CACHE_LIMIT) {
                bubbleBitmaps.values.forEach { cachedBitmap -> cachedBitmap.recycle() }
                bubbleBitmaps.clear()
            }
            bubbleBitmaps[radiusKey] = createdBitmap
        }
        canvas.drawBitmap(bubbleBitmap, centerX - bubbleBitmap.width / 2f, centerY - bubbleBitmap.height / 2f, spritePaint)
    }

    private fun createBubbleBitmap(radius: Float): Bitmap {
        val size = ceil(radius * 2f).toInt() + BUBBLE_IMAGE_PADDING
        val center = size / 2f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bitmapCanvas = Canvas(bitmap)
        bubbleFillPaint.shader = LinearGradient(
            center - radius,
            center - radius,
            center + radius,
            center + radius,
            BUBBLE_FILL_TOP_LEFT_COLOR,
            BUBBLE_FILL_BOTTOM_RIGHT_COLOR,
            Shader.TileMode.CLAMP
        )
        bitmapCanvas.drawCircle(center, center, radius, bubbleFillPaint)
        bubbleFillPaint.shader = null
        bitmapCanvas.drawCircle(center, center, radius - (BUBBLE_RIM_STROKE_WIDTH / 2f), bubbleRimPaint)
        val highlightRadius = radius * BUBBLE_HIGHLIGHT_RADIUS_RATIO
        val highlightOffset = radius * BUBBLE_HIGHLIGHT_OFFSET_RATIO
        bubbleHighlightPaint.shader = RadialGradient(
            center - highlightOffset,
            center - highlightOffset,
            highlightRadius,
            BUBBLE_HIGHLIGHT_COLOR,
            BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR,
            Shader.TileMode.CLAMP
        )
        bitmapCanvas.drawCircle(center - highlightOffset, center - highlightOffset, highlightRadius, bubbleHighlightPaint)
        bubbleHighlightPaint.shader = null
        return bitmap
    }

    private fun drawDrip(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
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
        val perpendicularY = directionX
        val neckSetting = max(beadRadius * physicsSettingsService.dripNeckLengthRatio.toFloat(), DRIP_MINIMUM_LENGTH)
        val neckLength = min(streakLength, neckSetting)
        val tailRadius = beadRadius + (trailRadius - beadRadius) * (streakLength / neckSetting).coerceIn(0f, 1f)
        val neckStartX = headX - directionX * neckLength
        val neckStartY = headY - directionY * neckLength
        val dripColor = particleColorArgb(particle)
        dripPaint.color = dripColor
        dripPaint.strokeWidth = tailRadius * 2f
        canvas.drawLine(originX, originY, neckStartX, neckStartY, dripPaint)
        dripNeckPath.reset()
        dripNeckPath.moveTo(neckStartX + perpendicularX * tailRadius, neckStartY + perpendicularY * tailRadius)
        dripNeckPath.lineTo(headX + perpendicularX * beadRadius, headY + perpendicularY * beadRadius)
        dripNeckPath.lineTo(headX - perpendicularX * beadRadius, headY - perpendicularY * beadRadius)
        dripNeckPath.lineTo(neckStartX - perpendicularX * tailRadius, neckStartY - perpendicularY * tailRadius)
        dripNeckPath.close()
        dripFillPaint.color = dripColor
        canvas.drawPath(dripNeckPath, dripFillPaint)
        canvas.drawCircle(headX, headY, beadRadius, dripFillPaint)
        val glossIntensity = physicsSettingsService.dripGlossIntensity.toFloat()
        if (streakLength - neckLength > 0.5f) {
            val stripeOffset = trailRadius * physicsSettingsService.dripGlossStripeOffsetRatio.toFloat()
            dripGlossStripePaint.color =
                Color.argb((glossIntensity * DRIP_STRIPE_ALPHA_SCALE * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            dripGlossStripePaint.strokeWidth = trailRadius * physicsSettingsService.dripGlossStripeWidthRatio.toFloat() * 2f
            canvas.drawLine(
                originX + perpendicularX * stripeOffset,
                originY + perpendicularY * stripeOffset,
                neckStartX + perpendicularX * stripeOffset,
                neckStartY + perpendicularY * stripeOffset,
                dripGlossStripePaint
            )
        }
        val specularOffset = beadRadius * physicsSettingsService.dripGlossSpecularOffsetRatio.toFloat()
        val specularCenterX = headX - directionX * specularOffset + perpendicularX * specularOffset
        val specularCenterY = headY - directionY * specularOffset + perpendicularY * specularOffset
        val specularRadius = max(beadRadius * physicsSettingsService.dripGlossSpecularRadiusRatio.toFloat(), DRIP_MINIMUM_LENGTH)
        val specularBitmap = specularBitmapFor((glossIntensity * 255f).toInt().coerceIn(0, 255))
        particleSrcRect.set(0, 0, specularBitmap.width, specularBitmap.height)
        particleRectF.set(
            specularCenterX - specularRadius,
            specularCenterY - specularRadius,
            specularCenterX + specularRadius,
            specularCenterY + specularRadius
        )
        canvas.drawBitmap(specularBitmap, particleSrcRect, particleRectF, spritePaint)
    }

    private fun specularBitmapFor(alpha: Int): Bitmap {
        val cachedBitmap = dripSpecularBitmap
        if (cachedBitmap != null && dripSpecularAlpha == alpha) return cachedBitmap
        cachedBitmap?.recycle()
        val bitmap = Bitmap.createBitmap(DRIP_SPECULAR_IMAGE_SIZE, DRIP_SPECULAR_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
        val center = DRIP_SPECULAR_IMAGE_SIZE / 2f
        dripGlossSpecularPaint.shader = RadialGradient(
            center,
            center,
            center,
            Color.argb(alpha, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        Canvas(bitmap).drawCircle(center, center, center, dripGlossSpecularPaint)
        dripGlossSpecularPaint.shader = null
        dripSpecularBitmap = bitmap
        dripSpecularAlpha = alpha
        return bitmap
    }

    private fun particleColorArgb(particle: Particle): Int {
        val alpha = (particle.appearance.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
        val red = (particle.appearance.color.red * 255f).toInt().coerceIn(0, 255)
        val green = (particle.appearance.color.green * 255f).toInt().coerceIn(0, 255)
        val blue = (particle.appearance.color.blue * 255f).toInt().coerceIn(0, 255)
        return Color.argb(alpha, red, green, blue)
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
        val lobeCount = BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT
        val lobePointXs = FloatArray(lobeCount)
        val lobePointYs = FloatArray(lobeCount)
        var lobeIndex = 0
        while (lobeIndex < lobeCount) {
            val lobeAngle = (lobeIndex.toFloat() / lobeCount.toFloat()) * (2f * PI.toFloat())
            val lobeFrequency = BlobProjectileGenerator.PROJECTILE_BLOB_BASE_FREQUENCY +
                    (lobeIndex * BlobProjectileGenerator.PROJECTILE_BLOB_FREQUENCY_STEP)
            val lobePhase = lobeIndex * BlobProjectileGenerator.PROJECTILE_BLOB_PHASE_STEP
            val lobeWobble = sin((age * lobeFrequency) + lobePhase)
            val lobeRadius = baseRadius * (1f + (BlobProjectileGenerator.PROJECTILE_BLOB_AMPLITUDE_RATIO * lobeWobble))
            lobePointXs[lobeIndex] = centerX + (cos(lobeAngle) * lobeRadius)
            lobePointYs[lobeIndex] = centerY + (sin(lobeAngle) * lobeRadius)
            lobeIndex++
        }
        buildSmoothClosedBlobPath(lobePointXs, lobePointYs)
        val alpha = (particle.appearance.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
        val red = (particle.appearance.color.red * 255f).toInt().coerceIn(0, 255)
        val green = (particle.appearance.color.green * 255f).toInt().coerceIn(0, 255)
        val blue = (particle.appearance.color.blue * 255f).toInt().coerceIn(0, 255)
        projectileBlobPaint.color = Color.argb(alpha, red, green, blue)
        canvas.drawPath(projectileBlobPath, projectileBlobPaint)
    }

    private fun buildSmoothClosedBlobPath(lobePointXs: FloatArray, lobePointYs: FloatArray) {
        projectileBlobPath.reset()
        val lobeCount = lobePointXs.size
        projectileBlobPath.moveTo(lobePointXs[0], lobePointYs[0])
        var lobeIndex = 0
        while (lobeIndex < lobeCount) {
            val previousIndex = (lobeIndex - 1 + lobeCount) % lobeCount
            val nextIndex = (lobeIndex + 1) % lobeCount
            val afterNextIndex = (lobeIndex + 2) % lobeCount
            val tension = BlobProjectileGenerator.PROJECTILE_BLOB_SPLINE_TENSION
            val controlPoint1X =
                lobePointXs[lobeIndex] + (lobePointXs[nextIndex] - lobePointXs[previousIndex]) / tension
            val controlPoint1Y =
                lobePointYs[lobeIndex] + (lobePointYs[nextIndex] - lobePointYs[previousIndex]) / tension
            val controlPoint2X =
                lobePointXs[nextIndex] - (lobePointXs[afterNextIndex] - lobePointXs[lobeIndex]) / tension
            val controlPoint2Y =
                lobePointYs[nextIndex] - (lobePointYs[afterNextIndex] - lobePointYs[lobeIndex]) / tension
            projectileBlobPath.cubicTo(
                controlPoint1X,
                controlPoint1Y,
                controlPoint2X,
                controlPoint2Y,
                lobePointXs[nextIndex],
                lobePointYs[nextIndex]
            )
            lobeIndex++
        }
        projectileBlobPath.close()
    }

}
