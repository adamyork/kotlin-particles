package com.github.adamyork.kparticles.wasm.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.style.BlobGeometry
import com.github.adamyork.kparticles.platform.effects.style.BlobStyleParameters
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.DiagnosticPhase
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
class WasmBlobRenderer(
    private val engineDiagnostics: EngineDiagnostics
) : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = EffectStyles.BLOB
    override val drawOrder: Int = CpuDrawOrder.COMPLEX_SHAPES
    override val diagnosticPhase: DiagnosticPhase = DiagnosticPhase.DRAW_COMPLEX_SHAPE
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawBlobs
    private fun drawBlobs(drawFunctionData: DrawFunctionData<Canvas>) {
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (generator in drawFunctionData.generators) {
            val generatorMark = engineDiagnostics.startTimer()
            val particles = generator.particles
            var particleIndex = 0
            while (particleIndex < particles.size) {
                val particle = particles[particleIndex]
                particleIndex++
                if (!particle.cullingCheck(viewPort)) continue
                drawProjectileBlob(particle, viewPortOffsetX, viewPortOffsetY, canvas)
            }
            engineDiagnostics.stopEffectTimer(generator.effect, generatorMark)
        }
    }

    private val projectileBlobPaint = Paint().apply { isAntiAlias = true; mode = PaintMode.FILL }

    private val blobLobePointXs = FloatArray(BlobStyleParameters.LOBE_COUNT)

    private val blobLobePointYs = FloatArray(BlobStyleParameters.LOBE_COUNT)

    private val blobSegmentPoints = FloatArray(BlobGeometry.SEGMENT_POINT_FLOATS)

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
