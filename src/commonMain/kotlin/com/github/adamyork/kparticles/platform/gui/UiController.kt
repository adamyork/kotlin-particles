package com.github.adamyork.kparticles.platform.gui

import com.github.adamyork.kparticles.platform.common.LifeCycleState
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.Engine
import com.github.adamyork.kparticles.platform.engine.Particles
import com.github.adamyork.kparticles.platform.engine.data.DrawResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.gui.data.ScreenDimensions
import com.github.adamyork.kparticles.platform.gui.data.StateElements
import com.github.adamyork.kparticles.platform.gui.data.UiState
import com.github.adamyork.kparticles.platform.service.AssetService
import com.github.adamyork.kparticles.platform.service.LoadingProgressListener
import com.github.adamyork.kparticles.platform.service.LoadingViewModel
import com.github.adamyork.kparticles.platform.service.RuntimeService
import com.github.adamyork.kparticles.platform.service.data.ImageAsset
import com.github.adamyork.kparticles.platform.service.data.LoadingTask
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class UiController(
    private val assetService: AssetService,
    private val engine: Engine,
    private val particles: Particles,
    private val runtimeService: RuntimeService,
    private val screenDimensionsService: ScreenDimensionsService
) : LoadingProgressListener {

    private val logger = KotlinLogging.logger {}
    private val viewModel = LoadingViewModel()
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hasSpawnedCompletionFireworkTailParticles: Boolean = false
    private val allParticles: ArrayList<Particle> = arrayListOf()

    val stateElements: StateElements = StateElements.emptyStateElements
    val loadingTasks: List<LoadingTask>
        get() = viewModel.loadingTasks

    suspend fun initializeGame() {
        runCatching {
            val screenDimensions = screenDimensionsService.getScreenDimensions()
            assetService.initialize(this)
            val loaders: Map<String, suspend () -> Any> = mapOf(
                "collectible item" to { assetService.loadItem(0) },
//                "particles" to { assetService.loadParticleShader() },
//                "particles_gl" to { assetService.loadParticleGlShaders() }
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
            val viewPort = createInitialViewPort(screenDimensions)
            val collectibleAsset = loadedAssets.getValue("collectible item") as ImageAsset
            withContext(Dispatchers.Default) {
                engine.initialize(collectibleAsset)
                particles.populateColorMap(assetService)
            }
            withContext(Dispatchers.Main) {
                stateElements.viewPort = viewPort
                stateElements.mapItemCollectibleAsset = collectibleAsset
                runtimeService.lifeCycleState = LifeCycleState.INITIALIZED
            }
        }.onFailure { failure ->
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
        if (completed && runtimeService.lifeCycleState == LifeCycleState.INITIALIZED) {
            runtimeService.lifeCycleState = LifeCycleState.RUNNING
        }
        return completed
    }

    fun tick(timestamp: Double): UiState {
        val elements = stateElements
        val fpsLabel = { "FPS: ${runtimeService.getFps().toInt()}" }
        if (runtimeService.lifeCycleState != LifeCycleState.RUNNING || runtimeService.lifeCycleState == LifeCycleState.INITIALIZING) {
            return UiState(
                drawResult = DrawResult.EMPTY_DRAW_RESULT,
                fpsLabel = fpsLabel(),
            )
        }
        engine.manageMap(allParticles, elements.viewPort)
        if (!hasSpawnedCompletionFireworkTailParticles) {
            //particles.createFireworkTailParticles(elements.viewPort, elements.gameMap.particles)
            hasSpawnedCompletionFireworkTailParticles = true
        }
        val drawResult = engine.draw(allParticles, elements.viewPort, timestamp)
        val uiState = UiState(
            drawResult = drawResult,
            fpsLabel = fpsLabel(),
        )
        return uiState
    }


    fun createParticles(mode: String) {
        val viewPort = stateElements.viewPort
        logger.info { "createParticles: mode=$mode, viewport: x=${viewPort.x} y=${viewPort.y} w=${viewPort.width} h=${viewPort.height}" }
        val centerX = viewPort.x + (viewPort.width / 2.0)
        val centerY = viewPort.y + (viewPort.height / 2.0)
        val destinationX = viewPort.x + (viewPort.width * 0.75)
        val destinationY = viewPort.y + (viewPort.height * 0.25)
        val direction = if (centerX < destinationX) "right" else "left"
        val createdParticles = particles.create(
            mode = mode,
            x = centerX,
            y = centerY,
            destinationX = destinationX,
            destinationY = destinationY,
            direction = direction
        )
        logger.info { "createParticles: created ${createdParticles.size} particles at ($centerX, $centerY)" }
        createdParticles.forEach { p ->
            logger.info { "  created particle: type=${p.type} x=${p.x} y=${p.y} w=${p.width} h=${p.height} age=${p.age} lifetime=${p.lifetime}" }
        }
        allParticles.addAll(createdParticles)
        if (runtimeService.lifeCycleState != LifeCycleState.RUNNING) {
            runtimeService.lifeCycleState = LifeCycleState.RUNNING
        }
    }

    private fun createInitialViewPort(screenDimensions: ScreenDimensions): ViewPort {
        return ViewPort(
            assetService.appProperties.viewport.x,
            assetService.appProperties.viewport.y,
            0,
            0,
            screenDimensions.width,
            screenDimensions.height
        )
    }
}
