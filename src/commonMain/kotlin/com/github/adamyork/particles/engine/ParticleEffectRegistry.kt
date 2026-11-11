package com.github.adamyork.particles.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.data.ParticleDirection
import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleEffectRequest
import com.github.adamyork.particles.engine.data.ParticleRenderStyle

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class ParticleEffectRegistry(initialEffects: List<ParticleEffect> = emptyList()) {

    public companion object {
        public const val MAXIMUM_REGISTERED_EFFECTS: Int = 64
    }

    public var totalSpawnedParticles: Long = 0L
        private set

    public val effects: List<ParticleEffect>
        field = ArrayList<ParticleEffect>()

    public val renderStyles: List<ParticleRenderStyle>
        field = ArrayList(ParticleRenderStyle.BUILT_IN)

    public val count: Int
        get() = effects.size

    public val isSealed: Boolean
        get() = sealed

    private val indexByEffect = HashMap<ParticleEffect, Int>()

    private val registeredGenerators = ArrayList<ParticleEffectGenerator>()

    private val generatorsByEffect = HashMap<ParticleEffect, ParticleEffectGenerator>()

    private var customRenderStyleCount = 0

    private var sealed = false

    init {
        for (effect in initialEffects) {
            register(effect)
        }
    }

    public fun register(effect: ParticleEffect): Int {
        val existingIndex = indexByEffect[effect]
        if (existingIndex != null) return existingIndex
        requireNotSealed("register particle effect ${effect.id}")
        if (effects.any { registeredEffect -> registeredEffect.id == effect.id }) {
            throw EngineException("A particle effect with id ${effect.id} is already registered")
        }
        if (effects.size >= MAXIMUM_REGISTERED_EFFECTS) {
            throw EngineException("Cannot register more than $MAXIMUM_REGISTERED_EFFECTS particle effects")
        }
        registerRenderStyle(effect.renderStyle)
        val newIndex = effects.size
        effects.add(effect)
        indexByEffect[effect] = newIndex
        return newIndex
    }

    public fun register(generator: ParticleEffectGenerator): Int {
        requireNotSealed("register a generator for ${generator.effect.id}")
        if (generatorsByEffect.containsKey(generator.effect)) {
            throw EngineException("A generator for particle effect ${generator.effect.id} is already registered")
        }
        val effectIndex = register(generator.effect)
        registeredGenerators.add(generator)
        generatorsByEffect[generator.effect] = generator
        return effectIndex
    }

    public fun generators(): List<ParticleEffectGenerator> = registeredGenerators

    public fun notifyAssetsReady() {
        for (generator in registeredGenerators) {
            generator.onAssetsReady()
        }
    }

    public fun spawn(
        effect: ParticleEffect,
        x: Double,
        y: Double,
        viewPort: ParticleViewPort,
        destinationX: Double? = null,
        destinationY: Double? = null,
        direction: ParticleDirection
    ) {
        val rightEdge = viewPort.x + viewPort.width.toDouble()
        val request = ParticleEffectRequest(x, y, viewPort, destinationX ?: rightEdge, destinationY ?: 0.0, direction)
        val generator = generatorsByEffect[effect]
            ?: throw EngineException("No particle effect generator registered for $effect")
        val particleCountBeforeSpawn = generator.particles.size
        generator.spawn(request)
        totalSpawnedParticles += (generator.particles.size - particleCountBeforeSpawn).toLong()
        for (companionEffect in generator.companionEffects) {
            val companionGenerator = generatorsByEffect[companionEffect] ?: continue
            val companionCountBeforeSpawn = companionGenerator.particles.size
            companionGenerator.spawn(request)
            totalSpawnedParticles += (companionGenerator.particles.size - companionCountBeforeSpawn).toLong()
        }
    }

    public fun advanceGenerators(deltaTime: Double) {
        for (generator in registeredGenerators) {
            val particleCountBeforeAdvance = generator.particles.size
            generator.advance(deltaTime)
            totalSpawnedParticles += (generator.particles.size - particleCountBeforeAdvance).toLong()
        }
    }

    public fun clear() {
        for (generator in registeredGenerators) {
            generator.reset()
            generator.particles.clear()
        }
    }

    public fun seal() {
        sealed = true
    }

    public fun createCustomRenderStyle(id: String): ParticleRenderStyle {
        requireNotSealed("create render style $id")
        if (renderStyles.any { renderStyle -> renderStyle.id == id }) {
            throw EngineException("A render style with id $id already exists")
        }
        var code = ParticleRenderStyle.FIRST_CUSTOM_CODE + customRenderStyleCount
        while (renderStyles.any { renderStyle -> renderStyle.code == code }) {
            customRenderStyleCount++
            code = ParticleRenderStyle.FIRST_CUSTOM_CODE + customRenderStyleCount
        }
        customRenderStyleCount++
        val renderStyle = ParticleRenderStyle(id, code)
        renderStyles.add(renderStyle)
        return renderStyle
    }

    public fun indexOf(effect: ParticleEffect): Int =
        indexByEffect[effect] ?: throw EngineException("Particle effect ${effect.id} is not registered")

    private fun registerRenderStyle(renderStyle: ParticleRenderStyle) {
        if (renderStyles.any { registeredStyle -> registeredStyle === renderStyle }) return
        if (renderStyles.any { registeredStyle -> registeredStyle.id == renderStyle.id }) {
            throw EngineException("A render style with id ${renderStyle.id} already exists")
        }
        if (renderStyles.any { registeredStyle -> registeredStyle.code == renderStyle.code }) {
            throw EngineException("A render style with code ${renderStyle.code} already exists")
        }
        renderStyles.add(renderStyle)
    }

    private fun requireNotSealed(action: String) {
        if (sealed) {
            throw EngineException("Cannot $action: the registry is sealed once rendering has started, register everything first")
        }
    }

}
