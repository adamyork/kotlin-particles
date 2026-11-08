package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleRenderStyle
import com.github.adamyork.kparticles.platform.effects.style.EffectStyles
import com.github.adamyork.kparticles.platform.Projectile

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
object ParticleEffects {

    val DUST = ParticleEffect("DUST", "Dust", EffectStyles.DUST)
    val PROJECTILE = ParticleEffect("PROJECTILE", "Projectile")
    val BLOB_PROJECTILE =
        ParticleEffect("BLOB_PROJECTILE", "Blob Projectile", EffectStyles.BLOB)
    val ITEM_RETURN = ParticleEffect("ITEM_RETURN", "Item Return", ParticleRenderStyle.IMAGE)
    val ANIMATED_ITEM_RETURN = ParticleEffect(
        "ANIMATED_ITEM_RETURN",
        "Animated Item Return",
        ParticleRenderStyle.IMAGE
    )
    val BUBBLE = ParticleEffect("BUBBLE", "Bubbles", EffectStyles.BUBBLE)
    val COLLISION = ParticleEffect("COLLISION", "Collision")
    val FIREWORK = ParticleEffect("FIREWORK", "Fireworks")
    val COLLIDING_BITS = ParticleEffect("COLLIDING_BITS", "Colliding Bits")
    val GOBBLER = ParticleEffect("GOBBLER", "Gobbler")
    val BLACK_HOLE = ParticleEffect("BLACK_HOLE", "Black Hole")
    val STRESS_TEST = ParticleEffect("STRESS_TEST", "Stress Test")
    val SHATTER = ParticleEffect("SHATTER", "Shatter")
    val DRIPS = ParticleEffect("DRIPS", "Drips", EffectStyles.DRIP)
    val SPINNERS = ParticleEffect("SPINNERS", "Spinners")
    val SPRITE_EXPLODE = ParticleEffect("SPRITE_EXPLODE", "Sprite Explode")
    val SPRITE_EXPLODE_BACKDROP = ParticleEffect(
        "SPRITE_EXPLODE_BACKDROP",
        "Sprite Explode Backdrop",
        ParticleRenderStyle.IMAGE
    )

    val ALL: List<ParticleEffect> = listOf(
        DUST,
        PROJECTILE,
        BLOB_PROJECTILE,
        ITEM_RETURN,
        ANIMATED_ITEM_RETURN,
        BUBBLE,
        COLLISION,
        FIREWORK,
        COLLIDING_BITS,
        GOBBLER,
        BLACK_HOLE,
        STRESS_TEST,
        SHATTER,
        DRIPS,
        SPINNERS,
        SPRITE_EXPLODE,
        SPRITE_EXPLODE_BACKDROP
    )

}
