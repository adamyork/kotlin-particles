package com.github.adamyork.kparticles.wasm.engine

import androidx.compose.ui.graphics.asSkiaBitmap
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.Collision
import com.github.adamyork.kparticles.platform.engine.CommonEngine
import com.github.adamyork.kparticles.platform.engine.Particles
import com.github.adamyork.kparticles.platform.engine.Physics
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import com.github.adamyork.kparticles.wasm.engine.data.WasmJsImage
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
open class WasmJsEngine(
    physics: Physics,
    particles: Particles,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop,
    collision: Collision,
) : CommonEngine(
    physics,
    particles,
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

    override fun manageMapParticles(particles: ArrayList<Particle>, viewPort: ViewPort) {
        physics.applyParticlePhysics(particles, viewPort, completedParticleResults)
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
        var particleIndex = 0
        val particleCount = particles.size
        while (particleIndex < particleCount) {
            val particle = particles[particleIndex]
            if (!particle.cullingCheck(viewPort)) {
                particleIndex++
                continue
            }
            if (particle.type == ParticleType.ITEM_RETURN) {
                itemReturnParticles.add(particle)
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

}
