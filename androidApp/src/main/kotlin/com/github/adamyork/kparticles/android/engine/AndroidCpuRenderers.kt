package com.github.adamyork.kparticles.android.engine

import android.graphics.*
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import com.github.adamyork.kparticles.android.engine.data.AndroidImage
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.BlobGeometry
import com.github.adamyork.kparticles.platform.engine.CpuRendererConstants
import com.github.adamyork.kparticles.platform.engine.DripGeometry
import com.github.adamyork.kparticles.platform.engine.ParticleDrawFunction
import com.github.adamyork.kparticles.platform.engine.Renderers
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import me.tatarka.inject.annotations.Inject
import kotlin.math.*

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
    private val blobLobePointXs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
    private val blobLobePointYs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
    private val blobSegmentPoints = FloatArray(BlobGeometry.SEGMENT_POINT_FLOATS)
    private val dripGeometry = DripGeometry(physicsSettingsService)
    private val bubbleFillPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleRimPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = CpuRendererConstants.BUBBLE_RIM_STROKE_WIDTH
        color = CpuRendererConstants.BUBBLE_RIM_COLOR
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
                particlePaint.color = particle.appearance.packedArgb()
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
                            canvas.withRotation(particle.transform.angle.toFloat() * RADIANS_TO_DEGREES, x, y) {
                                drawRect(particleRectF, particlePaint)
                            }
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
                    ParticleEffect.BLOB_PROJECTILE -> drawProjectileBlob(
                        particle,
                        viewPortOffsetX,
                        viewPortOffsetY,
                        canvas
                    )
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
            if (bubbleBitmaps.size >= CpuRendererConstants.BUBBLE_IMAGE_CACHE_LIMIT) {
                bubbleBitmaps.values.forEach { cachedBitmap -> cachedBitmap.recycle() }
                bubbleBitmaps.clear()
            }
            bubbleBitmaps[radiusKey] = createdBitmap
        }
        canvas.drawBitmap(
            bubbleBitmap,
            centerX - bubbleBitmap.width / 2f,
            centerY - bubbleBitmap.height / 2f,
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

    private fun drawDrip(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        dripGeometry.update(particle, viewPortOffsetX, viewPortOffsetY)
        val dripColor = particle.appearance.packedArgb()
        dripPaint.color = dripColor
        dripPaint.strokeWidth = dripGeometry.tailRadius * 2f
        canvas.drawLine(dripGeometry.originX, dripGeometry.originY, dripGeometry.neckStartX, dripGeometry.neckStartY, dripPaint)
        dripNeckPath.reset()
        dripNeckPath.moveTo(dripGeometry.neckStartX + dripGeometry.perpendicularX * dripGeometry.tailRadius, dripGeometry.neckStartY + dripGeometry.directionX * dripGeometry.tailRadius)
        dripNeckPath.lineTo(dripGeometry.headX + dripGeometry.perpendicularX * dripGeometry.beadRadius, dripGeometry.headY + dripGeometry.directionX * dripGeometry.beadRadius)
        dripNeckPath.lineTo(dripGeometry.headX - dripGeometry.perpendicularX * dripGeometry.beadRadius, dripGeometry.headY - dripGeometry.directionX * dripGeometry.beadRadius)
        dripNeckPath.lineTo(dripGeometry.neckStartX - dripGeometry.perpendicularX * dripGeometry.tailRadius, dripGeometry.neckStartY - dripGeometry.directionX * dripGeometry.tailRadius)
        dripNeckPath.close()
        dripFillPaint.color = dripColor
        canvas.drawPath(dripNeckPath, dripFillPaint)
        canvas.drawCircle(dripGeometry.headX, dripGeometry.headY, dripGeometry.beadRadius, dripFillPaint)
        val glossIntensity = physicsSettingsService.dripGlossIntensity.toFloat()
        if (dripGeometry.hasStripe) {
            dripGlossStripePaint.color =
                Color.argb((glossIntensity * CpuRendererConstants.DRIP_STRIPE_ALPHA_SCALE * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            dripGlossStripePaint.strokeWidth =
                dripGeometry.stripeRadius * 2f
            canvas.drawLine(
                dripGeometry.originX + dripGeometry.perpendicularX * dripGeometry.stripeOffset,
                dripGeometry.originY + dripGeometry.directionX * dripGeometry.stripeOffset,
                dripGeometry.neckStartX + dripGeometry.perpendicularX * dripGeometry.stripeOffset,
                dripGeometry.neckStartY + dripGeometry.directionX * dripGeometry.stripeOffset,
                dripGlossStripePaint
            )
        }
        val specularBitmap = specularBitmapFor((glossIntensity * 255f).toInt().coerceIn(0, 255))
        particleSrcRect.set(0, 0, specularBitmap.width, specularBitmap.height)
        particleRectF.set(
            dripGeometry.specularCenterX - dripGeometry.specularRadius,
            dripGeometry.specularCenterY - dripGeometry.specularRadius,
            dripGeometry.specularCenterX + dripGeometry.specularRadius,
            dripGeometry.specularCenterY + dripGeometry.specularRadius
        )
        canvas.drawBitmap(specularBitmap, particleSrcRect, particleRectF, spritePaint)
    }

    private fun specularBitmapFor(alpha: Int): Bitmap {
        val cachedBitmap = dripSpecularBitmap
        if (cachedBitmap != null && dripSpecularAlpha == alpha) return cachedBitmap
        cachedBitmap?.recycle()
        val bitmap = createBitmap(CpuRendererConstants.DRIP_SPECULAR_IMAGE_SIZE, CpuRendererConstants.DRIP_SPECULAR_IMAGE_SIZE)
        val center = CpuRendererConstants.DRIP_SPECULAR_IMAGE_SIZE / 2f
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
        BlobGeometry.writeLobePoints(centerX, centerY, baseRadius, age, blobLobePointXs, blobLobePointYs)
        buildSmoothClosedBlobPath(blobLobePointXs, blobLobePointYs)
        projectileBlobPaint.color = particle.appearance.packedArgb()
        canvas.drawPath(projectileBlobPath, projectileBlobPaint)
    }

    private fun buildSmoothClosedBlobPath(lobePointXs: FloatArray, lobePointYs: FloatArray) {
        projectileBlobPath.reset()
        projectileBlobPath.moveTo(lobePointXs[0], lobePointYs[0])
        var segmentIndex = 0
        while (segmentIndex < lobePointXs.size) {
            BlobGeometry.writeSegmentControlPoints(lobePointXs, lobePointYs, segmentIndex, blobSegmentPoints)
            projectileBlobPath.cubicTo(
                blobSegmentPoints[0],
                blobSegmentPoints[1],
                blobSegmentPoints[2],
                blobSegmentPoints[3],
                blobSegmentPoints[4],
                blobSegmentPoints[5]
            )
            segmentIndex++
        }
        projectileBlobPath.close()
    }

}
