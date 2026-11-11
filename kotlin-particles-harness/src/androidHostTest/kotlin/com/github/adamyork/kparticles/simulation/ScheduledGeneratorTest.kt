package com.github.adamyork.kparticles.simulation

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.gui.data.HarnessViewPort
import com.github.adamyork.particles.engine.EngineException
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.ScheduledGenerator
import com.github.adamyork.particles.engine.data.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class ScheduledGeneratorTest {

    private val testEffect = ParticleEffect("SCHEDULED_TEST", "Scheduled Test")

    private val viewPort = HarnessViewPort(0, 0, 1024, 768)

    private val request = ParticleEffectRequest(10.0, 20.0, viewPort, 0.0, 0.0, ParticleDirection.RIGHT)

    private inner class TestScheduledGenerator(
        intervalTicks: Double,
        particlesPerInterval: Int,
        maximumEmittedParticles: Int = Int.MAX_VALUE,
        private val deferredOffsetTicks: Double? = null
    ) : ScheduledGenerator<Unit>(testEffect, intervalTicks, particlesPerInterval, maximumEmittedParticles) {

        override fun createStreamState(request: ParticleEffectRequest) = Unit

        override fun createParticle(
            request: ParticleEffectRequest,
            streamState: Unit,
            particleId: Long,
            emissionIndex: Int,
            emissionOffset: Double
        ): Particle {
            if (deferredOffsetTicks != null) {
                deferEmission(emissionOffset + deferredOffsetTicks) { listOf(buildParticle(request, nextParticleId())) }
            }
            return buildParticle(request, particleId)
        }

        private fun buildParticle(request: ParticleEffectRequest, particleId: Long): Particle = Particle(
            id = particleId,
            lifecycle = ParticleLifecycle(lifetime = 100.0),
            appearance = ParticleAppearance(color = Color.White),
            dimensions = CircleDimensions(radius = 1.0),
            transform = ParticleTransform(position = ParticleVector(request.x, request.y)),
            material = ParticleMaterial(density = 1.0)
        )
    }

    @Test
    fun emitsTheConfiguredCountAtEachIntervalWithExactDelayOffsets() {
        val generator = TestScheduledGenerator(10.0, 3)
        generator.spawn(request)
        assertTrue(generator.particles.isEmpty())
        generator.advance(25.0)
        assertEquals(9, generator.particles.size)
        val delays = generator.particles.map { particle -> particle.lifecycle.delay }
        assertEquals(listOf(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 20.0, 20.0, 20.0), delays)
        generator.advance(5.0)
        assertEquals(9, generator.particles.size)
        generator.advance(1.0)
        assertEquals(12, generator.particles.size)
        assertEquals(0.0, generator.particles.last().lifecycle.delay)
    }

    @Test
    fun splittingTimeIntoSmallStepsEmitsTheSameTotalAsOneLargeStep() {
        val largeStepGenerator = TestScheduledGenerator(7.0, 2)
        val smallStepGenerator = TestScheduledGenerator(7.0, 2)
        largeStepGenerator.spawn(request)
        smallStepGenerator.spawn(request)
        largeStepGenerator.advance(100.0)
        repeat(200) { smallStepGenerator.advance(0.5) }
        assertEquals(30, largeStepGenerator.particles.size)
        assertEquals(largeStepGenerator.particles.size, smallStepGenerator.particles.size)
    }

    @Test
    fun stopHaltsEmissionAndSpawningAgainRestarts() {
        val generator = TestScheduledGenerator(5.0, 1)
        generator.spawn(request)
        generator.advance(12.0)
        val countBeforeStop = generator.particles.size
        generator.stop()
        assertFalse(generator.isEmitting)
        generator.advance(50.0)
        assertEquals(countBeforeStop, generator.particles.size)
        generator.spawn(request)
        assertTrue(generator.isEmitting)
        generator.advance(1.0)
        assertEquals(countBeforeStop + 1, generator.particles.size)
    }

    @Test
    fun registryCountsScheduledEmissionsAndClearStopsThem() {
        val registry = ParticleEffectRegistry()
        val generator = TestScheduledGenerator(10.0, 2)
        registry.register(generator)
        registry.spawn(testEffect, 10.0, 20.0, viewPort, direction = ParticleDirection.RIGHT)
        assertEquals(0L, registry.totalSpawnedParticles)
        registry.advanceGenerators(25.0)
        assertEquals(6L, registry.totalSpawnedParticles)
        registry.clear()
        registry.advanceGenerators(100.0)
        assertTrue(generator.particles.isEmpty())
        assertFalse(generator.isEmitting)
    }

    @Test
    fun maximumEmittedParticlesEndsTheRunMidInterval() {
        val generator = TestScheduledGenerator(1.0, 6, maximumEmittedParticles = 10)
        generator.spawn(request)
        generator.advance(50.0)
        assertEquals(10, generator.particles.size)
        assertFalse(generator.isEmitting)
        generator.advance(50.0)
        assertEquals(10, generator.particles.size)
    }

    @Test
    fun deferredEmissionIsCreatedInTheFrameItIsDueWithTheRightDelayOffset() {
        val generator = TestScheduledGenerator(10.0, 1, maximumEmittedParticles = 1, deferredOffsetTicks = 50.0)
        generator.spawn(request)
        generator.advance(40.0)
        assertEquals(1, generator.particles.size)
        generator.advance(20.0)
        assertEquals(2, generator.particles.size)
        assertEquals(10.0, generator.particles.last().lifecycle.delay)
        generator.advance(100.0)
        assertEquals(2, generator.particles.size)
    }

    @Test
    fun eachSpawnRunsItsOwnIndependentStream() {
        val generator = TestScheduledGenerator(10.0, 1, maximumEmittedParticles = 3)
        generator.spawn(request)
        generator.advance(5.0)
        assertEquals(1, generator.particles.size)
        generator.spawn(request)
        generator.advance(5.0)
        assertEquals(2, generator.particles.size)
        generator.advance(20.0)
        assertEquals(6, generator.particles.size)
        generator.advance(100.0)
        assertEquals(6, generator.particles.size)
        assertFalse(generator.isEmitting)
    }

    @Test
    fun invalidIntervalOrCountIsRejected() {
        assertFailsWith<EngineException> { TestScheduledGenerator(0.0, 1) }
        assertFailsWith<EngineException> { TestScheduledGenerator(5.0, 0) }
        assertFailsWith<EngineException> { TestScheduledGenerator(5.0, 1, maximumEmittedParticles = 0) }
    }
}
