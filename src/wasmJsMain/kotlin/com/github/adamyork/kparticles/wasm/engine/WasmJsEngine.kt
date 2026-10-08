package com.github.adamyork.kparticles.wasm.engine

import androidx.compose.ui.graphics.asSkiaBitmap
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.Collision
import com.github.adamyork.kparticles.platform.engine.CommonEngine
import com.github.adamyork.kparticles.platform.engine.CommonParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import com.github.adamyork.kparticles.wasm.engine.data.WasmJsImage
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.*
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
open class WasmJsEngine(
    particlePhysics: ParticlePhysics,
    particleFactory: ParticleFactory,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    collision: Collision,
) : CommonEngine(
    particlePhysics,
    particleFactory,
    assetService,
    runtimeService,
    platformInterop,
    collision
) {

    private val logger = KotlinLogging.logger {}

    override var mapItemImage: CommonImage = WasmJsImage(
        Image.makeFromBitmap(AbstractPlatformAssetService.getTmpImageBitmap().asSkiaBitmap())
    )
    private var mapItemFrameWidth: Int = 1
    private var mapItemFrameHeight: Int = 1
    override var foregroundSurface: Any? = null
    override val mapElementPaint = Paint().apply { isAntiAlias = true }
    override val particlePaint = Paint().apply { isAntiAlias = false; mode = PaintMode.FILL }
    override val mapItemReturnPaint = Paint().apply { isAntiAlias = true }
    private val projectileBlobPaint = Paint().apply { isAntiAlias = true; mode = PaintMode.FILL }

    override fun getOrCreateForegroundSurface(viewPort: ViewPort): Surface =
        (foregroundSurface as Surface?) ?: Surface.makeRaster(ImageInfo.makeN32Premul(viewPort.width, viewPort.height))
            .also {
                foregroundSurface = it
            }

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing CPU engine" }
        mapItemImage = WasmJsImage(
            Image.makeFromBitmap(collectibleAsset.imageAndBytes.imageBitmap.asSkiaBitmap())
        )
        mapItemFrameWidth = collectibleAsset.width
        mapItemFrameHeight = collectibleAsset.height
    }

    override fun manageParticles(particles: ArrayList<Particle>, viewPort: ViewPort) {
        particlePhysics.applyParticlePhysics(particles, viewPort, completedParticleResults)
        collision.applyParticleCollision(particles)
    }

    override fun draw(
        particles: ArrayList<Particle>,
        viewPort: ViewPort,
        timestamp: Double
    ): DrawResult {
        val foregroundSurface = getOrCreateForegroundSurface(viewPort)
        val foregroundCanvas = foregroundSurface.canvas
        foregroundCanvas.clear(0x00000000)
        drawParticles(particles, viewPort, foregroundCanvas, mapItemImage)
        val foregroundImage = foregroundSurface.makeImageSnapshot()
        runtimeService.lastPaintTime = timestamp
        return DrawResult(
            foregroundImage = WasmJsImage(foregroundImage),
        )
    }

    protected open fun drawParticles(
        particles: ArrayList<Particle>,
        viewPort: ViewPort,
        canvas: Canvas,
        mapItemImage: CommonImage?
    ) {
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        val groups = HashMap<Int, MutableList<Particle>>(16)
        val itemReturnParticles = ArrayList<Particle>()
        val blobProjectileParticles = ArrayList<Particle>()
        var particleIndex = 0
        val particleCount = particles.size
        while (particleIndex < particleCount) {
            val particle = particles[particleIndex]
            if (!particle.cullingCheck(viewPort)) {
                particleIndex++
                continue
            }
            if (particle.effect == ParticleEffect.ITEM_RETURN) {
                itemReturnParticles.add(particle)
                particleIndex++
                continue
            }
            if (particle.effect == ParticleEffect.BLOB_PROJECTILE) {
                blobProjectileParticles.add(particle)
                particleIndex++
                continue
            }
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
        drawGroups(groups, viewPortOffsetX, viewPortOffsetY, canvas)
        if (blobProjectileParticles.isNotEmpty()) {
            drawProjectileBlobs(blobProjectileParticles, viewPortOffsetX, viewPortOffsetY, canvas)
        }
        if (mapItemImage != null) {
            val mapItemSkia = (mapItemImage as WasmJsImage).image
            val sourceWidth = mapItemFrameWidth.toFloat()
            val sourceHeight = mapItemFrameHeight.toFloat()
            var itemIndex = 0
            while (itemIndex < itemReturnParticles.size) {
                val particle = itemReturnParticles[itemIndex]
                val localX = particle.x.toFloat() - viewPortOffsetX
                val localY = particle.y.toFloat() - viewPortOffsetY
                canvas.drawImageRect(
                    image = mapItemSkia,
                    srcLeft = 0f,
                    srcTop = 0f,
                    srcRight = sourceWidth,
                    srcBottom = sourceHeight,
                    dstLeft = localX - sourceWidth / 2f,
                    dstTop = localY - sourceHeight / 2f,
                    dstRight = localX + sourceWidth / 2f,
                    dstBottom = localY + sourceHeight / 2f,
                    samplingMode = SamplingMode.LINEAR,
                    paint = mapItemReturnPaint,
                    strict = true
                )
                itemIndex++
            }
        }
    }

    private fun drawGroups(
        groups: Map<Int, MutableList<Particle>>,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        val groupIterator = groups.entries.iterator()
        while (groupIterator.hasNext()) {
            val entry = groupIterator.next()
            val color = entry.key
            val particleList = entry.value
            val builder = PathBuilder()
            var particleIndex = 0
            while (particleIndex < particleList.size) {
                val particle = particleList[particleIndex]
                val x = particle.x.toFloat() - viewPortOffsetX
                val y = particle.y.toFloat() - viewPortOffsetY
                if (particle.shape == ParticleShape.CIRCLE) {
                    val radius = particle.radius.toFloat()
                    val diameter = radius * 2f
                    builder.addOval(
                        Rect.makeXYWH(
                            x - radius,
                            y - radius,
                            diameter,
                            diameter
                        )
                    )
                } else {
                    val width = particle.width.toFloat()
                    val height = particle.height.toFloat()
                    builder.addRect(Rect.makeXYWH(x - width / 2f, y - height / 2f, width, height))
                }
                particleIndex++
            }
            val batchPath = builder.detach()
            particlePaint.color = color
            canvas.drawPath(batchPath, particlePaint)
            batchPath.close()
        }
    }

    private fun drawProjectileBlobs(
        particles: List<Particle>,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        var particleIndex = 0
        while (particleIndex < particles.size) {
            val particle = particles[particleIndex]
            val centerX = particle.x.toFloat() - viewPortOffsetX
            val centerY = particle.y.toFloat() - viewPortOffsetY
            val baseRadius = particle.radius.toFloat()
            val age = particle.age.toFloat()
            val lobePointXs = FloatArray(CommonParticleFactory.PROJECTILE_BLOB_LOBE_COUNT)
            val lobePointYs = FloatArray(CommonParticleFactory.PROJECTILE_BLOB_LOBE_COUNT)
            var lobeIndex = 0
            while (lobeIndex < CommonParticleFactory.PROJECTILE_BLOB_LOBE_COUNT) {
                val lobeAngle = (lobeIndex.toFloat() / CommonParticleFactory.PROJECTILE_BLOB_LOBE_COUNT.toFloat()) * (2f * PI.toFloat())
                val lobeFrequency = CommonParticleFactory.PROJECTILE_BLOB_BASE_FREQUENCY + (lobeIndex * CommonParticleFactory.PROJECTILE_BLOB_FREQUENCY_STEP)
                val lobePhase = lobeIndex * CommonParticleFactory.PROJECTILE_BLOB_PHASE_STEP
                val lobeWobble = sin((age * lobeFrequency) + lobePhase)
                val lobeRadius = baseRadius * (1f + (CommonParticleFactory.PROJECTILE_BLOB_AMPLITUDE_RATIO * lobeWobble))
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
            particleIndex++
        }
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
            val controlPoint1X = lobePointXs[lobeIndex] + (lobePointXs[nextIndex] - lobePointXs[previousIndex]) / CommonParticleFactory.PROJECTILE_BLOB_SPLINE_TENSION
            val controlPoint1Y = lobePointYs[lobeIndex] + (lobePointYs[nextIndex] - lobePointYs[previousIndex]) / CommonParticleFactory.PROJECTILE_BLOB_SPLINE_TENSION
            val controlPoint2X = lobePointXs[nextIndex] - (lobePointXs[afterNextIndex] - lobePointXs[lobeIndex]) / CommonParticleFactory.PROJECTILE_BLOB_SPLINE_TENSION
            val controlPoint2Y = lobePointYs[nextIndex] - (lobePointYs[afterNextIndex] - lobePointYs[lobeIndex]) / CommonParticleFactory.PROJECTILE_BLOB_SPLINE_TENSION
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
