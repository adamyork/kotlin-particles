package com.github.adamyork.particles.wasm.engine

import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.ParticleDrawFunction
import org.jetbrains.skia.Canvas

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public class WasmGpuRenderers(
    gpuDataPacketWriter: GpuDataPacketWriter
) {

    public val gpuParticleRenderer: WasmJsGpuParticleRenderer = WasmJsGpuParticleRenderer(gpuDataPacketWriter)

    public val drawFunction: ParticleDrawFunction<Canvas> = { drawFunctionData ->
        gpuParticleRenderer.draw(viewPort = drawFunctionData.viewPort)
    }


}
