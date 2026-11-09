package com.github.adamyork.kparticles.platform.effects

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.assets.AssetEngineTiming
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.service.EngineTiming
import com.github.adamyork.particles.engine.ParticleEffectGenerator
import com.github.adamyork.kparticles.platform.effects.style.DripStyleSettings
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import com.github.adamyork.kparticles.platform.effects.style.EffectStyleShaders
import com.github.adamyork.particles.engine.shader.BuiltInStyleShaders
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.engine.data.ParticleImageSource

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface ParticleEffectGeneratorConfig {

    @Provides
    @ParticleScope
    fun provideParticleEffectRegistry(generators: Set<ParticleEffectGenerator>): ParticleEffectRegistry {
        val registry = ParticleEffectRegistry(ParticleEffects.ALL)
        for (generator in generators) {
            registry.register(generator)
        }
        return registry
    }

    @ParticleScope
    @Provides
    fun provideImageSource(): ParticleImageSource = ParticleImageSource()

    @ParticleScope
    @Provides
    fun provideEngineTiming(impl: AssetEngineTiming): EngineTiming = impl

    @ParticleScope
    @Provides
    fun provideGpuStyleShaders(dripStyleSettings: DripStyleSettings, imageSource: ParticleImageSource): List<GpuStyleShader> =
        listOf(BuiltInStyleShaders.SHAPE, BuiltInStyleShaders.image(imageSource)) + EffectStyleShaders.create(dripStyleSettings)

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
    fun provideFirework(impl: FireworkGenerator): ParticleEffectGenerator = impl

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

    @Provides
    @IntoSet
    fun provideSpriteExplode(impl: SpriteExplodeGenerator): ParticleEffectGenerator = impl

    @Provides
    @IntoSet
    fun provideSpriteExplodeBackdrop(impl: SpriteExplodeBackdropGenerator): ParticleEffectGenerator = impl

}
