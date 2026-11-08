package com.github.adamyork.kparticles.platform.gui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.engine.EngineDiagnostics

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class UiMetricsDialog {

    @Composable
    fun Build(
        engineDiagnostics: EngineDiagnostics,
        platformInterop: PlatformInterop,
        onDismiss: () -> Unit
    ) {
        var reportText by remember { mutableStateOf(engineDiagnostics.buildReport()) }
        val colorScheme = MaterialTheme.colorScheme
        val dialogBackground = colorScheme.primary
        val dialogTextColor = colorScheme.onPrimary
        val copyButtonColors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.onPrimary,
            contentColor = colorScheme.primary
        )
        val outlinedButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = dialogTextColor)
        val outlinedButtonBorder = BorderStroke(1.dp, dialogTextColor)
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = dialogBackground,
                modifier = Modifier
                    .semantics { contentDescription = "metrics-dialog" }
                    .testTag("metrics-dialog")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Engine metrics",
                        style = MaterialTheme.typography.titleMedium,
                        color = dialogTextColor
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SelectionContainer {
                            Text(
                                text = reportText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = dialogTextColor,
                                modifier = Modifier
                                    .semantics { contentDescription = "metrics-text" }
                                    .testTag("metrics-text")
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { platformInterop.copyTextToClipboard(reportText) },
                            colors = copyButtonColors,
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .testTag("metrics-copy")
                        ) {
                            Text("copy")
                        }
                        OutlinedButton(
                            onClick = { reportText = engineDiagnostics.buildReport() },
                            colors = outlinedButtonColors,
                            border = outlinedButtonBorder,
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .testTag("metrics-refresh")
                        ) {
                            Text("refresh")
                        }
                        OutlinedButton(
                            onClick = {
                                engineDiagnostics.reset()
                                reportText = engineDiagnostics.buildReport()
                            },
                            colors = outlinedButtonColors,
                            border = outlinedButtonBorder,
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .testTag("metrics-reset")
                        ) {
                            Text("reset")
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = outlinedButtonColors,
                            border = outlinedButtonBorder,
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .testTag("metrics-close")
                        ) {
                            Text("close")
                        }
                    }
                }
            }
        }
    }
}
