package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.DiagnosticCounter
import com.github.adamyork.particles.engine.data.DiagnosticPhase
import com.github.adamyork.particles.engine.data.ParticleSubframeSchedule
import com.github.adamyork.particles.service.RuntimeService

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class CommonParticlePhysicsEngine(
    private val particlePhysics: ParticlePhysics,
    private val collision: ParticleCollision,
    private val particleEffectRegistry: ParticleEffectRegistry,
    private val runtimeService: RuntimeService,
    private val engineDiagnostics: EngineDiagnostics
) : ParticlePhysicsEngine {

    override fun applyPhysics(viewPort: ParticleViewPort): ParticleSubframeSchedule {
        val subframeSchedule = runtimeService.getSubframeSchedule()
        engineDiagnostics.beginEngineWork()
        engineDiagnostics.count(DiagnosticCounter.SUBFRAMES, subframeSchedule.subframeCount)
        val generators = particleEffectRegistry.generators()
        repeat(subframeSchedule.subframeCount) {
            val physicsMark = engineDiagnostics.startTimer()
            particlePhysics.applyParticlePhysics(
                generators, viewPort, subframeSchedule.subframeDeltaTime
            )
            engineDiagnostics.stopTimer(DiagnosticPhase.PHYSICS, physicsMark)
            val collisionMark = engineDiagnostics.startTimer()
            collision.applyParticleCollision(generators, viewPort, subframeSchedule.subframeDeltaTime)
            engineDiagnostics.stopTimer(DiagnosticPhase.COLLISION_TOTAL, collisionMark)
        }
        return subframeSchedule
    }

}
