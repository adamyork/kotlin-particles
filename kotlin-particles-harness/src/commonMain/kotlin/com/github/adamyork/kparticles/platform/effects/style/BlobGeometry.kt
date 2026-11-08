package com.github.adamyork.kparticles.platform.effects.style

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
object BlobGeometry {

    const val SEGMENT_POINT_FLOATS: Int = 6

    fun writeLobePoints(
        centerX: Float,
        centerY: Float,
        baseRadius: Float,
        age: Float,
        lobePointXs: FloatArray,
        lobePointYs: FloatArray
    ) {
        val lobeCount = BlobStyleParameters.LOBE_COUNT
        var lobeIndex = 0
        while (lobeIndex < lobeCount) {
            val lobeAngle = (lobeIndex.toFloat() / lobeCount.toFloat()) * (2f * PI.toFloat())
            val lobeFrequency = BlobStyleParameters.BASE_FREQUENCY +
                    (lobeIndex * BlobStyleParameters.FREQUENCY_STEP)
            val lobePhase = lobeIndex * BlobStyleParameters.PHASE_STEP
            val lobeWobble = sin((age * lobeFrequency) + lobePhase)
            val lobeRadius = baseRadius * (1f + (BlobStyleParameters.AMPLITUDE_RATIO * lobeWobble))
            lobePointXs[lobeIndex] = centerX + (cos(lobeAngle) * lobeRadius)
            lobePointYs[lobeIndex] = centerY + (sin(lobeAngle) * lobeRadius)
            lobeIndex++
        }
    }

    fun writeSegmentControlPoints(
        lobePointXs: FloatArray,
        lobePointYs: FloatArray,
        segmentIndex: Int,
        segmentPoints: FloatArray
    ) {
        val lobeCount = lobePointXs.size
        val previousIndex = (segmentIndex - 1 + lobeCount) % lobeCount
        val nextIndex = (segmentIndex + 1) % lobeCount
        val afterNextIndex = (segmentIndex + 2) % lobeCount
        val tension = BlobStyleParameters.SPLINE_TENSION
        segmentPoints[0] = lobePointXs[segmentIndex] + (lobePointXs[nextIndex] - lobePointXs[previousIndex]) / tension
        segmentPoints[1] = lobePointYs[segmentIndex] + (lobePointYs[nextIndex] - lobePointYs[previousIndex]) / tension
        segmentPoints[2] = lobePointXs[nextIndex] - (lobePointXs[afterNextIndex] - lobePointXs[segmentIndex]) / tension
        segmentPoints[3] = lobePointYs[nextIndex] - (lobePointYs[afterNextIndex] - lobePointYs[segmentIndex]) / tension
        segmentPoints[4] = lobePointXs[nextIndex]
        segmentPoints[5] = lobePointYs[nextIndex]
    }

}
