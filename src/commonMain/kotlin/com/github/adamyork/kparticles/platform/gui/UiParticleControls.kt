package com.github.adamyork.kparticles.platform.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.adamyork.kparticles.platform.engine.data.ParticleType

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class UiParticleControls {

    @Composable
    fun Build(
        particleModes: List<ParticleType>,
        selectedParticleMode: ParticleType,
        isParticleModeMenuExpanded: Boolean,
        onParticleModeMenuExpandedChange: (Boolean) -> Unit,
        onParticleModeSelected: (ParticleType) -> Unit,
        colorScheme: ColorScheme,
        dropdownButtonColors: ButtonColors,
        dropdownMenuTextColor: Color,
        createButtonColors: ButtonColors,
        controlsShape: RoundedCornerShape,
        onCreateClicked: () -> Unit
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .semantics { contentDescription = "particle-controls" }
                .testTag("particle-controls")
        ) {
            Box {
                OutlinedButton(
                    onClick = { onParticleModeMenuExpandedChange(true) },
                    colors = dropdownButtonColors,
                    modifier = Modifier
                        .focusProperties { canFocus = false }
                        .semantics { contentDescription = "particleMode" }
                        .testTag("particleMode")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedParticleMode.displayName,
                            color = colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = colorScheme.primary
                        )
                    }
                }
                DropdownMenu(
                    expanded = isParticleModeMenuExpanded,
                    onDismissRequest = { onParticleModeMenuExpandedChange(false) },
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .border(width = 1.dp, color = Color.White, shape = controlsShape)
                        .background(colorScheme.surface, controlsShape)
                ) {
                    particleModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.displayName, color = dropdownMenuTextColor) },
                            onClick = {
                                onParticleModeSelected(mode)
                                onParticleModeMenuExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onCreateClicked,
                colors = createButtonColors,
                modifier = Modifier
                    .focusProperties { canFocus = false }
                    .semantics { contentDescription = "createBtn" }
                    .testTag("createBtn")
            ) {
                Text("create")
            }
        }
    }
}
