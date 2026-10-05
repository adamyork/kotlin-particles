package com.github.adamyork.kparticles.android.engine

import android.graphics.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.createBitmap
import com.github.adamyork.kparticles.android.engine.data.AndroidImage
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.Collision
import com.github.adamyork.kparticles.platform.engine.CommonEngine
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.data.*
import com.github.adamyork.kparticles.platform.service.AbstractPlatformAssetService
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
open class AndroidEngine(
    particlePhysics: ParticlePhysics,
    collision: Collision,
    particleFactory: ParticleFactory,
    assetService: AssetService,
    runtimeService: RuntimeService,
    platformInterop: PlatformInterop
) : CommonEngine(
    particlePhysics,
    particleFactory,
    assetService,
    runtimeService,
    platformInterop,
    collision
) {

    private val logger = KotlinLogging.logger {}

    override var foregroundSurface: Any? = null

    override val mapElementPaint: Any = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    override val particlePaint: Any = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    override val mapItemReturnPaint: Any = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val particleSrcRect = Rect()
    private val particleRectF = RectF()

    override var mapItemImage: CommonImage = AndroidImage(
        AbstractPlatformAssetService.getTmpImageBitmap().asAndroidBitmap()
    )
    private var mapItemFrameWidth: Int = 1
    private var mapItemFrameHeight: Int = 1

    override fun getOrCreateForegroundSurface(viewPort: ViewPort): Any {
        val current = foregroundSurface as? Bitmap
        if (current == null || current.width != viewPort.width || current.height != viewPort.height) {
            foregroundSurface = createBitmap(viewPort.width, viewPort.height)
        }
        return foregroundSurface as Bitmap
    }

    override suspend fun initialize(collectibleAsset: ImageAsset) {
        logger.info { "Initializing CPU engine" }
        withContext(Dispatchers.Default) {
            mapItemImage = AndroidImage(collectibleAsset.imageAndBytes.imageBitmap.asAndroidBitmap())
            mapItemFrameWidth = collectibleAsset.width
            mapItemFrameHeight = collectibleAsset.height
        }
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
        val foregroundBitmap = getOrCreateForegroundSurface(viewPort) as Bitmap
        val foregroundCanvas = Canvas(foregroundBitmap)
        foregroundCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawParticles(particles, viewPort, foregroundCanvas, mapItemImage)
        runtimeService.lastPaintTime = timestamp
        return DrawResult(
            foregroundImage = AndroidImage(foregroundBitmap),
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
        val particlePaint = particlePaint as Paint
        for (i in particles.indices) {
            val particle = particles[i]
            if (!particle.cullingCheck(viewPort)) continue
            if (particle.type == ParticleType.ITEM_RETURN) {
                if (mapItemImage is AndroidImage) {
                    val localX = particle.x.toFloat() - vpX
                    val localY = particle.y.toFloat() - vpY
                    particleSrcRect.set(0, 0, mapItemFrameWidth, mapItemFrameHeight)
                    particleRectF.set(
                        localX,
                        localY,
                        localX + particle.width.toFloat(),
                        localY + particle.height.toFloat()
                    )
                    canvas.drawBitmap(
                        mapItemImage.bitmap,
                        particleSrcRect,
                        particleRectF,
                        mapItemReturnPaint as Paint
                    )
                }
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
            particlePaint.color = particleColorArgb(particle, alphaMultiplier)
            val x = particle.x.toFloat() - vpX
            val y = particle.y.toFloat() - vpY
            particleRectF.set(x, y, x + particle.width.toFloat(), y + particle.height.toFloat())
            if (particle.shape == ParticleShape.CIRCLE) {
                canvas.drawOval(particleRectF, particlePaint)
            } else {
                canvas.drawRect(particleRectF, particlePaint)
            }
        }
    }

    protected fun particleColorArgb(particle: Particle, alphaMultiplier: Float): Int {
        val alpha = (particle.color.alpha.coerceIn(0f, 1f) * alphaMultiplier * 255f).toInt().coerceIn(0, 255)
        val red = (particle.color.red * 255f).toInt().coerceIn(0, 255)
        val green = (particle.color.green * 255f).toInt().coerceIn(0, 255)
        val blue = (particle.color.blue * 255f).toInt().coerceIn(0, 255)
        return Color.argb(alpha, red, green, blue)
    }

}
