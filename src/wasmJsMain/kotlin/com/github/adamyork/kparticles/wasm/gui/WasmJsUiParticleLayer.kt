package com.github.adamyork.kparticles.wasm.gui

import com.github.adamyork.kparticles.platform.AppScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.browser.document
import kotlinx.browser.window
import me.tatarka.inject.annotations.Inject
import org.w3c.dom.HTMLCanvasElement

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@AppScope
@Inject
class WasmJsUiParticleLayer {

    private companion object {
        const val PARTICLE_OVERLAY_ID = "kotlin-particles-gpu-particles"
    }

    private val logger = KotlinLogging.logger {}

    fun initializeOverlayCanvas(viewportWidth: Int, viewportHeight: Int): HTMLCanvasElement? {
        val overlayCanvas = document.getElementById(PARTICLE_OVERLAY_ID) as? HTMLCanvasElement ?: return null
        val clampedWidth = viewportWidth.coerceAtLeast(1)
        val clampedHeight = viewportHeight.coerceAtLeast(1)
        val left = ((window.innerWidth - clampedWidth) / 2).coerceAtLeast(0)
        overlayCanvas.style.left = "${left}px"
        overlayCanvas.style.top = "0px"
        overlayCanvas.style.width = "${clampedWidth}px"
        overlayCanvas.style.height = "${clampedHeight}px"
        val renderScale = window.devicePixelRatio.toFloat().coerceAtLeast(1f)
        val scaledWidth = (clampedWidth * renderScale).toInt().coerceAtLeast(1)
        val scaledHeight = (clampedHeight * renderScale).toInt().coerceAtLeast(1)
        if (overlayCanvas.width != scaledWidth || overlayCanvas.height != scaledHeight) {
            overlayCanvas.width = scaledWidth
            overlayCanvas.height = scaledHeight
        }
        logger.info {
            "overlay canvas initialized: windowInner=${window.innerWidth}x${window.innerHeight} " +
                "viewport=${clampedWidth}x${clampedHeight} left=$left top=0 " +
                "renderScale=$renderScale backingBuffer=${scaledWidth}x${scaledHeight}"
        }
        return overlayCanvas
    }

    fun getOverlayCanvas(): HTMLCanvasElement? {
        return document.getElementById(PARTICLE_OVERLAY_ID) as? HTMLCanvasElement
    }
}
