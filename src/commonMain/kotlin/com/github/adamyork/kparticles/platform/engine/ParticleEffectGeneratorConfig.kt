package com.github.adamyork.kparticles.platform.engine

import com.github.adamyork.kparticles.platform.engine.effect.AnimatedItemReturnGenerator
import com.github.adamyork.kparticles.platform.engine.effect.BlackHoleGenerator
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.engine.effect.BubbleGenerator
import com.github.adamyork.kparticles.platform.engine.effect.CollidingBitsGenerator
import com.github.adamyork.kparticles.platform.engine.effect.CollisionGenerator
import com.github.adamyork.kparticles.platform.engine.effect.DripsGenerator
import com.github.adamyork.kparticles.platform.engine.effect.DustGenerator
import com.github.adamyork.kparticles.platform.engine.effect.FireworkBurstGenerator
import com.github.adamyork.kparticles.platform.engine.effect.FireworkTailGenerator
import com.github.adamyork.kparticles.platform.engine.effect.GobblerGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ItemReturnGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ProjectileGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ShatterGenerator
import com.github.adamyork.kparticles.platform.engine.effect.SpinnersGenerator
import com.github.adamyork.kparticles.platform.engine.effect.StressTestGenerator
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticleEffectGeneratorConfig {

    @Provides
    @IntoSet
    fun provideDust(impl: DustGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideCollision(impl: CollisionGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideProjectile(impl: ProjectileGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideBlobProjectile(impl: BlobProjectileGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideFireworkBurst(impl: FireworkBurstGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideFireworkTail(impl: FireworkTailGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideItemReturn(impl: ItemReturnGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideAnimatedItemReturn(impl: AnimatedItemReturnGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideBubble(impl: BubbleGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideCollidingBits(impl: CollidingBitsGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideGobbler(impl: GobblerGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideBlackHole(impl: BlackHoleGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideStressTest(impl: StressTestGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideShatter(impl: ShatterGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideDrips(impl: DripsGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideSpinners(impl: SpinnersGenerator): ParticleEffectGenerator = impl

}
