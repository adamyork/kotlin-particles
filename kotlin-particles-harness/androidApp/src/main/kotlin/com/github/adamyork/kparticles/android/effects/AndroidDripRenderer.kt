package com.github.adamyork.kparticles.android.effects

import android.graphics.*
import androidx.core.graphics.createBitmap
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.style.CpuRendererConstants
import com.github.adamyork.kparticles.platform.effects.style.DripGeometry
import com.github.adamyork.kparticles.platform.effects.style.DripStyleSettings
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.DiagnosticPhase
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import kotlin.math.*
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AndroidDripRenderer(
    private val dripStyleSettings: DripStyleSettings
) : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = EffectStyles.DRIP
    override val drawOrder: Int = CpuDrawOrder.COMPLEX_SHAPES
    override val diagnosticPhase: DiagnosticPhase = DiagnosticPhase.DRAW_COMPLEX_SHAPE
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawAllDrips
    private fun drawAllDrips(drawFunctionData: DrawFunctionData<Canvas>) {
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in drawFunctionData.generators) {
            val particles = generator.particles
            for (particleIndex in particles.indices) {
                val particle = particles[particleIndex]
                if (!particle.cullingCheck(viewPort)) continue
                if (particle.lifecycle.isVisible()) {
                    drawDrip(particle, viewPortOffsetX, viewPortOffsetY, canvas)
                }
            }
        }
    }

    private val spritePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val dripGeometry = DripGeometry(dripStyleSettings)

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

    private var dripSpecularBitmap: Bitmap? = null

    private var dripSpecularAlpha = -1

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
        val glossIntensity = dripStyleSettings.dripGlossIntensity.toFloat()
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

}
