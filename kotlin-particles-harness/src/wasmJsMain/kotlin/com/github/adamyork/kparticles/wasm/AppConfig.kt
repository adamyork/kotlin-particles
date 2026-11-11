package com.github.adamyork.kparticles.wasm


import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.ParticleEffectGeneratorConfig
import com.github.adamyork.kparticles.platform.harness.ParticleEngineConfig
import com.github.adamyork.kparticles.wasm.common.CommonConfig
import com.github.adamyork.kparticles.wasm.engine.EngineConfig
import com.github.adamyork.kparticles.wasm.gui.GuiConfig
import com.github.adamyork.kparticles.wasm.service.ServiceConfig
import me.tatarka.inject.annotations.Component
import com.github.adamyork.kparticles.wasm.effects.WasmParticleRendererConfig

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Component
abstract class AppConfig : GuiConfig, ServiceConfig, EngineConfig, CommonConfig, ParticleEffectGeneratorConfig, ParticleEngineConfig, WasmParticleRendererConfig
