package com.github.adamyork.particles.engine

import com.github.adamyork.particles.engine.data.Particle
import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleEffectRequest
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public abstract class ScheduledGenerator<StreamState : Any>(
    effect: ParticleEffect,
    public val intervalTicks: Double,
    public val particlesPerInterval: Int,
    public val maximumEmittedParticles: Int = Int.MAX_VALUE
) : AbstractGenerator(effect) {

    private companion object {
        private const val MINIMUM_INTERVAL_TICKS: Double = 0.01
    }

    private class DeferredEmission(val dueTicks: Double, val createParticles: () -> List<Particle>)

    private inner class EmissionStream(val request: ParticleEffectRequest, val state: StreamState) {
        var ticksUntilNextEmission = 0.0
        var emittedParticleCount = 0
    }

    public val isEmitting: Boolean
        get() = activeStreams.isNotEmpty()

    private val activeStreams = ArrayList<EmissionStream>()

    private val deferredEmissions = ArrayList<DeferredEmission>()

    private var elapsedTicks = 0.0

    init {
        if (intervalTicks < MINIMUM_INTERVAL_TICKS) {
            throw EngineException("Scheduled generator for ${effect.id} needs an interval of at least $MINIMUM_INTERVAL_TICKS ticks")
        }
        if (particlesPerInterval < 1) {
            throw EngineException("Scheduled generator for ${effect.id} needs at least one particle per interval")
        }
        if (maximumEmittedParticles < 1) {
            throw EngineException("Scheduled generator for ${effect.id} needs a maximum of at least one emitted particle")
        }
    }

    final override fun create(request: ParticleEffectRequest): MutableList<Particle> {
        val streamState = createStreamState(request)
        return MutableList(particlesPerInterval) { emissionIndex ->
            createParticle(request, streamState, nextParticleId(), emissionIndex, 0.0)
        }
    }

    override fun spawn(request: ParticleEffectRequest) {
        activeStreams.add(EmissionStream(request, createStreamState(request)))
    }

    override fun advance(deltaTime: Double) {
        for (stream in activeStreams) {
            emitDueParticles(stream, deltaTime)
        }
        for (streamIndex in activeStreams.lastIndex downTo 0) {
            if (activeStreams[streamIndex].emittedParticleCount >= maximumEmittedParticles) {
                activeStreams.removeAt(streamIndex)
            }
        }
        releaseDeferredEmissions(deltaTime)
        elapsedTicks += deltaTime
    }

    override fun reset() {
        activeStreams.clear()
        deferredEmissions.clear()
        elapsedTicks = 0.0
    }

    public fun stop() {
        activeStreams.clear()
    }

    protected abstract fun createStreamState(request: ParticleEffectRequest): StreamState

    protected abstract fun createParticle(
        request: ParticleEffectRequest,
        streamState: StreamState,
        particleId: Long,
        emissionIndex: Int,
        emissionOffset: Double
    ): Particle

    protected fun deferEmission(offsetFromFrameStartTicks: Double, createParticles: () -> List<Particle>) {
        deferredEmissions.add(DeferredEmission(elapsedTicks + offsetFromFrameStartTicks, createParticles))
    }

    private fun emitDueParticles(stream: EmissionStream, deltaTime: Double) {
        var emissionOffset = stream.ticksUntilNextEmission
        while (emissionOffset < deltaTime && stream.emittedParticleCount < maximumEmittedParticles) {
            repeat(min(particlesPerInterval, maximumEmittedParticles - stream.emittedParticleCount)) {
                val particle = createParticle(stream.request, stream.state, nextParticleId(), stream.emittedParticleCount, emissionOffset)
                stream.emittedParticleCount++
                particles.add(withDelayOffset(particle, emissionOffset))
            }
            emissionOffset += intervalTicks
        }
        stream.ticksUntilNextEmission = emissionOffset - deltaTime
    }

    private fun releaseDeferredEmissions(deltaTime: Double) {
        val frameEndTicks = elapsedTicks + deltaTime
        var deferredEmissionIndex = 0
        while (deferredEmissionIndex < deferredEmissions.size) {
            val deferredEmission = deferredEmissions[deferredEmissionIndex]
            if (deferredEmission.dueTicks >= frameEndTicks) {
                deferredEmissionIndex++
                continue
            }
            val delayOffset = max(0.0, deferredEmission.dueTicks - elapsedTicks)
            for (particle in deferredEmission.createParticles()) {
                particles.add(withDelayOffset(particle, delayOffset))
            }
            deferredEmissions[deferredEmissionIndex] = deferredEmissions[deferredEmissions.lastIndex]
            deferredEmissions.removeAt(deferredEmissions.lastIndex)
        }
    }

    private fun withDelayOffset(particle: Particle, delayOffset: Double): Particle =
        particle.copy(lifecycle = particle.lifecycle.copy(delay = particle.lifecycle.delay + delayOffset))
}
