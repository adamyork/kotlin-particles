package com.github.adamyork.kparticles.android.effects

import android.graphics.*
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.style.BlobGeometry
import com.github.adamyork.kparticles.platform.effects.style.BlobStyleParameters
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class AndroidBlobRenderer : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = EffectStyles.BLOB
    override val drawOrder: Int = CpuDrawOrder.COMPLEX_SHAPES
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawBlobs

    private val projectileBlobPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val projectileBlobPath = Path()

    private val blobLobePointXs = FloatArray(BlobStyleParameters.LOBE_COUNT)

    private val blobLobePointYs = FloatArray(BlobStyleParameters.LOBE_COUNT)

    private val blobSegmentPoints = FloatArray(BlobGeometry.SEGMENT_POINT_FLOATS)

    private fun drawBlobs(drawFunctionData: DrawFunctionData<Canvas>) {
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in drawFunctionData.generators) {
            val particles = generator.particles
            for (particleIndex in particles.indices) {
                val particle = particles[particleIndex]
                if (!particle.cullingCheck(viewPort)) continue
                drawProjectileBlob(particle, viewPortOffsetX, viewPortOffsetY, canvas)
            }
        }
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
