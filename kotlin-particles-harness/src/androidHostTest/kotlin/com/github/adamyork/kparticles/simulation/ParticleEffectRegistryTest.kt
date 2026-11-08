package com.github.adamyork.kparticles.simulation

import com.github.adamyork.particles.engine.EngineException
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import com.github.adamyork.kparticles.platform.effects.ParticleEffects

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class ParticleEffectRegistryTest {

    @Test
    fun builtInEffectsKeepTheirOriginalIndices() {
        val registry = ParticleEffectRegistry(ParticleEffects.ALL)
        assertEquals(17, registry.count)
        assertEquals(0, registry.indexOf(ParticleEffects.DUST))
        assertEquals(1, registry.indexOf(ParticleEffects.PROJECTILE))
        assertEquals(11, registry.indexOf(ParticleEffects.STRESS_TEST))
        assertEquals(16, registry.indexOf(ParticleEffects.SPRITE_EXPLODE_BACKDROP))
        for ((expectedIndex, effect) in ParticleEffects.ALL.withIndex()) {
            assertEquals(expectedIndex, registry.indexOf(effect))
        }
    }

    @Test
    fun renderStyleCodesAreUniqueAndStable() {
        assertEquals(0f, ParticleRenderStyle.SHAPE.code)
        assertEquals(1f, ParticleRenderStyle.IMAGE.code)
        assertEquals(2f, EffectStyles.DUST.code)
        assertEquals(3f, EffectStyles.BLOB.code)
        assertEquals(4f, EffectStyles.BUBBLE.code)
        assertEquals(5f, EffectStyles.DRIP.code)
    }

    @Test
    fun customEffectsGetNextIndexAndCustomRenderStylesStartAtSixteen() {
        val registry = ParticleEffectRegistry(ParticleEffects.ALL)
        val firstStyle = registry.createCustomRenderStyle("GLOW")
        val secondStyle = registry.createCustomRenderStyle("SPARKLE")
        assertEquals(16f, firstStyle.code)
        assertEquals(17f, secondStyle.code)
        val customEffect = ParticleEffect("GLOW_TRAIL", "Glow Trail", firstStyle)
        assertEquals(17, registry.register(customEffect))
        assertSame(customEffect, registry.effects.last())
        assertEquals(ParticleRenderStyle.BUILT_IN.size + 4 + 2, registry.renderStyles.size)
    }

    @Test
    fun registeringTheSameEffectTwiceReturnsItsExistingIndex() {
        val registry = ParticleEffectRegistry()
        val firstIndex = registry.register(ParticleEffects.BUBBLE)
        assertEquals(firstIndex, registry.register(ParticleEffects.BUBBLE))
        assertEquals(1, registry.count)
    }

    @Test
    fun invalidRegistrationsAreRejected() {
        val registry = ParticleEffectRegistry(ParticleEffects.ALL)
        assertFailsWith<EngineException> {
            registry.register(ParticleEffect("DUST", "Imposter"))
        }
        assertFailsWith<EngineException> {
            registry.indexOf(ParticleEffect("UNREGISTERED", "Unregistered"))
        }
        assertFailsWith<EngineException> { registry.createCustomRenderStyle("DRIP") }
        for (extraIndex in registry.count until ParticleEffectRegistry.MAXIMUM_REGISTERED_EFFECTS) {
            registry.register(ParticleEffect("EXTRA_$extraIndex", "Extra"))
        }
        assertFailsWith<EngineException> {
            registry.register(ParticleEffect("ONE_TOO_MANY", "One Too Many"))
        }
    }

    @Test
    fun sealingRejectsFurtherRegistrationButStillAllowsLookups() {
        val registry = ParticleEffectRegistry(ParticleEffects.ALL)
        assertEquals(false, registry.isSealed)
        registry.seal()
        assertEquals(true, registry.isSealed)
        val lateEffect = ParticleEffect("LATE", "Late")
        assertFailsWith<EngineException> { registry.register(lateEffect) }
        assertFailsWith<EngineException> { registry.createCustomRenderStyle("LATE_STYLE") }
        assertEquals(0, registry.register(ParticleEffects.DUST))
        assertEquals(0, registry.indexOf(ParticleEffects.DUST))
        assertEquals(17, registry.count)
    }

}
