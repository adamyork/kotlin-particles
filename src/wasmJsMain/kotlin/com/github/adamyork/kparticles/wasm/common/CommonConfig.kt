package com.github.adamyork.kparticles.wasm.common

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface CommonConfig {

    val platformInterop: PlatformInterop

    @AppScope
    @Provides
    fun providePlatformInterop(impl: WasmJsInterop): PlatformInterop = impl

}
