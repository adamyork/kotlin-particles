package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.GpuDataPacketWriter
import com.github.adamyork.kparticles.platform.engine.ParticleDrawFunction
import com.github.adamyork.kparticles.platform.engine.Renderers
import com.github.adamyork.kparticles.platform.engine.data.RenderType
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skia.Canvas

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class WasmGpuRenderers(
    gpuDataPacketWriter: GpuDataPacketWriter
) : Renderers<Canvas> {

    val gpuParticleRenderer = WasmJsGpuParticleRenderer(gpuDataPacketWriter)

    private val gpuDrawFunction: ParticleDrawFunction<Canvas> = { drawFunctionData ->
        gpuParticleRenderer.draw(viewPort = drawFunctionData.viewPort)
    }

    override fun getForType(renderType: RenderType): ParticleDrawFunction<Canvas> = gpuDrawFunction

}
