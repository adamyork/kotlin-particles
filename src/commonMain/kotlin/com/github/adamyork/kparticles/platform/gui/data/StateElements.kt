package com.github.adamyork.kparticles.platform.gui.data

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.service.data.ImageAsset

data class StateElements(
    var viewPort: ViewPort,
    var mapItemCollectibleAsset: ImageAsset,
) {
    companion object {
        val emptyStateElements: StateElements = StateElements(
            viewPort = ViewPort(0, 0, 0, 0, 1, 1),
            mapItemCollectibleAsset = ImageAsset.getTmpImageAsset()
        )
    }
}
