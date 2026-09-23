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
    }

    override fun draw(
        particles: List<Particle>,
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
        particles: List<Particle>,
        viewPort: ViewPort,
        canvas: Canvas,
        mapItemImage: CommonImage?
    ) {
        val particles = particles
        val vpX = viewPort.x.toFloat()
        val vpY = viewPort.y.toFloat()
        fun drawGrouped(includeCollision: Boolean) {
            val groups = mutableMapOf<Int, MutableList<Particle>>()
            for (particle in particles) {
                if (!particle.cullingCheck(viewPort) || particle.type == ParticleType.ITEM_RETURN) {
                    continue
                }
                if (includeCollision != (particle.type == ParticleType.COLLISION)) {
                    continue
                }
                val lifetime = if (particle.lifetime <= 0) 1 else particle.lifetime
                val ageProgress = (particle.age.toFloat() / lifetime.toFloat()).coerceIn(0f, 1f)
                val alphaMultiplier = when {
                    particle.type == ParticleType.PROJECTILE -> 1.0f
                    ageProgress < 0.33f -> 1.0f
                    ageProgress < 0.66f -> 0.66f
                    else -> 0.33f
                }
                val alpha = (particle.color.alpha.coerceIn(0f, 1f) * alphaMultiplier * 255f).toInt().coerceIn(0, 255)
                val color = Color.makeARGB(
                    alpha,
                    (particle.color.red * 255).toInt(),
                    (particle.color.green * 255).toInt(),
                    (particle.color.blue * 255).toInt()
                )
                groups.getOrPut(color) { mutableListOf() }.add(particle)
            }

            for ((color, particleList) in groups) {
                val builder = PathBuilder()
                for (particle in particleList) {
                    val x = particle.x.toFloat() - vpX
                    val y = particle.y.toFloat() - vpY
                    if (particle.shape == ParticleShape.CIRCLE) {
                        builder.addOval(Rect.makeXYWH(x, y, particle.width.toFloat(), particle.height.toFloat()))
                    } else {
                        builder.addRect(Rect.makeXYWH(x, y, particle.width.toFloat(), particle.height.toFloat()))
                    }
                }
                val batchPath = builder.detach()
                particlePaint.color = color
                canvas.drawPath(batchPath, particlePaint)
                batchPath.close()
            }
        }

        drawGrouped(includeCollision = false)

        if (mapItemImage != null) {
            val mapItemSkia = (mapItemImage as WasmJsImage).image
            for (particle in particles) {
                if (particle.type != ParticleType.ITEM_RETURN || !particle.cullingCheck(viewPort)) {
                    continue
                }
                val localX = particle.x.toFloat() - vpX
                val localY = particle.y.toFloat() - vpY
                canvas.drawImageRect(
                    image = mapItemSkia,
                    srcLeft = 0f,
                    srcTop = 0f,
                    srcRight = mapItemSkia.width.toFloat(),
                    srcBottom = mapItemSkia.height.toFloat(),
                    dstLeft = localX,
                    dstTop = localY,
                    dstRight = localX + particle.width.toFloat(),
                    dstBottom = localY + particle.height.toFloat(),
                    samplingMode = SamplingMode.LINEAR,
                    paint = mapItemReturnPaint,
                    strict = true
                )
            }
        }

        drawGrouped(includeCollision = true)
    }

}
