package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.common.PlatformInterop

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class FakePlatformInterop : PlatformInterop {

    override fun onReady(action: () -> Unit) {
        action()
    }

    override fun getWindowHeight(): Double = 768.0

    override fun getWindowWidth(): Double = 1024.0

    override fun hidePlatformLoader() {
    }

    override fun getPlatformNowTime(): Double = 0.0

    override fun getBlobFromBytes(bytes: ByteArray): Any = bytes

    override fun isTouchDevice(): Boolean = false

    override fun isGpuEngineSupported(platformData: Any?): Boolean = false

    override fun requestKeyboardFocus() {
    }

    override fun <T> addEventListener(type: String, callback: (T) -> Unit) {
    }

    override fun <T> removeEventListener(type: String, callback: (T) -> Unit) {
    }

    override fun requestAnimationFrame(callback: (Double) -> Unit): Int = 0

    override fun cancelAnimationFrame(handle: Int) {
    }

}
