package com.github.adamyork.kparticles.platform.common.data

import androidx.compose.ui.unit.IntOffset

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class ViewPort(var x: Int, var y: Int, var lastX: Int, var lastY: Int, val width: Int, val height: Int) {

    fun globalToLocal(x: Int, y: Int): IntOffset {
        return IntOffset(x - this.x, y - this.y)
    }

}
