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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
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
        for (particleIndex in particles.indices) {
            val particle = particles[particleIndex]
            val lifetime = if (particle.lifetime <= 0) 1 else particle.lifetime
            val ageProgress = (particle.age.toFloat() / lifetime.toFloat()).coerceIn(0f, 1f)
            val alphaMultiplier = when {
                particle.effect == ParticleEffect.PROJECTILE -> 1.0f
                ageProgress < 0.33f -> 1.0f
                ageProgress < 0.66f -> 0.66f
                else -> 0.33f
            }
            particlePaint.color = particleColorArgb(particle, alphaMultiplier)
            val x = particle.x.toFloat() - viewPortOffsetX
            val y = particle.y.toFloat() - viewPortOffsetY
            particleRectF.set(x, y, x + particle.width.toFloat(), y + particle.height.toFloat())
            if (particle.shape == ParticleShape.CIRCLE) {
                canvas.drawOval(particleRectF, particlePaint)
            } else {
                canvas.drawRect(particleRectF, particlePaint)
            }
        }
    }

    private fun drawComplexShapes(drawFunctionData: DrawFunctionData<Canvas>) {
        val particles = drawFunctionData.particles
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (particleIndex in particles.indices) {
            val particle = particles[particleIndex]
            when (particle.effect) {
                ParticleEffect.BUBBLE -> if (particle.visible) {
                    drawBubble(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                }

                ParticleEffect.BLOB_PROJECTILE -> drawProjectileBlob(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                ParticleEffect.DRIPS -> if (particle.visible) {
                    drawDrip(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                }

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
        val particles = drawFunctionData.particles
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val spriteImage = spriteSheet.image as? AndroidImage ?: return
        val spriteBitmap: Bitmap = spriteImage.bitmap
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        val sourceWidth = spriteSheet.frameWidth.toFloat()
        val sourceHeight = spriteSheet.frameHeight.toFloat()
        for (particleIndex in particles.indices) {
            val particle = particles[particleIndex]
            val localX = particle.x.toFloat() - viewPortOffsetX
            val localY = particle.y.toFloat() - viewPortOffsetY
            val frameOffsetX = (if (animated) particle.frame else 0) * spriteSheet.frameWidth
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

    private fun drawBubble(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val centerX = particle.x.toFloat() - viewPortOffsetX
        val centerY = particle.y.toFloat() - viewPortOffsetY
        val radius = particle.radius.toFloat()
        bubbleFillPaint.shader = LinearGradient(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            BUBBLE_FILL_TOP_LEFT_COLOR,
            BUBBLE_FILL_BOTTOM_RIGHT_COLOR,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, bubbleFillPaint)
        val rimRadius = radius - (BUBBLE_RIM_STROKE_WIDTH / 2f)
        canvas.drawCircle(centerX, centerY, rimRadius, bubbleRimPaint)
        val highlightRadius = radius * BUBBLE_HIGHLIGHT_RADIUS_RATIO
        val highlightOffset = radius * BUBBLE_HIGHLIGHT_OFFSET_RATIO
        val highlightCenterX = centerX - highlightOffset
        val highlightCenterY = centerY - highlightOffset
        bubbleHighlightPaint.shader = RadialGradient(
            highlightCenterX,
            highlightCenterY,
            highlightRadius,
            BUBBLE_HIGHLIGHT_COLOR,
            BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(highlightCenterX, highlightCenterY, highlightRadius, bubbleHighlightPaint)
    }

    private fun drawDrip(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val headProgress = particle.travelProgress().toFloat()
        val originX = particle.originX.toFloat() - viewPortOffsetX
        val originY = particle.originY.toFloat() - viewPortOffsetY
        val headX = originX + (particle.destinationX - particle.originX).toFloat() * headProgress
        val headY = originY + (particle.destinationY - particle.originY).toFloat() * headProgress
        val beadRadius = particle.radius.toFloat()
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
        val dripColor = particleColorArgb(particle, 1.0f)
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
        dripGlossSpecularPaint.shader = RadialGradient(
            specularCenterX,
            specularCenterY,
            specularRadius,
            Color.argb((glossIntensity * 255f).toInt().coerceIn(0, 255), 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(specularCenterX, specularCenterY, specularRadius, dripGlossSpecularPaint)
    }

    private fun particleColorArgb(particle: Particle, alphaMultiplier: Float): Int {
        val alpha = (particle.color.alpha.coerceIn(0f, 1f) * alphaMultiplier * 255f).toInt().coerceIn(0, 255)
        val red = (particle.color.red * 255f).toInt().coerceIn(0, 255)
        val green = (particle.color.green * 255f).toInt().coerceIn(0, 255)
        val blue = (particle.color.blue * 255f).toInt().coerceIn(0, 255)
        return Color.argb(alpha, red, green, blue)
    }

    private fun drawProjectileBlob(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val centerX = (particle.x.toFloat() - viewPortOffsetX) + (particle.width.toFloat() * 0.5f)
        val centerY = (particle.y.toFloat() - viewPortOffsetY) + (particle.height.toFloat() * 0.5f)
        val baseRadius = particle.radius.toFloat()
        val age = particle.age.toFloat()
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
        val alpha = (particle.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
        val red = (particle.color.red * 255f).toInt().coerceIn(0, 255)
        val green = (particle.color.green * 255f).toInt().coerceIn(0, 255)
        val blue = (particle.color.blue * 255f).toInt().coerceIn(0, 255)
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
