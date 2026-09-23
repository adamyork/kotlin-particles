package com.github.adamyork.kparticles.wasm.gui

import com.github.adamyork.kparticles.platform.AppScope
import kotlinx.browser.document
import kotlinx.browser.window
import me.tatarka.inject.annotations.Inject
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement

/**
 * Owns and aligns the WebGPU particle overlay canvas with the Compose viewport.
 */
@AppScope
@Inject
class WasmJsUiParticleLayer {

    private companion object {
        const val PARTICLE_OVERLAY_ID = "sparrow-gpu-particles"
    }

    fun initializeOverlayCanvas(expectedWidth: Int, expectedHeight: Int): HTMLCanvasElement? {
        val composeTarget = document.getElementById("ComposeTarget") as? HTMLElement ?: return null
        val overlayCanvas = document.getElementById(PARTICLE_OVERLAY_ID) as? HTMLCanvasElement ?: return null
        val composeRect = composeTarget.getBoundingClientRect()
        if (composeRect.width <= 0.0 || composeRect.height <= 0.0) {
            return null
        }
        val clampedExpectedWidth = expectedWidth.coerceAtLeast(1)
        val clampedExpectedHeight = expectedHeight.coerceAtLeast(1)
        val left =
            (composeRect.left + ((composeRect.width - clampedExpectedWidth.toDouble()) * 0.5)).toInt().coerceAtLeast(0)
        val top =
            (composeRect.top + ((composeRect.height - clampedExpectedHeight.toDouble()) * 0.5)).toInt().coerceAtLeast(0)
        overlayCanvas.style.left = "${left}px"
        overlayCanvas.style.top = "${top}px"
        overlayCanvas.style.width = "${clampedExpectedWidth}px"
        overlayCanvas.style.height = "${clampedExpectedHeight}px"
        val renderScale = window.devicePixelRatio.toFloat().coerceAtLeast(1f)
        val scaledWidth = (clampedExpectedWidth * renderScale).toInt().coerceAtLeast(1)
        val scaledHeight = (clampedExpectedHeight * renderScale).toInt().coerceAtLeast(1)
        if (overlayCanvas.width != scaledWidth || overlayCanvas.height != scaledHeight) {
            overlayCanvas.width = scaledWidth
            overlayCanvas.height = scaledHeight
        }
        return overlayCanvas
    }

    fun getOverlayCanvas(): HTMLCanvasElement? {
        return document.getElementById(PARTICLE_OVERLAY_ID) as? HTMLCanvasElement
    }
}
