@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.adamyork.kparticles.wasm.common

import org.khronos.webgl.Int8Array
import org.w3c.files.Blob

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
external interface VisualViewport {
    val height: Double
    val width: Double
}

fun createBlobFromInt8Array(@Suppress("UNUSED_PARAMETER") int8Array: Int8Array): Blob =
    js("new Blob([int8Array])")

fun getVisualViewport(): VisualViewport = js(
    """(window.visualViewport || {
        height: window.innerHeight,
        width: window.innerWidth
    })"""
)

fun writeClipboardText(text: String): Unit = js("{ if (navigator.clipboard) navigator.clipboard.writeText(text); }")
