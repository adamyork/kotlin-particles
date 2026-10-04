package com.github.adamyork.kparticles.android

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.lifecycleScope
import com.github.adamyork.kparticles.android.gui.AndroidPortraitGui
import com.github.adamyork.kparticles.platform.LogConfig
import com.github.adamyork.kparticles.platform.gui.TestBed
import com.github.adamyork.kparticles.platform.gui.UiScaffold
import com.github.adamyork.sparrow.platform.gui.TestBedColorScheme
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class MainActivity : ComponentActivity() {

    private lateinit var component: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogConfig.initialize(minimumLevel = Level.INFO)
        logger.info { "MainActivity onCreate invoked" }
        component = AppConfig::class.create()
        component.androidInterop.initialize(applicationContext)
        val testBed = component.testBed
        val testBedColorScheme = component.testBedColorScheme
        setContent {
            ScaffoldDelegate(component, testBed, testBedColorScheme)
        }
        lifecycleScope.launch {
            component.platformInterop.onReady {
                component.platformInterop.hidePlatformLoader()
            }
        }
    }

    @SuppressLint("ConfigurationScreenWidthHeight")
    @Composable
    private fun ScaffoldDelegate(
        component: AppConfig,
        testBed: TestBed,
        testBedColorScheme: TestBedColorScheme
    ) {
        val configuration = LocalConfiguration.current
        component.screenDimensionsService.initialize(
            configuration.screenWidthDp,
            configuration.screenHeightDp
        )
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (isPortrait) {
            AndroidPortraitGui().BuildGui()
        } else {
            UiScaffold().BuildGui(testBed, testBedColorScheme)
        }
    }

}
