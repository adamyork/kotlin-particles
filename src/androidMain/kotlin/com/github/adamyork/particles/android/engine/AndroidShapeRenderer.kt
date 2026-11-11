package com.github.adamyork.particles.android.engine

import android.graphics.*
import androidx.core.graphics.withRotation
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import kotlin.math.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class AndroidShapeRenderer : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = ParticleRenderStyle.SHAPE
    override val drawOrder: Int = CpuDrawOrder.SHAPES
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawSimpleShapes
    private companion object {
        const val RADIANS_TO_DEGREES: Float = (180.0 / PI).toFloat()
    }

    private val particlePaint: Paint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }

    private val particleRectF = RectF()

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
                    is ParticleRectDimensions -> {
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

}
