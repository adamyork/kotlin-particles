package com.github.adamyork.sparrow.platform.gui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface TestBedColorScheme {

    @Composable
    fun getScheme(): ColorScheme

    @Composable
    fun getTypography(): Typography

    @Composable
    fun getHoverColor(): Color

    @Composable
    fun getFocusOutlineColor(): Color

    @Composable
    fun getDisabledOpacity(): Float

}
