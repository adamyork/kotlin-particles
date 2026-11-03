package com.github.adamyork.kparticles.simulation

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.BlobGeometry
import com.github.adamyork.kparticles.platform.engine.DripGeometry
import com.github.adamyork.kparticles.platform.engine.data.CircleDimensions
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.data.ParticleAppearance
import com.github.adamyork.kparticles.platform.engine.data.ParticleDynamics
import com.github.adamyork.kparticles.platform.engine.data.ParticleLifecycle
import com.github.adamyork.kparticles.platform.engine.data.ParticleMaterial
import com.github.adamyork.kparticles.platform.engine.data.ParticleTransform
import com.github.adamyork.kparticles.platform.engine.data.ParticleVector
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.service.CommonPhysicsSettingsService
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class RendererGeometryTest {

    private class DripCase(
        val label: String,
        val originX: Double,
        val originY: Double,
        val headX: Double,
        val headY: Double,
        val radius: Double
    )

    private val dripCases = listOf(
        DripCase("long vertical", 400.0, 100.0, 400.0, 340.0, 6.0),
        DripCase("long diagonal", 120.0, 80.0, 190.0, 310.0, 5.5),
        DripCase("long horizontal", 50.0, 200.0, 260.0, 200.0, 4.0),
        DripCase("short streak inside neck", 300.0, 300.0, 301.0, 303.5, 7.0),
        DripCase("zero length", 640.0, 360.0, 640.0, 360.0, 3.0),
        DripCase("up and left", 500.0, 500.0, 410.0, 380.0, 8.25)
    )

    @Test
    fun blobLobePointsMatchOriginalRendererMath() {
        for (age in listOf(0f, 0.5f, 13.75f, 421.125f)) {
            for (baseRadius in listOf(2f, 9.5f, 31.25f)) {
                val actualXs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
                val actualYs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
                BlobGeometry.writeLobePoints(412.5f, 233.25f, baseRadius, age, actualXs, actualYs)
                val expectedXs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
                val expectedYs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
                referenceLobePoints(412.5f, 233.25f, baseRadius, age, expectedXs, expectedYs)
                for (lobeIndex in expectedXs.indices) {
                    assertEquals(expectedXs[lobeIndex], actualXs[lobeIndex], "x age=$age radius=$baseRadius lobe=$lobeIndex")
                    assertEquals(expectedYs[lobeIndex], actualYs[lobeIndex], "y age=$age radius=$baseRadius lobe=$lobeIndex")
                }
            }
        }
    }

    @Test
    fun blobSegmentControlPointsMatchOriginalRendererMath() {
        val lobePointXs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
        val lobePointYs = FloatArray(BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT)
        referenceLobePoints(412.5f, 233.25f, 17.5f, 88.5f, lobePointXs, lobePointYs)
        val segmentPoints = FloatArray(BlobGeometry.SEGMENT_POINT_FLOATS)
        for (segmentIndex in lobePointXs.indices) {
            BlobGeometry.writeSegmentControlPoints(lobePointXs, lobePointYs, segmentIndex, segmentPoints)
            val expectedPoints = referenceSegmentControlPoints(lobePointXs, lobePointYs, segmentIndex)
            for (pointIndex in expectedPoints.indices) {
                assertEquals(expectedPoints[pointIndex], segmentPoints[pointIndex], "segment=$segmentIndex point=$pointIndex")
            }
        }
    }

    @Test
    fun dripGeometryMatchesOriginalRendererMath() {
        val settings = CommonPhysicsSettingsService()
        val dripGeometry = DripGeometry(settings)
        for (dripCase in dripCases) {
            val particle = dripParticle(dripCase)
            dripGeometry.update(particle, 17.5f, 42.25f)
            val expected = referenceDrip(particle, 17.5f, 42.25f, settings)
            val label = dripCase.label
            assertEquals(expected.getValue("originX"), dripGeometry.originX, "$label originX")
            assertEquals(expected.getValue("originY"), dripGeometry.originY, "$label originY")
            assertEquals(expected.getValue("headX"), dripGeometry.headX, "$label headX")
            assertEquals(expected.getValue("headY"), dripGeometry.headY, "$label headY")
            assertEquals(expected.getValue("beadRadius"), dripGeometry.beadRadius, "$label beadRadius")
            assertEquals(expected.getValue("trailRadius"), dripGeometry.trailRadius, "$label trailRadius")
            assertEquals(expected.getValue("streakLength"), dripGeometry.streakLength, "$label streakLength")
            assertEquals(expected.getValue("directionX"), dripGeometry.directionX, "$label directionX")
            assertEquals(expected.getValue("directionY"), dripGeometry.directionY, "$label directionY")
            assertEquals(expected.getValue("perpendicularX"), dripGeometry.perpendicularX, "$label perpendicularX")
            assertEquals(expected.getValue("neckLength"), dripGeometry.neckLength, "$label neckLength")
            assertEquals(expected.getValue("tailRadius"), dripGeometry.tailRadius, "$label tailRadius")
            assertEquals(expected.getValue("neckStartX"), dripGeometry.neckStartX, "$label neckStartX")
            assertEquals(expected.getValue("neckStartY"), dripGeometry.neckStartY, "$label neckStartY")
            assertEquals(expected.getValue("hasStripe") == 1f, dripGeometry.hasStripe, "$label hasStripe")
            assertEquals(expected.getValue("stripeOffset"), dripGeometry.stripeOffset, "$label stripeOffset")
            assertEquals(expected.getValue("stripeRadius"), dripGeometry.stripeRadius, "$label stripeRadius")
            assertEquals(expected.getValue("specularCenterX"), dripGeometry.specularCenterX, "$label specularCenterX")
            assertEquals(expected.getValue("specularCenterY"), dripGeometry.specularCenterY, "$label specularCenterY")
            assertEquals(expected.getValue("specularRadius"), dripGeometry.specularRadius, "$label specularRadius")
        }
    }

    @Test
    fun dripGeometryCoversStripeAndNoStripeCases() {
        val dripGeometry = DripGeometry(CommonPhysicsSettingsService())
        dripGeometry.update(dripParticle(dripCases.first { it.label == "long vertical" }), 0f, 0f)
        assertEquals(true, dripGeometry.hasStripe, "long streak shows a stripe")
        dripGeometry.update(dripParticle(dripCases.first { it.label == "short streak inside neck" }), 0f, 0f)
        assertEquals(false, dripGeometry.hasStripe, "short streak hides the stripe")
    }

    private fun dripParticle(dripCase: DripCase): Particle = Particle(
        id = 1L,
        lifecycle = ParticleLifecycle(lifetime = 10000.0),
        appearance = ParticleAppearance(color = Color.White),
        dimensions = CircleDimensions(radius = dripCase.radius, maxRadius = dripCase.radius),
        transform = ParticleTransform(
            position = ParticleVector(dripCase.headX, dripCase.headY, 0.0),
            origin = ParticleVector(dripCase.originX, dripCase.originY, 0.0)
        ),
        material = ParticleMaterial(density = 1.0, restitution = 0.5),
        dynamics = ParticleDynamics(velocity = ParticleVector(0.0, 0.0, 0.0))
    )

    private fun referenceLobePoints(
        centerX: Float,
        centerY: Float,
        baseRadius: Float,
        age: Float,
        lobePointXs: FloatArray,
        lobePointYs: FloatArray
    ) {
        val lobeCount = BlobProjectileGenerator.PROJECTILE_BLOB_LOBE_COUNT
        var lobeIndex = 0
        while (lobeIndex < lobeCount) {
            val lobeAngle = (lobeIndex.toFloat() / lobeCount.toFloat()) * (2f * PI.toFloat())
            val lobeFrequency = BlobProjectileGenerator.PROJECTILE_BLOB_BASE_FREQUENCY +
                    (lobeIndex * BlobProjectileGenerator.PROJECTILE_BLOB_FREQUENCY_STEP)
            val lobePhase = lobeIndex * BlobProjectileGenerator.PROJECTILE_BLOB_PHASE_STEP
            val lobeWobble = sin((age * lobeFrequency) + lobePhase)
            val lobeRadius = baseRadius * (1f + (BlobProjectileGenerator.PROJECTILE_BLOB_AMPLITUDE_RATIO * lobeWobble))
            lobePointXs[lobeIndex] = centerX + (cos(lobeAngle) * lobeRadius)
            lobePointYs[lobeIndex] = centerY + (sin(lobeAngle) * lobeRadius)
            lobeIndex++
        }
    }

    private fun referenceSegmentControlPoints(lobePointXs: FloatArray, lobePointYs: FloatArray, lobeIndex: Int): FloatArray {
        val lobeCount = lobePointXs.size
        val previousIndex = (lobeIndex - 1 + lobeCount) % lobeCount
        val nextIndex = (lobeIndex + 1) % lobeCount
        val afterNextIndex = (lobeIndex + 2) % lobeCount
        val tension = BlobProjectileGenerator.PROJECTILE_BLOB_SPLINE_TENSION
        val controlPoint1X = lobePointXs[lobeIndex] + (lobePointXs[nextIndex] - lobePointXs[previousIndex]) / tension
        val controlPoint1Y = lobePointYs[lobeIndex] + (lobePointYs[nextIndex] - lobePointYs[previousIndex]) / tension
        val controlPoint2X = lobePointXs[nextIndex] - (lobePointXs[afterNextIndex] - lobePointXs[lobeIndex]) / tension
        val controlPoint2Y = lobePointYs[nextIndex] - (lobePointYs[afterNextIndex] - lobePointYs[lobeIndex]) / tension
        return floatArrayOf(controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y, lobePointXs[nextIndex], lobePointYs[nextIndex])
    }

    private fun referenceDrip(
        particle: Particle,
        viewPortOffsetX: Float,
        viewPortOffsetY: Float,
        settings: CommonPhysicsSettingsService
    ): Map<String, Float> {
        val minimumLength = 0.0001f
        val originX = particle.transform.origin.x.toFloat() - viewPortOffsetX
        val originY = particle.transform.origin.y.toFloat() - viewPortOffsetY
        val headX = particle.transform.position.x.toFloat() - viewPortOffsetX
        val headY = particle.transform.position.y.toFloat() - viewPortOffsetY
        val beadRadius = particle.collisionRadius().toFloat()
        val trailRadius = beadRadius * settings.dripTrailRadiusRatio.toFloat()
        val streakLength = hypot(headX - originX, headY - originY)
        val directionX = if (streakLength > minimumLength) (headX - originX) / streakLength else 0f
        val directionY = if (streakLength > minimumLength) (headY - originY) / streakLength else 1f
        val perpendicularX = -directionY
        val neckSetting = max(beadRadius * settings.dripNeckLengthRatio.toFloat(), minimumLength)
        val neckLength = min(streakLength, neckSetting)
        val tailRadius = beadRadius + (trailRadius - beadRadius) * (streakLength / neckSetting).coerceIn(0f, 1f)
        val neckStartX = headX - directionX * neckLength
        val neckStartY = headY - directionY * neckLength
        val stripeOffset = trailRadius * settings.dripGlossStripeOffsetRatio.toFloat()
        val stripeRadius = trailRadius * settings.dripGlossStripeWidthRatio.toFloat()
        val specularOffset = beadRadius * settings.dripGlossSpecularOffsetRatio.toFloat()
        val specularCenterX = headX - directionX * specularOffset + perpendicularX * specularOffset
        val specularCenterY = headY - directionY * specularOffset + directionX * specularOffset
        val specularRadius = max(beadRadius * settings.dripGlossSpecularRadiusRatio.toFloat(), minimumLength)
        return mapOf(
            "originX" to originX,
            "originY" to originY,
            "headX" to headX,
            "headY" to headY,
            "beadRadius" to beadRadius,
            "trailRadius" to trailRadius,
            "streakLength" to streakLength,
            "directionX" to directionX,
            "directionY" to directionY,
            "perpendicularX" to perpendicularX,
            "neckLength" to neckLength,
            "tailRadius" to tailRadius,
            "neckStartX" to neckStartX,
            "neckStartY" to neckStartY,
            "hasStripe" to (if (streakLength - neckLength > 0.5f) 1f else 0f),
            "stripeOffset" to stripeOffset,
            "stripeRadius" to stripeRadius,
            "specularCenterX" to specularCenterX,
            "specularCenterY" to specularCenterY,
            "specularRadius" to specularRadius
        )
    }

}
