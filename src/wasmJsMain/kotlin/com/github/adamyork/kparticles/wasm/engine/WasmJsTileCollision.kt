package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.engine.CommonTileCollision
import com.github.adamyork.kparticles.platform.engine.Physics
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class WasmJsTileCollision(
    physics: Physics,
) : CommonTileCollision(physics)