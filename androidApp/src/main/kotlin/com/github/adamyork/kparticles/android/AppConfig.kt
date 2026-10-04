package com.github.adamyork.kparticles.android

import com.github.adamyork.kparticles.android.common.CommonConfig
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.android.engine.EngineConfig
import com.github.adamyork.kparticles.android.gui.GuiConfig
import com.github.adamyork.kparticles.android.service.ServiceConfig
import me.tatarka.inject.annotations.Component

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Component
abstract class AppConfig : CommonConfig, ServiceConfig, EngineConfig, GuiConfig
