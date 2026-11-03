package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.BlobGeometry
import com.github.adamyork.kparticles.platform.engine.CpuRendererConstants
import com.github.adamyork.kparticles.platform.engine.DripGeometry
import com.github.adamyork.kparticles.platform.engine.EngineDiagnostics
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
    private val physicsSettingsService: PhysicsSettingsService,
    private val engineDiagnostics: EngineDiagnostics
) : Renderers<Canvas> {

    private companion object {
        const val DRIP_SPECULAR_FLOATS: Int = 3
        const val DRIP_VERTICAL_TOLERANCE: Float = 0.001f
    }

    private val particlePaint = Paint().apply { isAntiAlias = false; mode = PaintMode.FILL }
    private val spritePaint = Paint().apply { isAntiAlias = true }
    private val projectileBlobPaint = Paint().apply { isAntiAlias = true; mode = PaintMode.FILL }
    private val blobLobePointXs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
    private val blobLobePointYs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
    private val blobSegmentPoints = FloatArray(BlobGeometry.SEGMENT_POINT_FLOATS)
    private val dripGeometry = DripGeometry(physicsSettingsService)
    private val bubbleFillPaint = Paint().apply { isAntiAlias = true }
    private val bubbleRimPaint = Paint().apply {
        isAntiAlias = true
        mode = PaintMode.STROKE
        color = CpuRendererConstants.BUBBLE_RIM_COLOR
        strokeWidth = CpuRendererConstants.BUBBLE_RIM_STROKE_WIDTH
    }
    private val bubbleHighlightPaint = Paint().apply { isAntiAlias = true }
    private val dripFillPaint = Paint().apply { isAntiAlias = true }
    private val dripGlossStripeFillPaint = Paint().apply { isAntiAlias = true }
    private val dripGlossSpecularPaint = Paint().apply { isAntiAlias = true }
    private val bubbleBlitPaint = Paint().apply { isAntiAlias = false }
    private val bubbleImages = HashMap<Int, Image>()
    private val simpleShapeGroups = HashMap<Int, ArrayList<Particle>>(64)
    private val simpleShapeListPool = ArrayList<ArrayList<Particle>>()
    private val simpleShapeBuilder = PathBuilder()
    private val dripBodyBuilder = PathBuilder()
    private val dripStripeBuilder = PathBuilder()
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
        for (retiredList in simpleShapeGroups.values) {
            retiredList.clear()
            simpleShapeListPool.add(retiredList)
        }
        simpleShapeGroups.clear()
        val groups = simpleShapeGroups
        val groupingMark = engineDiagnostics.startTimer()
        var culledCount = 0
        var drawnCount = 0
        for (generator in generators) {
            val particles = generator.particles
            var particleIndex = 0
            while (particleIndex < particles.size) {
                val particle = particles[particleIndex]
                particleIndex++
                if (!particle.cullingCheck(viewPort)) {
                    culledCount++
                    continue
                }
                drawnCount++
                val color = particle.appearance.packedArgb()
                val bucket = groups[color]
                if (bucket == null) {
                    val newBucket = if (simpleShapeListPool.isEmpty()) {
                        ArrayList()
                    } else {
                        simpleShapeListPool.removeAt(simpleShapeListPool.lastIndex)
                    }
                    newBucket.add(particle)
                    groups[color] = newBucket
                } else {
                    bucket.add(particle)
                }
            }
        }
        engineDiagnostics.stopTimer(DiagnosticPhase.SIMPLE_GROUPING, groupingMark)
        engineDiagnostics.count(DiagnosticCounter.SIMPLE_CULLED, culledCount)
        engineDiagnostics.count(DiagnosticCounter.SIMPLE_DRAWN, drawnCount)
        engineDiagnostics.count(DiagnosticCounter.SIMPLE_COLOR_GROUPS, groups.size)
        val fillMark = engineDiagnostics.startTimer()
        val groupIterator = groups.entries.iterator()
        while (groupIterator.hasNext()) {
            val entry = groupIterator.next()
            val particleList = entry.value
            val builder = simpleShapeBuilder
            var groupParticleIndex = 0
            while (groupParticleIndex < particleList.size) {
                val particle = particleList[groupParticleIndex]
                val x = particle.transform.position.x.toFloat() - viewPortOffsetX
                val y = particle.transform.position.y.toFloat() - viewPortOffsetY
                when (val dimensions = particle.dimensions) {
                    is CircleDimensions -> {
                        val radius = dimensions.radius.toFloat()
                        builder.addOval(x - radius, y - radius, x + radius, y + radius)
                    }
                    is RectDimensions -> {
                        val width = dimensions.width.toFloat()
                        val height = dimensions.height.toFloat()
                        if (particle.transform.angle == 0.0) {
                            builder.addRect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)
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
        engineDiagnostics.stopTimer(DiagnosticPhase.SIMPLE_FILL, fillMark)
    }

    private fun drawComplexShapes(drawFunctionData: DrawFunctionData<Canvas>) {
        val generators = drawFunctionData.generators
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in generators) {
            val generatorMark = engineDiagnostics.startTimer()
            drawComplexShapeGenerator(generator, viewPort, viewPortOffsetX, viewPortOffsetY, canvas)
            engineDiagnostics.stopEffectTimer(generator.effect, generatorMark)
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
            intArrayOf(CpuRendererConstants.BUBBLE_FILL_TOP_LEFT_COLOR, CpuRendererConstants.BUBBLE_FILL_BOTTOM_RIGHT_COLOR)
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
            intArrayOf(CpuRendererConstants.BUBBLE_HIGHLIGHT_COLOR, CpuRendererConstants.BUBBLE_HIGHLIGHT_TRANSPARENT_COLOR)
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
            Color.makeARGB((glossIntensity * CpuRendererConstants.DRIP_STRIPE_ALPHA_SCALE * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
        val stripeBuilder = dripStripeBuilder
        var stripeCount = 0
        var specularCount = 0
        var particleIndex = 0
        while (particleIndex < particles.size) {
            val particle = particles[particleIndex]
            particleIndex++
            if (!particle.cullingCheck(viewPort) || !particle.lifecycle.isVisible()) continue
            dripGeometry.update(particle, viewPortOffsetX, viewPortOffsetY)
            val isVertical = abs(dripGeometry.directionX) <= DRIP_VERTICAL_TOLERANCE
            dripFillPaint.color = particle.appearance.packedArgb()
            if (isVertical) {
                canvas.drawRect(
                    Rect.makeLTRB(dripGeometry.originX - dripGeometry.tailRadius, min(dripGeometry.originY, dripGeometry.neckStartY), dripGeometry.originX + dripGeometry.tailRadius, max(dripGeometry.originY, dripGeometry.neckStartY)),
                    dripFillPaint
                )
                canvas.drawCircle(dripGeometry.originX, dripGeometry.originY, dripGeometry.tailRadius, dripFillPaint)
                val neckBuilder = dripBodyBuilder
                addCapsule(
                    neckBuilder, dripGeometry.neckStartX, dripGeometry.neckStartY, dripGeometry.headX, dripGeometry.headY, dripGeometry.perpendicularX,
                    dripGeometry.directionX, dripGeometry.tailRadius, dripGeometry.beadRadius
                )
                val neckPath = neckBuilder.detach()
                canvas.drawPath(neckPath, dripFillPaint)
                neckPath.close()
            } else {
                val bodyBuilder = dripBodyBuilder
                addCapsule(
                    bodyBuilder, dripGeometry.originX, dripGeometry.originY, dripGeometry.neckStartX, dripGeometry.neckStartY, dripGeometry.perpendicularX,
                    dripGeometry.directionX, dripGeometry.tailRadius, dripGeometry.tailRadius
                )
                addCapsule(
                    bodyBuilder, dripGeometry.neckStartX, dripGeometry.neckStartY, dripGeometry.headX, dripGeometry.headY, dripGeometry.perpendicularX,
                    dripGeometry.directionX, dripGeometry.tailRadius, dripGeometry.beadRadius
                )
                val bodyPath = bodyBuilder.detach()
                canvas.drawPath(bodyPath, dripFillPaint)
                bodyPath.close()
            }
            if (dripGeometry.hasStripe) {
                val stripeStartX = dripGeometry.originX + dripGeometry.perpendicularX * dripGeometry.stripeOffset
                val stripeStartY = dripGeometry.originY + dripGeometry.directionX * dripGeometry.stripeOffset
                val stripeEndX = dripGeometry.neckStartX + dripGeometry.perpendicularX * dripGeometry.stripeOffset
                val stripeEndY = dripGeometry.neckStartY + dripGeometry.directionX * dripGeometry.stripeOffset
                if (isVertical) {
                    dripGlossStripeFillPaint.color = stripeColor
                    canvas.drawRect(
                        Rect.makeLTRB(
                            stripeStartX - dripGeometry.stripeRadius,
                            min(stripeStartY, stripeEndY),
                            stripeStartX + dripGeometry.stripeRadius,
                            max(stripeStartY, stripeEndY)
                        ),
                        dripGlossStripeFillPaint
                    )
                    canvas.drawCircle(stripeStartX, stripeStartY, dripGeometry.stripeRadius, dripGlossStripeFillPaint)
                    canvas.drawCircle(stripeEndX, stripeEndY, dripGeometry.stripeRadius, dripGlossStripeFillPaint)
                } else {
                    addCapsule(
                        stripeBuilder,
                        stripeStartX,
                        stripeStartY,
                        stripeEndX,
                        stripeEndY,
                        dripGeometry.perpendicularX,
                        dripGeometry.directionX,
                        dripGeometry.stripeRadius,
                        dripGeometry.stripeRadius
                    )
                    stripeCount++
                }
            }
            val specularBase = specularCount * DRIP_SPECULAR_FLOATS
            dripSpecularBuffer[specularBase] = dripGeometry.specularCenterX
            dripSpecularBuffer[specularBase + 1] = dripGeometry.specularCenterY
            dripSpecularBuffer[specularBase + 2] = dripGeometry.specularRadius
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
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(CpuRendererConstants.DRIP_SPECULAR_IMAGE_SIZE, CpuRendererConstants.DRIP_SPECULAR_IMAGE_SIZE))
        val center = CpuRendererConstants.DRIP_SPECULAR_IMAGE_SIZE / 2f
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
        BlobGeometry.writeLobePoints(centerX, centerY, baseRadius, age, blobLobePointXs, blobLobePointYs)
        val blobPath = buildSmoothClosedBlobPath(blobLobePointXs, blobLobePointYs)
        projectileBlobPaint.color = particle.appearance.packedArgb()
        canvas.drawPath(blobPath, projectileBlobPaint)
        blobPath.close()
    }

    private fun buildSmoothClosedBlobPath(lobePointXs: FloatArray, lobePointYs: FloatArray): Path {
        val builder = PathBuilder()
        builder.moveTo(lobePointXs[0], lobePointYs[0])
        var segmentIndex = 0
        while (segmentIndex < lobePointXs.size) {
            BlobGeometry.writeSegmentControlPoints(lobePointXs, lobePointYs, segmentIndex, blobSegmentPoints)
            builder.cubicTo(
                blobSegmentPoints[0],
                blobSegmentPoints[1],
                blobSegmentPoints[2],
                blobSegmentPoints[3],
                blobSegmentPoints[4],
                blobSegmentPoints[5]
            )
            segmentIndex++
        }
        builder.closePath()
        return builder.detach()
    }

}
