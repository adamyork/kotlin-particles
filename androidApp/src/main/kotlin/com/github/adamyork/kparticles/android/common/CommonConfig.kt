package com.github.adamyork.kparticles.android.common

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import me.tatarka.inject.annotations.Provides

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface CommonConfig {

    val androidInterop: AndroidInterop
    val platformInterop: PlatformInterop

    @AppScope
    @Provides
    fun providePlatformInterop(impl: AndroidInterop): PlatformInterop = impl

}
