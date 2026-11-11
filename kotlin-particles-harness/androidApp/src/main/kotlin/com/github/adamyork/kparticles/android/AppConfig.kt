package com.github.adamyork.kparticles.android

import com.github.adamyork.kparticles.android.common.CommonConfig
import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.effects.ParticleEffectGeneratorConfig
import com.github.adamyork.kparticles.platform.harness.ParticleEngineConfig
import com.github.adamyork.kparticles.android.engine.EngineConfig
import com.github.adamyork.kparticles.android.gui.GuiConfig
import com.github.adamyork.kparticles.android.service.ServiceConfig
import me.tatarka.inject.annotations.Component
import com.github.adamyork.kparticles.android.effects.AndroidParticleRendererConfig

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Component
abstract class AppConfig : CommonConfig, ServiceConfig, EngineConfig, GuiConfig, ParticleEffectGeneratorConfig, ParticleEngineConfig, AndroidParticleRendererConfig
