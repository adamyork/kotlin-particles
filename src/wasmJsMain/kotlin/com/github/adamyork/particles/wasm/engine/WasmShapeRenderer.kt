package com.github.adamyork.particles.wasm.engine

import com.github.adamyork.particles.engine.CpuDrawOrder
import com.github.adamyork.particles.engine.CpuStyleRenderer
import com.github.adamyork.particles.engine.EngineDiagnostics
import com.github.adamyork.particles.engine.ParticleDrawFunction
import com.github.adamyork.particles.engine.data.*
import com.github.adamyork.particles.engine.data.DiagnosticPhase
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import kotlin.math.*
import org.jetbrains.skia.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class WasmShapeRenderer(
    private val engineDiagnostics: EngineDiagnostics
) : CpuStyleRenderer<Canvas> {

    override val renderStyle: ParticleRenderStyle = ParticleRenderStyle.SHAPE
    override val drawOrder: Int = CpuDrawOrder.SHAPES
    override val diagnosticPhase: DiagnosticPhase = DiagnosticPhase.DRAW_SIMPLE_SHAPE
    override val drawFunction: ParticleDrawFunction<Canvas> = ::drawSimpleShapes
    private val particlePaint = Paint().apply { isAntiAlias = false; mode = PaintMode.FILL }

    private val simpleShapeGroups = HashMap<Int, ArrayList<Particle>>(64)

    private val simpleShapeListPool = ArrayList<ArrayList<Particle>>()

    private val simpleShapeBuilder = PathBuilder()

    private fun drawSimpleShapes(drawFunctionData: DrawFunctionData<Canvas>) {
        val generators = drawFunctionData.generators
        val viewPort = drawFunctionData.viewPort
        val canvas = drawFunctionData.canvas
        val viewPortOffsetX = viewPort.x.toFloat()
        val viewPortOffsetY = viewPort.y.toFloat()
        for (retiredList in simpleShapeGroups.values) {
            retiredList.clear()
            simpleShapeListPool.add(retiredList)
        }
        simpleShapeGroups.clear()
        val groups = simpleShapeGroups
        val groupingMark = engineDiagnostics.startTimer()
        var culledCount = 0
        var drawnCount = 0
        for (generator in generators) {
            val particles = generator.particles
            var particleIndex = 0
            while (particleIndex < particles.size) {
                val particle = particles[particleIndex]
                particleIndex++
                if (!particle.cullingCheck(viewPort)) {
                    culledCount++
                    continue
                }
                drawnCount++
                val color = particle.appearance.packedArgb()
                val bucket = groups[color]
                if (bucket == null) {
                    val newBucket = if (simpleShapeListPool.isEmpty()) {
                        ArrayList()
                    } else {
                        simpleShapeListPool.removeAt(simpleShapeListPool.lastIndex)
                    }
                    newBucket.add(particle)
                    groups[color] = newBucket
                } else {
                    bucket.add(particle)
                }
            }
        }
        engineDiagnostics.stopTimer(DiagnosticPhase.SIMPLE_GROUPING, groupingMark)
        engineDiagnostics.count(DiagnosticCounter.SIMPLE_CULLED, culledCount)
        engineDiagnostics.count(DiagnosticCounter.SIMPLE_DRAWN, drawnCount)
        engineDiagnostics.count(DiagnosticCounter.SIMPLE_COLOR_GROUPS, groups.size)
        val fillMark = engineDiagnostics.startTimer()
        val groupIterator = groups.entries.iterator()
        while (groupIterator.hasNext()) {
            val entry = groupIterator.next()
            val particleList = entry.value
            val builder = simpleShapeBuilder
            var groupParticleIndex = 0
            while (groupParticleIndex < particleList.size) {
                val particle = particleList[groupParticleIndex]
                val x = particle.transform.position.x.toFloat() - viewPortOffsetX
                val y = particle.transform.position.y.toFloat() - viewPortOffsetY
                when (val dimensions = particle.dimensions) {
                    is CircleDimensions -> {
                        val radius = dimensions.radius.toFloat()
                        builder.addOval(x - radius, y - radius, x + radius, y + radius)
                    }
                    is ParticleRectDimensions -> {
                        val width = dimensions.width.toFloat()
                        val height = dimensions.height.toFloat()
                        if (particle.transform.angle == 0.0) {
                            builder.addRect(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)
                        } else {
                            val halfWidth = width / 2f
                            val halfHeight = height / 2f
                            val cosine = cos(particle.transform.angle).toFloat()
                            val sine = sin(particle.transform.angle).toFloat()
                            builder.moveTo(x + (-halfWidth * cosine + halfHeight * sine), y + (-halfWidth * sine - halfHeight * cosine))
                            builder.lineTo(x + (halfWidth * cosine + halfHeight * sine), y + (halfWidth * sine - halfHeight * cosine))
                            builder.lineTo(x + (halfWidth * cosine - halfHeight * sine), y + (halfWidth * sine + halfHeight * cosine))
                            builder.lineTo(x + (-halfWidth * cosine - halfHeight * sine), y + (-halfWidth * sine + halfHeight * cosine))
                            builder.closePath()
                        }
                    }
                }
                groupParticleIndex++
            }
            val batchPath = builder.detach()
            particlePaint.color = entry.key
            canvas.drawPath(batchPath, particlePaint)
            batchPath.close()
        }
        engineDiagnostics.stopTimer(DiagnosticPhase.SIMPLE_FILL, fillMark)
    }

}
