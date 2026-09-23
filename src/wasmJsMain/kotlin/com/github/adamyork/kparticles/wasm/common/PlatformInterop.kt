package com.github.adamyork.kparticles.wasm.common

import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.browser.document
import kotlinx.browser.window
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skiko.wasm.onWasmReady
import org.khronos.webgl.toInt8Array
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob

@AppScope
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

    override fun hidePlatformLoader() {
        document.getElementById("loading-screen")?.let {
            (it as HTMLElement).style.display = "none"
        }
    }

    override fun getPlatformNowTime(): Double {
        return window.performance.now()
    }

    override fun getBlobFromBytes(bytes: ByteArray): Any {
        return createBlobFromInt8Array(bytes.toInt8Array())
    }

    override fun createAudioBlobUri(blob: Any): String {
        return URL.createObjectURL(blob as Blob)
    }

    override fun isTouchDevice(): Boolean {
        return window.navigator.maxTouchPoints > 0
    }

    override fun isGpuEngineSupported(platformData: Any?): Boolean {
        return false
        //return hasWebGpuSupport()
    }

    override fun requestKeyboardFocus() {
        val composeTarget = document.getElementById("ComposeTarget") as? HTMLElement
        if (composeTarget == null) {
            logger.warn { "requestKeyboardFocus: ComposeTarget element not found" }
            return
        }
        if (!composeTarget.hasAttribute("tabindex")) {
            composeTarget.setAttribute("tabindex", "0")
        }
        composeTarget.focus()
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
