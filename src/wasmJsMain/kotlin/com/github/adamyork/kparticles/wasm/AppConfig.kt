package com.github.adamyork.kparticles.wasm


import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.wasm.common.CommonConfig
import com.github.adamyork.kparticles.wasm.engine.EngineConfig
import com.github.adamyork.kparticles.wasm.gui.GuiConfig
import com.github.adamyork.kparticles.wasm.service.ServiceConfig
import me.tatarka.inject.annotations.Component

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Component
abstract class AppConfig : GuiConfig, ServiceConfig, EngineConfig, CommonConfig
