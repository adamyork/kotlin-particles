package com.github.adamyork.kparticles.platform.common


interface PlatformInterop {

    fun onReady(action: () -> Unit)

    fun getWindowHeight(): Double

    fun getWindowWidth(): Double

    fun hidePlatformLoader()

    fun getPlatformNowTime(): Double

    fun getBlobFromBytes(bytes: ByteArray): Any

    fun createAudioBlobUri(blob: Any): String

    fun isTouchDevice(): Boolean

    fun isGpuEngineSupported(platformData: Any? = null): Boolean

    fun requestKeyboardFocus()

    fun <T> addEventListener(type: String, callback: (T) -> Unit)

    fun <T> removeEventListener(type: String, callback: (T) -> Unit)

    fun requestAnimationFrame(callback: (Double) -> Unit): Int

    fun cancelAnimationFrame(handle: Int)

}
