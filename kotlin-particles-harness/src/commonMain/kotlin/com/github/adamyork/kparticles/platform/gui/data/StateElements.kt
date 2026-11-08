package com.github.adamyork.kparticles.platform.gui.data

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
data class StateElements(
    var viewPort: ParticleViewPort,
    var mapItemCollectibleAsset: ImageAsset,
) {
    companion object {
        val emptyStateElements: StateElements = StateElements(
            viewPort = HarnessViewPort(0, 0, 1, 1),
            mapItemCollectibleAsset = ImageAsset.getTmpImageAsset()
        )
    }
}
