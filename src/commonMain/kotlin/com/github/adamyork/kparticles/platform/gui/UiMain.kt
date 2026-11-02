package com.github.adamyork.kparticles.platform.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.adamyork.kparticles.platform.common.LifeCycleState
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import com.github.adamyork.kparticles.platform.engine.EngineDiagnostics
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect
import com.github.adamyork.kparticles.platform.gui.data.ScreenDimensions
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.LoadingTask
import com.github.adamyork.kparticles.platform.service.data.LoadingTaskStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class UiMain(
    val controller: UiController,
    val runtimeService: RuntimeService,
    val screenDimensionsService: ScreenDimensionsService,
    val platformInterop: PlatformInterop,
    val engineDiagnostics: EngineDiagnostics,
) {

    abstract var uiDrawLayer: UiDrawLayer
    protected open val centerHudWithinViewport: Boolean = false
    protected open val hudTopInset: Dp = 72.dp
    protected open val hudOverlayTopPadding: Dp = 8.dp
    protected open val controlsPlacement: ControlsPlacement = ControlsPlacement.INLINE

    private companion object {
        val PARTICLE_MODES = listOf(
            ParticleEffect.DUST,
            ParticleEffect.COLLISION,
            ParticleEffect.PROJECTILE,
            ParticleEffect.BLOB_PROJECTILE,
            ParticleEffect.FIREWORK_BURST,
            ParticleEffect.FIREWORK_TAIL,
            ParticleEffect.ITEM_RETURN,
            ParticleEffect.ANIMATED_ITEM_RETURN,
            ParticleEffect.BUBBLE,
            ParticleEffect.COLLIDING_BITS,
            ParticleEffect.GOBBLER,
            ParticleEffect.BLACK_HOLE,
            ParticleEffect.SHATTER,
            ParticleEffect.DRIPS,
            ParticleEffect.SPINNERS,
            ParticleEffect.SPRITE_EXPLODE,
            ParticleEffect.STRESS_TEST
        )
        val OVERLAY_BACKGROUND_COLOR = Color(0xB3000000)
        val OVERLAY_TEXT_COLOR = Color.White
    }

    @Composable
    fun Build() {
        val screenDimensions = remember { screenDimensionsService.getScreenDimensions() }
        val drawLayer = remember { uiDrawLayer }
        var fpsLabel by remember { mutableStateOf("FPS: --") }
        var isLoadingChecklistVisible by remember { mutableStateOf(true) }
        var selectedParticleMode by remember { mutableStateOf(PARTICLE_MODES.first()) }
        val allTasksCompleted = controller.allTasksCompleted()
        val gameLifeCycleState = runtimeService.lifeCycleState
        val focusManager = LocalFocusManager.current
        val areLabelsVisible = !isLoadingChecklistVisible && (
                gameLifeCycleState == LifeCycleState.INITIALIZED ||
                        gameLifeCycleState == LifeCycleState.RUNNING
                )
        val areControlsAvailable = gameLifeCycleState != LifeCycleState.INITIALIZING
        val controlsShape = RoundedCornerShape(8.dp)
        val particleControls: @Composable () -> Unit = {
            UiDropDown().Build(
                controller = controller,
                platformInterop = platformInterop,
                particleModes = PARTICLE_MODES,
                selectedParticleMode = selectedParticleMode,
                onParticleModeSelected = { selectedParticleMode = it },
                controlsShape = controlsShape,
                engineDiagnostics = engineDiagnostics
            )
        }
        val density = LocalDensity.current
        var viewportTopInRootPx by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(allTasksCompleted, isLoadingChecklistVisible) {
            if (allTasksCompleted && isLoadingChecklistVisible) {
                delay(1000.milliseconds)
                isLoadingChecklistVisible = false
            }
        }
        LaunchedEffect(Unit) {
            controller.initializeGame()
        }
        LaunchedEffect(runtimeService.lifeCycleState) {
            if (
                runtimeService.lifeCycleState == LifeCycleState.RUNNING
            ) {
                var frameId: Int
                fun loop(timestamp: Double) {
                    runtimeService.setCurrentFrameTime(timestamp)
                    val frame = controller.tick(timestamp)
                    frame.drawResult.foregroundImage?.let { image ->
                        drawLayer.drawForeground(image)
                    }
                    fpsLabel = frame.fpsLabel
                    frameId = platformInterop.requestAnimationFrame(::loop)
                }
                frameId = platformInterop.requestAnimationFrame(::loop)
                try {
                    awaitCancellation()
                } finally {
                    platformInterop.cancelAnimationFrame(frameId)
                }
            }
        }
        LaunchedEffect(gameLifeCycleState) {
            if (gameLifeCycleState == LifeCycleState.RUNNING) {
                focusManager.clearFocus(force = true)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Main content area" }
                .testTag("main")
                .background(color = MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Main content stack" }
                    .testTag("main-column")
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .onGloballyPositioned { coordinates ->
                            viewportTopInRootPx = coordinates.positionInRoot().y
                        }
                        .semantics { contentDescription = "canvas-with-fps-overlay" }
                        .testTag("canvas-with-fps-overlay")
                ) {
                    drawLayer.Build(
                        isRunning = runtimeService.lifeCycleState == LifeCycleState.RUNNING,
                        onFpsLabelChanged = { nextLabel ->
                            fpsLabel = nextLabel
                        }
                    )
                }
                if (areControlsAvailable && controlsPlacement == ControlsPlacement.INLINE) {
                    Box(modifier = Modifier.padding(top = 20.dp)) {
                        particleControls()
                    }
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = hudOverlayTopPadding)
                    .zIndex(5f)
            ) {
                val measuredViewportTopOffset = if (centerHudWithinViewport) {
                    with(density) { viewportTopInRootPx.toDp() }
                } else {
                    0.dp
                }
                val hudContainerModifier = if (centerHudWithinViewport) {
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(screenDimensions.width.dp)
                        .height(screenDimensions.height.dp)
                        .padding(top = measuredViewportTopOffset)
                } else {
                    Modifier.fillMaxSize()
                }
                Box(modifier = hudContainerModifier) {
                    if (areLabelsVisible) {
                        Text(
                            text = fpsLabel,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 10.dp, start = 10.dp)
                                .background(OVERLAY_BACKGROUND_COLOR, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .semantics { contentDescription = "fpsLabel" }
                                .testTag("fpsLabel"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFF00FFCC)
                        )
                        Text(
                            text = "Screen: ${screenDimensions.width}x${screenDimensions.height}",
                            style = MaterialTheme.typography.labelLarge,
                            color = OVERLAY_TEXT_COLOR,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 12.dp)
                                .background(OVERLAY_BACKGROUND_COLOR, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .semantics { contentDescription = "Screen dimensions label" }
                                .testTag("screen-dimensions-label")
                        )
                    }
                    if (areControlsAvailable && controlsPlacement == ControlsPlacement.OVERLAY) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .background(OVERLAY_BACKGROUND_COLOR, controlsShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            particleControls()
                        }
                    }
                }
            }
            if (isLoadingChecklistVisible) {
                LoadingChecklist(
                    loadingTasks = controller.loadingTasks,
                    viewportTopInRootPx = viewportTopInRootPx,
                    screenDimensions = screenDimensions
                )
            }
        }
    }

    @Composable
    private fun LoadingChecklist(
        loadingTasks: List<LoadingTask>,
        viewportTopInRootPx: Float,
        screenDimensions: ScreenDimensions
    ) {
        val density = LocalDensity.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(6f)
        ) {
            val viewportTopOffset = with(density) { viewportTopInRootPx.toDp() }
            val availableChecklistHeight = screenDimensions.height.dp.coerceAtLeast(1.dp)
            val estimatedChecklistHeight = (loadingTasks.size * 30).dp + 48.dp
            val checklistScale = (availableChecklistHeight / estimatedChecklistHeight).coerceIn(0.62f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = viewportTopOffset)
                    .width(screenDimensions.width.dp)
                    .height(screenDimensions.height.dp)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(260.dp)
                        .graphicsLayer(
                            scaleX = checklistScale,
                            scaleY = checklistScale,
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        )
                        .background(OVERLAY_BACKGROUND_COLOR, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    loadingTasks.forEach { task ->
                        LoadingTaskRow(task)
                    }
                }
            }
        }
    }

    @Composable
    private fun LoadingTaskRow(task: LoadingTask) {
        val statusIcon = when (task.status) {
            LoadingTaskStatus.COMPLETED -> Icons.Default.CheckCircle
            LoadingTaskStatus.FAILED -> Icons.Default.Cancel
            LoadingTaskStatus.PENDING -> Icons.Default.Circle
        }
        val statusColor = when (task.status) {
            LoadingTaskStatus.COMPLETED -> Color.Green
            LoadingTaskStatus.FAILED -> Color.Red
            LoadingTaskStatus.PENDING -> Color.Gray
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = task.label,
                style = MaterialTheme.typography.bodySmall,
                color = OVERLAY_TEXT_COLOR
            )
        }
    }
}
