package com.github.adamyork.kparticles.platform.gui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.adamyork.particles.service.ParticlePhysicsSettingsService
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class UiPhysicsDialog {

    private class PhysicsSliderSpecification(
        val label: String,
        val minimum: Float,
        val maximum: Float,
        val decimalPlaces: Int,
        val testTagName: String,
        val readValue: () -> Double,
        val writeValue: (Double) -> Unit
    )

    @Composable
    fun Build(
        physicsSettingsService: ParticlePhysicsSettingsService,
        onGravityChanged: (Double) -> Unit,
        onDismiss: () -> Unit
    ) {
        val sliderSpecifications = remember {
            listOf(
                PhysicsSliderSpecification(
                    "gravity (m/s²)", 0f, 30f, 2, "physics-gravity",
                    { physicsSettingsService.gravityMetersPerSecondSquared },
                    { onGravityChanged(it) }
                ),
                PhysicsSliderSpecification(
                    "drag", 0f, 0.1f, 3, "physics-drag",
                    { physicsSettingsService.drag },
                    { physicsSettingsService.drag = it }
                ),
                PhysicsSliderSpecification(
                    "drag coefficient", 0f, 10f, 2, "physics-drag-coefficient",
                    { physicsSettingsService.dragCoefficient },
                    { physicsSettingsService.dragCoefficient = it }
                ),
                PhysicsSliderSpecification(
                    "friction", 0f, 1f, 2, "physics-friction",
                    { physicsSettingsService.friction },
                    { physicsSettingsService.friction = it }
                ),
                PhysicsSliderSpecification(
                    "wall restitution", 0f, 1f, 2, "physics-wall-restitution",
                    { physicsSettingsService.wallRestitution },
                    { physicsSettingsService.wallRestitution = it }
                ),
                PhysicsSliderSpecification(
                    "medium density", 0f, 0.01f, 5, "physics-medium-density",
                    { physicsSettingsService.mediumDensity },
                    { physicsSettingsService.mediumDensity = it }
                ),
                PhysicsSliderSpecification(
                    "wind (px/tick)", -3f, 3f, 2, "physics-wind",
                    { physicsSettingsService.windVelocity },
                    { physicsSettingsService.windVelocity = it }
                ),
                PhysicsSliderSpecification(
                    "flow amplitude", 0f, 2f, 3, "physics-flow-amplitude",
                    { physicsSettingsService.flowAmplitude },
                    { physicsSettingsService.flowAmplitude = it }
                ),
                PhysicsSliderSpecification(
                    "flow frequency", 0f, 0.02f, 4, "physics-flow-frequency",
                    { physicsSettingsService.flowFrequency },
                    { physicsSettingsService.flowFrequency = it }
                ),
                PhysicsSliderSpecification(
                    "flow wavelength", 50f, 1000f, 0, "physics-flow-wavelength",
                    { physicsSettingsService.flowWavelength },
                    { physicsSettingsService.flowWavelength = it }
                )
            )
        }
        val colorScheme = MaterialTheme.colorScheme
        val dialogTextColor = colorScheme.onPrimary
        val outlinedButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = dialogTextColor)
        val outlinedButtonBorder = BorderStroke(1.dp, dialogTextColor)
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.primary,
                modifier = Modifier
                    .semantics { contentDescription = "physics-dialog" }
                    .testTag("physics-dialog")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Physics",
                        style = MaterialTheme.typography.titleMedium,
                        color = dialogTextColor
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sliderSpecifications.forEach { specification ->
                            SliderRow(specification, dialogTextColor)
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = outlinedButtonColors,
                        border = outlinedButtonBorder,
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .testTag("physics-close")
                    ) {
                        Text("close")
                    }
                }
            }
        }
    }

    @Composable
    private fun SliderRow(
        specification: PhysicsSliderSpecification,
        textColor: Color
    ) {
        var currentValue by remember { mutableStateOf(specification.readValue()) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = specification.label, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatValue(currentValue, specification.decimalPlaces),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = currentValue.toFloat().coerceIn(specification.minimum, specification.maximum),
                onValueChange = {
                    currentValue = it.toDouble()
                    specification.writeValue(it.toDouble())
                },
                valueRange = specification.minimum..specification.maximum,
                colors = SliderDefaults.colors(
                    thumbColor = textColor,
                    activeTrackColor = textColor,
                    inactiveTrackColor = textColor.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .focusProperties { canFocus = false }
                    .testTag(specification.testTagName)
            )
        }
    }

    private fun formatValue(value: Double, decimalPlaces: Int): String {
        val scale = 10.0.pow(decimalPlaces)
        val scaledValue = (value * scale).roundToLong()
        val sign = if (scaledValue < 0) "-" else ""
        val magnitude = abs(scaledValue)
        if (decimalPlaces == 0) return "$sign$magnitude"
        val scaleLong = scale.roundToLong()
        val fraction = (magnitude % scaleLong).toString().padStart(decimalPlaces, '0')
        return "$sign${magnitude / scaleLong}.$fraction"
    }
}
