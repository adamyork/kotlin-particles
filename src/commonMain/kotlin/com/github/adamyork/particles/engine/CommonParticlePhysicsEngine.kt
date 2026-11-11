package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
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
    private val particleEngineDiagnostics: ParticleEngineDiagnostics
) : ParticlePhysicsEngine {

    override fun applyPhysics(viewPort: ParticleViewPort): ParticleSubframeSchedule {
        val subframeSchedule = runtimeService.getSubframeSchedule()
        particleEngineDiagnostics.beginEngineStep()
        particleEffectRegistry.advanceGenerators(subframeSchedule.subframeCount * subframeSchedule.subframeDeltaTime)
        val generators = particleEffectRegistry.generators()
        repeat(subframeSchedule.subframeCount) {
            particlePhysics.applyParticlePhysics(
                generators, viewPort, subframeSchedule.subframeDeltaTime
            )
            collision.applyParticleCollision(generators, viewPort, subframeSchedule.subframeDeltaTime)
        }
        particleEngineDiagnostics.endEngineStep()
        return subframeSchedule
    }

}
