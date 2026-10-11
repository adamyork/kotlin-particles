package com.github.adamyork.kparticles.platform.gui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class UiDropDown {

    @Composable
    fun Build(
        controller: UiController,
        platformInterop: PlatformInterop,
        particleModes: List<ParticleEffect>,
        selectedParticleMode: ParticleEffect,
        onParticleModeSelected: (ParticleEffect) -> Unit,
        controlsShape: RoundedCornerShape
    ) {
        var isParticleModeMenuExpanded by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current
        val colorScheme = MaterialTheme.colorScheme
        val createButtonColors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary
        )
        val dropdownButtonColors = ButtonDefaults.outlinedButtonColors(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface
        )
        UiParticleControls().Build(
            particleModes = particleModes,
            selectedParticleMode = selectedParticleMode,
            isParticleModeMenuExpanded = isParticleModeMenuExpanded,
            onParticleModeMenuExpandedChange = { isParticleModeMenuExpanded = it },
            onParticleModeSelected = {
                onParticleModeSelected(it)
                controller.setSelectedParticleEffect(it)
            },
            colorScheme = colorScheme,
            dropdownButtonColors = dropdownButtonColors,
            dropdownMenuTextColor = colorScheme.primary,
            createButtonColors = createButtonColors,
            controlsShape = controlsShape,
            onCreateClicked = {
                controller.createParticles(selectedParticleMode)
                focusManager.clearFocus(force = true)
                platformInterop.requestKeyboardFocus()
            }
        )
    }

}