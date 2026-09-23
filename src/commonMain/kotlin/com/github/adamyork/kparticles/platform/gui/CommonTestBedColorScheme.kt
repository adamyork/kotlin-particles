package com.github.adamyork.sparrow.platform.gui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.tatarka.inject.annotations.Inject

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@Inject
class CommonTestBedColorScheme : TestBedColorScheme {

    // --- Legacy CSS Constants ---
    private val buttonActive = Color(0xFFEDC189)
    private val buttonDisabledBg = Color(0xFFCCCCCC)
    private val buttonDisabledBorder = Color(0xFF999999)
    private val buttonDisabledText = Color(0xFF666666)
    private val overlayBackground = Color(0xFFCCCCCC)

    // --- Typography Tokens ---
    private val appFontFamily = FontFamily.SansSerif
    private val labelLargeSize = 14.sp
    private val bodySmallSize = 12.sp

    private val lightBgColor = Color(0xFFF8FAFC)        // --bg-color
    private val lightCardBg = Color(0xFFFFFFFF)         // --card-bg
    private val lightTextMain = Color.Black              // --text-com.github.adamyork.sparrow.wasm.main
    private val lightTextMuted = Color(0xFF64748B)      // --text-muted
    private val lightPrimary = Color(0xFF4F46E5)        // --primary
    private val lightPrimaryHover = Color(0xFF6366F1)   // --primary-hover
    private val lightBorderColor = Color(0xFFE2E8F0)    // --border-color

    private val darkBgColor = Color(0xFF0F172A)         // --bg-color
    private val darkCardBg = Color(0xFF1E293B)          // --card-bg
    private val darkTextMain = Color.Black               // --text-com.github.adamyork.sparrow.wasm.main
    private val darkTextMuted = Color(0xFF94A3B8)       // --text-muted
    private val darkPrimary = Color(0xFF818CF8)         // --primary
    private val darkPrimaryHover = Color(0xFFA5B4FC)    // --primary-hover
    private val darkBorderColor = Color(0xFF334155)     // --border-color

    private val primaryColor
        @Composable
        get() = if (isSystemInDarkTheme()) darkPrimary else lightPrimary
    private val primaryHoverColor
        @Composable
        get() = if (isSystemInDarkTheme()) darkPrimaryHover else lightPrimaryHover
    private val textMain
        @Composable
        get() = if (isSystemInDarkTheme()) darkTextMain else lightTextMain
    private val textMuted
        @Composable
        get() = if (isSystemInDarkTheme()) darkTextMuted else lightTextMuted
    private val backgroundColor
        @Composable
        get() = if (isSystemInDarkTheme()) darkBgColor else lightBgColor
    private val cardBackground
        @Composable
        get() = if (isSystemInDarkTheme()) darkCardBg else lightCardBg
    private val borderColor
        @Composable
        get() = if (isSystemInDarkTheme()) darkBorderColor else lightBorderColor
    private val focusOutlineColor
        @Composable
        get() = primaryColor.copy(alpha = 0.30f)

    private val disabledOpacity = 0.55f

    @Composable
    override fun getScheme(): ColorScheme {
        val baseScheme = if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = primaryColor,
                background = backgroundColor,
                surface = backgroundColor,
                surfaceContainer = cardBackground,
                onBackground = textMain,
                onSurface = textMain,
                onSurfaceVariant = textMuted,
                outline = borderColor
            )
        } else {
            lightColorScheme(
                primary = primaryColor,
                background = backgroundColor,
                surface = backgroundColor,
                surfaceContainer = cardBackground,
                onBackground = textMain,
                onSurface = textMain,
                onSurfaceVariant = textMuted,
                outline = borderColor
            )
        }
        return baseScheme.copy(
            primary = buttonActive,
            onPrimary = Color.Black,
            secondaryContainer = buttonDisabledBg,
            onSecondaryContainer = buttonDisabledText,
            outlineVariant = buttonDisabledBorder,
            inverseSurface = overlayBackground
        )
    }

    @Composable
    override fun getTypography(): Typography = Typography(
        labelLarge = TextStyle(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = labelLargeSize
        ),
        bodySmall = TextStyle(
            fontFamily = appFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = bodySmallSize
        )
    )


    @Composable
    override fun getHoverColor(): Color = primaryHoverColor

    @Composable
    override fun getFocusOutlineColor(): Color = focusOutlineColor

    @Composable
    override fun getDisabledOpacity(): Float = disabledOpacity
}
