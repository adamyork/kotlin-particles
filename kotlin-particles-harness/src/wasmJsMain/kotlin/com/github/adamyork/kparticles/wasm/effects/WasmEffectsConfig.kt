package com.github.adamyork.kparticles.wasm.effects

import com.github.adamyork.particles.engine.CpuStyleRenderer
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import org.jetbrains.skia.Canvas

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface WasmEffectsConfig {

    @Provides
    @IntoSet
    fun provideDust(impl: WasmDustRenderer): CpuStyleRenderer<Canvas> = impl

    @Provides
    @IntoSet
    fun provideBubble(impl: WasmBubbleRenderer): CpuStyleRenderer<Canvas> = impl

    @Provides
    @IntoSet
    fun provideBlob(impl: WasmBlobRenderer): CpuStyleRenderer<Canvas> = impl

    @Provides
    @IntoSet
    fun provideDrip(impl: WasmDripRenderer): CpuStyleRenderer<Canvas> = impl

}
