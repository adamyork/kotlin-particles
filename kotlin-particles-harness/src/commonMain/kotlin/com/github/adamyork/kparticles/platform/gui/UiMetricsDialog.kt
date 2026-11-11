package com.github.adamyork.kparticles.platform.gui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.particles.engine.ParticleEngineDiagnostics

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class UiMetricsDialog {

    @Composable
    fun Build(
        particleEngineDiagnostics: ParticleEngineDiagnostics,
        platformInterop: PlatformInterop,
        onDismiss: () -> Unit
    ) {
        var reportText by remember { mutableStateOf(buildReportText(particleEngineDiagnostics, platformInterop)) }
        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            particleEngineDiagnostics.sampleLiveParticles()
            reportText = buildReportText(particleEngineDiagnostics, platformInterop)
        }
        val colorScheme = MaterialTheme.colorScheme
        val dialogBackground = colorScheme.primary
        val dialogTextColor = colorScheme.onPrimary
        val copyButtonColors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.onPrimary,
            contentColor = colorScheme.primary
        )
        val outlinedButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = dialogTextColor)
        val outlinedButtonBorder = BorderStroke(1.dp, dialogTextColor)
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = dialogBackground,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 640.dp)
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
                            val reportGroups = reportText.trimEnd().split("\n\n")
                            val leftGroupCount = (reportGroups.size + 1) / 2
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "metrics-text" }
                                    .testTag("metrics-text")
                            ) {
                                Text(
                                    text = buildStyledReport(reportGroups.take(leftGroupCount).joinToString("\n\n")),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = dialogTextColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = buildStyledReport(reportGroups.drop(leftGroupCount).joinToString("\n\n")),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = dialogTextColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
                            onClick = {
                                coroutineScope.launch {
                                    particleEngineDiagnostics.sampleLiveParticles()
                                    reportText = buildReportText(particleEngineDiagnostics, platformInterop)
                                }
                            },
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
                                particleEngineDiagnostics.reset()
                                coroutineScope.launch {
                                    particleEngineDiagnostics.sampleLiveParticles()
                                    reportText = buildReportText(particleEngineDiagnostics, platformInterop)
                                }
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

    private fun buildStyledReport(reportText: String) = buildAnnotatedString {
        val lines = reportText.split("\n")
        lines.forEachIndexed { lineIndex, line ->
            val separatorIndex = line.indexOf(": ")
            if (separatorIndex < 0) {
                append(line)
            } else {
                append(line.substring(0, separatorIndex + 2))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSynthesis = FontSynthesis.All)) {
                    append(line.substring(separatorIndex + 2))
                }
            }
            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }

    private fun buildReportText(particleEngineDiagnostics: ParticleEngineDiagnostics, platformInterop: PlatformInterop): String =
        "Platform: ${platformInterop.getPlatformName()}\n" + particleEngineDiagnostics.buildReport()
}
