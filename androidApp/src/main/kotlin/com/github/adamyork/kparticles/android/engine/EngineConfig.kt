package com.github.adamyork.kparticles.android.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.Collision
import com.github.adamyork.kparticles.platform.engine.CommonParticleFactory
import com.github.adamyork.kparticles.platform.engine.CommonParticlePhysics
import com.github.adamyork.kparticles.platform.engine.CommonSpatialGrid
import com.github.adamyork.kparticles.platform.engine.Engine
import com.github.adamyork.kparticles.platform.engine.ParticleFactory
import com.github.adamyork.kparticles.platform.engine.ParticlePhysics
import com.github.adamyork.kparticles.platform.engine.SpatialGrid
import me.tatarka.inject.annotations.Provides

interface EngineConfig {

    @AppScope
    @Provides
    fun provideEngine(
        platformInterop: PlatformInterop,
        androidEngine: AndroidEngine,
        androidGpuEngine: AndroidGpuEngine
    ): Engine = if (platformInterop.isGpuEngineSupported()) androidGpuEngine else androidEngine

    @AppScope
    @Provides
    fun provideParticlePhysics(impl: CommonParticlePhysics): ParticlePhysics = impl

    @AppScope
    @Provides
    fun provideCollision(impl: AndroidTileCollision): Collision = impl

    @AppScope
    @Provides
    fun provideParticleFactory(impl: CommonParticleFactory): ParticleFactory = impl

    @AppScope
    @Provides
    fun provideSpatialGrid(impl: CommonSpatialGrid): SpatialGrid = impl


}
