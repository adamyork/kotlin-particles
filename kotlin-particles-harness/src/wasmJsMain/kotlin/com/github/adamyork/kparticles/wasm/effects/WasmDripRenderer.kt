package com.github.adamyork.kparticles.wasm.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.kparticles.platform.effects.style.CpuRendererConstants
import com.github.adamyork.kparticles.platform.effects.style.DripGeometry
import com.github.adamyork.kparticles.platform.effects.style.DripStyleSettings
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import kotlin.math.*
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class WasmDripRenderer(
    private val dripStyleSettings: DripStyleSettings
) : CpuStyleRenderer<Canvas> {

    private companion object {
        const val DRIP_SPECULAR_FLOATS: Int = 3
        const val DRIP_VERTICAL_TOLERANCE: Float = 0.001f
    }

    override val renderStyle: ParticleRenderStyle = EffectStyles.DRIP
    override val drawOrder: Int = CpuDrawOrder.COMPLEX_SHAPES
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawAllDrips

    private val dripGeometry = DripGeometry(dripStyleSettings)

    private val dripFillPaint = Paint().apply { isAntiAlias = true }

    private val dripGlossStripeFillPaint = Paint().apply { isAntiAlias = true }

    private val dripGlossSpecularPaint = Paint().apply { isAntiAlias = true }

    private val dripBodyBuilder = PathBuilder()

    private val dripStripeBuilder = PathBuilder()

    private var dripSpecularBuffer = FloatArray(0)

    private var dripSpecularImage: Image? = null

    private var dripSpecularAlpha = -1

    private fun drawAllDrips(drawFunctionData: DrawFunctionData<Canvas>) {
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in drawFunctionData.generators) {
            drawDrips(generator.particles, viewPort, viewPortOffsetX, viewPortOffsetY, canvas)
        }
    }

    private fun drawDrips(
        particles: List<Particle>,
        viewPort: ParticleViewPort,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        canvas: Canvas
    ) {
        if (dripSpecularBuffer.size < particles.size * DRIP_SPECULAR_FLOATS) {
            dripSpecularBuffer = FloatArray(particles.size * DRIP_SPECULAR_FLOATS)
        }
        val glossIntensity = dripStyleSettings.dripGlossIntensity.toFloat()
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
            Gradient(Gradient.Colors(arrayOf(Color4f(Color.makeARGB(alpha, 255, 255, 255)), Color4f(Color.makeARGB(0, 255, 255, 255))), null, FilterTileMode.CLAMP))
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

}
