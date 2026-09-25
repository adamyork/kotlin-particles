package com.github.adamyork.kparticles.wasm

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.github.adamyork.kparticles.platform.LogConfig
import com.github.adamyork.kparticles.platform.gui.UiScaffold
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    LogConfig.initialize(minimumLevel = Level.DEBUG)
    logger.info { "Wasm main invoked" }
    val component = AppConfig::class.create()
    component.platformInterop.onReady {
        component.screenDimensionsService.initialize(
            component.platformInterop.getWindowWidth().toInt(),
            component.platformInterop.getWindowHeight().toInt()
        )
        (document.getElementById("ComposeTarget") as? HTMLElement)?.apply {
            setAttribute("tabindex", "0")
            style.apply {
                width = "${component.platformInterop.getWindowWidth().toInt()}px"
                height = "${component.platformInterop.getWindowHeight().toInt()}px"
                minWidth = "${component.platformInterop.getWindowWidth().toInt()}px"
                minHeight = "${component.platformInterop.getWindowHeight().toInt()}px"
                maxWidth = "${component.platformInterop.getWindowWidth().toInt()}px"
                maxHeight = "${component.platformInterop.getWindowHeight().toInt()}px"
            }
        }
        component.platformInterop.hidePlatformLoader()
        val testBed = component.testBed
        val testbedColorScheme = component.testBedColorScheme
        ComposeViewport(viewportContainerId = "ComposeTarget") {
            UiScaffold().BuildGui(testBed, testbedColorScheme)
        }
    }
}