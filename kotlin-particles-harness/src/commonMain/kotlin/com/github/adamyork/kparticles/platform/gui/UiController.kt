package com.github.adamyork.kparticles.platform.gui

import com.github.adamyork.particles.common.ParticleEngineLifeCycleState
import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.kparticles.platform.gui.data.HarnessViewPort
import com.github.adamyork.kparticles.platform.harness.Engine
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.data.ParticleDirection
import com.github.adamyork.kparticles.platform.harness.DrawResult
import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.kparticles.platform.gui.data.ScreenDimensions
import com.github.adamyork.kparticles.platform.gui.data.StateElements
import com.github.adamyork.kparticles.platform.gui.data.UiState
import com.github.adamyork.kparticles.platform.assets.AssetService
import com.github.adamyork.kparticles.platform.assets.CollectibleImageStore
import com.github.adamyork.kparticles.platform.assets.LoadingProgressListener
import com.github.adamyork.kparticles.platform.assets.LoadingViewModel
import com.github.adamyork.particles.service.PhysicsSettingsService
import com.github.adamyork.particles.service.RuntimeService
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import com.github.adamyork.kparticles.platform.assets.data.LoadingTask
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class UiController(
    private val assetService: AssetService,
    private val engine: Engine,
    private val particleEffectRegistry: ParticleEffectRegistry,
    private val runtimeService: RuntimeService,
    private val screenDimensionsService: ScreenDimensionsService,
    private val collectibleImageStore: CollectibleImageStore
) : LoadingProgressListener {

    private val logger = KotlinLogging.logger {}
    private val viewModel = LoadingViewModel()
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val stateElements: StateElements = StateElements.emptyStateElements
    val loadingTasks: List<LoadingTask>
        get() = viewModel.loadingTasks

    suspend fun initializeGame() {
        try {
            val screenDimensions = screenDimensionsService.getScreenDimensions()
            assetService.initialize(this)
            val loaders: Map<String, suspend () -> Any> = mapOf(
                "collectible item" to { assetService.loadItem(0) },
                "particles" to { assetService.loadParticleShader() },
            )
            val loadedAssets = coroutineScope {
                loaders.map { (key, loader) ->
                    async(Dispatchers.Default) {
                        try {
                            val value = loader()
                            val mappedTaskId = LoadingViewModel.mapKeyToTaskId(key)
                            if (mappedTaskId.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    viewModel.onTaskCompleted(mappedTaskId)
                                }
                            }
                            key to value
                        } catch (failure: Throwable) {
                            ensureActive()
                            val mappedTaskId = LoadingViewModel.mapKeyToTaskId(key)
                            if (mappedTaskId.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    viewModel.onTaskFailed(mappedTaskId, failure)
                                }
                            }
                            throw failure
                        }
                    }
                }.awaitAll().toMap()
            }
            runtimeService.deriveGravityFromViewportHeight(screenDimensions.height)
            val viewPort = createInitialViewPort(screenDimensions)
            val collectibleAsset = loadedAssets.getValue("collectible item") as ImageAsset
            withContext(Dispatchers.Default) {
                engine.initialize(collectibleAsset)
                collectibleImageStore.image = collectibleAsset
                particleEffectRegistry.notifyAssetsReady()
            }
            withContext(Dispatchers.Main) {
                stateElements.viewPort = viewPort
                stateElements.mapItemCollectibleAsset = collectibleAsset
                runtimeService.particleEngineLifeCycleState = ParticleEngineLifeCycleState.INITIALIZED
            }
        } catch (failure: Throwable) {
            currentCoroutineContext().ensureActive()
            logger.error(failure) {
                "initializeGame failed: ${failure::class.simpleName}: ${failure.message ?: "no message"}"
            }
        }
    }

    override fun onTaskCompleted(taskId: String) {
        val mappedTaskId = LoadingViewModel.mapKeyToTaskId(taskId)
        if (mappedTaskId.isBlank()) return
        uiScope.launch {
            viewModel.onTaskCompleted(mappedTaskId)
        }
    }

    override fun onTaskFailed(taskId: String, cause: Throwable?) {
        val mappedTaskId = LoadingViewModel.mapKeyToTaskId(taskId)
        if (mappedTaskId.isBlank()) return
        uiScope.launch {
            viewModel.onTaskFailed(mappedTaskId, cause)
        }
    }

    fun allTasksCompleted(): Boolean {
        val completed = loadingTasks.all { it.isCompleted }
        if (completed && runtimeService.particleEngineLifeCycleState == ParticleEngineLifeCycleState.INITIALIZED) {
            runtimeService.particleEngineLifeCycleState = ParticleEngineLifeCycleState.RUNNING
        }
        return completed
    }

    fun tick(timestamp: Double): UiState {
        val elements = stateElements
        val fpsLabel = { "FPS: ${runtimeService.getFps().toInt()}" }
        if (runtimeService.particleEngineLifeCycleState != ParticleEngineLifeCycleState.RUNNING || runtimeService.particleEngineLifeCycleState == ParticleEngineLifeCycleState.INITIALIZING) {
            return UiState(
                drawResult = DrawResult.EMPTY_DRAW_RESULT,
                fpsLabel = fpsLabel(),
            )
        }
        engine.manageMap(elements.viewPort)
        val drawResult = engine.draw(elements.viewPort, timestamp)
        val uiState = UiState(
            drawResult = drawResult,
            fpsLabel = fpsLabel(),
        )
        return uiState
    }


    val physicsSettingsService: PhysicsSettingsService
        get() = runtimeService.physicsSettingsService

    fun applyGravity(metersPerSecondSquared: Double) {
        physicsSettingsService.gravityMetersPerSecondSquared = metersPerSecondSquared
        runtimeService.deriveGravityFromViewportHeight(stateElements.viewPort.height)
    }

    fun setSelectedParticleEffect(effect: ParticleEffect) {
        engine.selectedParticleEffect = effect
    }

    fun createParticles(type: ParticleEffect) {
        val viewPort = stateElements.viewPort
        val centerX = viewPort.x + (viewPort.width / 2.0)
        val centerY = viewPort.y + (viewPort.height / 2.0)
        val destinationX = viewPort.x + (viewPort.width * 0.75)
        val destinationY = viewPort.y + (viewPort.height * 0.25)
        val direction = if (centerX < destinationX) ParticleDirection.RIGHT else ParticleDirection.LEFT
        particleEffectRegistry.spawn(
            effect = type,
            x = centerX,
            y = centerY,
            viewPort = viewPort,
            destinationX = destinationX,
            destinationY = destinationY,
            direction = direction
        )
        if (runtimeService.particleEngineLifeCycleState != ParticleEngineLifeCycleState.RUNNING) {
            runtimeService.particleEngineLifeCycleState = ParticleEngineLifeCycleState.RUNNING
        }
    }

    private fun createInitialViewPort(screenDimensions: ScreenDimensions): ParticleViewPort {
        return HarnessViewPort(
            assetService.appProperties.viewport.x,
            assetService.appProperties.viewport.y,
            screenDimensions.width,
            screenDimensions.height
        )
    }
}
