package com.github.adamyork.kparticles.android.effects

import com.github.adamyork.particles.engine.CpuStyleRenderer
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import android.graphics.Canvas

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface AndroidEffectsConfig {

    @Provides
    @IntoSet
    fun provideDust(impl: AndroidDustRenderer): CpuStyleRenderer<Canvas> = impl

    @Provides
    @IntoSet
    fun provideBubble(impl: AndroidBubbleRenderer): CpuStyleRenderer<Canvas> = impl

    @Provides
    @IntoSet
    fun provideBlob(impl: AndroidBlobRenderer): CpuStyleRenderer<Canvas> = impl

    @Provides
    @IntoSet
    fun provideDrip(impl: AndroidDripRenderer): CpuStyleRenderer<Canvas> = impl

}
