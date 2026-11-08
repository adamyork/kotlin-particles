package com.github.adamyork.kparticles.wasm.common

import com.github.adamyork.kparticles.ParticleScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.browser.window
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skiko.wasm.onWasmReady
import org.khronos.webgl.toInt8Array
import org.w3c.dom.events.Event
import com.github.adamyork.particles.wasm.common.hasWebGpuSupport

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@ParticleScope
@Inject
class WasmJsInterop : PlatformInterop {

    private val logger = KotlinLogging.logger {}

    override fun onReady(action: () -> Unit) {
        onWasmReady {
            action()
        }
    }

    override fun getWindowHeight(): Double {
        return getVisualViewport().height
    }

    override fun getWindowWidth(): Double {
        return getVisualViewport().width
    }

    override fun getPlatformNowTime(): Double {
        return window.performance.now()
    }

    override fun getBlobFromBytes(bytes: ByteArray): Any {
        return createBlobFromInt8Array(bytes.toInt8Array())
    }

    override fun copyTextToClipboard(text: String) {
        writeClipboardText(text)
    }


    override fun isTouchDevice(): Boolean {
        return window.navigator.maxTouchPoints > 0
    }

    override fun isGpuEngineSupported(platformData: Any?): Boolean {
        return hasWebGpuSupport()
    }

    override fun <T> addEventListener(type: String, callback: (T) -> Unit) {
        val nativeCallback: (Event) -> Unit = { event ->
            @Suppress("UNCHECKED_CAST")
            callback(event as T)
        }
        window.addEventListener(type, nativeCallback)
    }

    override fun <T> removeEventListener(type: String, callback: (T) -> Unit) {
        val nativeCallback: (Event) -> Unit = { event ->
            @Suppress("UNCHECKED_CAST")
            callback(event as T)
        }
        window.removeEventListener(type, nativeCallback)
    }

    override fun requestAnimationFrame(callback: (Double) -> Unit): Int {
        return window.requestAnimationFrame { timestamp ->
            callback(timestamp)
        }
    }

    override fun cancelAnimationFrame(handle: Int) {
        window.cancelAnimationFrame(handle)
    }

}
