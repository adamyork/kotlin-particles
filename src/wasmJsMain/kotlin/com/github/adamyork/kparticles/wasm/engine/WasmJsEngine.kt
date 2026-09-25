package com.github.adamyork.kparticles.wasm.engine

import androidx.compose.ui.graphics.asSkiaBitmap
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
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
    platformInterop: PlatformInterop
) : CommonEngine(
    physics,
    particles,
    assetService,
    runtimeService,
    platformInterop
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
        val vpX = viewPort.x.toFloat()
        val vpY = viewPort.y.toFloat()
        val groups = mutableMapOf<Int, MutableList<Particle>>()
        val itemReturnParticles = mutableListOf<Particle>()
        for (particle in particles) {
            if (!particle.cullingCheck(viewPort)) {
                continue
            }
            if (particle.type == ParticleType.ITEM_RETURN) {
                itemReturnParticles.add(particle)
                continue
            }
            val alpha = (particle.alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
            val color = Color.makeARGB(
                alpha,
                (particle.color.red * 255).toInt(),
                (particle.color.green * 255).toInt(),
                (particle.color.blue * 255).toInt()
            )
            groups.getOrPut(color) { mutableListOf() }.add(particle)
        }

        drawGroups(groups, vpX, vpY, canvas)

        if (mapItemImage != null) {
            val mapItemSkia = (mapItemImage as WasmJsImage).image
            val srcWidth = mapItemFrameWidth.toFloat()
            val srcHeight = mapItemFrameHeight.toFloat()
            for ((_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, x, y) in itemReturnParticles) {
                val localX = x.toFloat() - vpX
                val localY = y.toFloat() - vpY
                canvas.drawImageRect(
                    image = mapItemSkia,
                    srcLeft = 0f,
                    srcTop = 0f,
                    srcRight = srcWidth,
                    srcBottom = srcHeight,
                    dstLeft = localX - srcWidth / 2f,
                    dstTop = localY - srcHeight / 2f,
                    dstRight = localX + srcWidth / 2f,
                    dstBottom = localY + srcHeight / 2f,
                    samplingMode = SamplingMode.LINEAR,
                    paint = mapItemReturnPaint,
                    strict = true
                )
            }
        }
    }

    private fun drawGroups(groups: Map<Int, MutableList<Particle>>, vpX: Float, vpY: Float, canvas: Canvas) {
        for ((color, particleList) in groups) {
            val builder = PathBuilder()
            for ((_, _, shape, _, _, _, _, _, _, _, _, width, height, _, _, radius, _, _, x1, y1) in particleList) {
                val x = x1.toFloat() - vpX
                val y = y1.toFloat() - vpY
                if (shape == ParticleShape.CIRCLE) {
                    val diameter = (radius * 2.0).toFloat()
                    builder.addOval(
                        Rect.makeXYWH(
                            x - radius.toFloat(),
                            y - radius.toFloat(),
                            diameter,
                            diameter
                        )
                    )
                } else {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    builder.addRect(Rect.makeXYWH(x - w / 2f, y - h / 2f, w, h))
                }
            }
            val batchPath = builder.detach()
            particlePaint.color = color
            canvas.drawPath(batchPath, particlePaint)
            batchPath.close()
        }
    }

}
