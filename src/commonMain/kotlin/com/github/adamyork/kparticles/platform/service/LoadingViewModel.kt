package com.github.adamyork.kparticles.platform.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.github.adamyork.kparticles.platform.service.data.LoadingTask
import com.github.adamyork.kparticles.platform.service.data.LoadingTaskStatus
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class LoadingViewModel : ViewModel(), LoadingProgressListener {

    private val logger = KotlinLogging.logger {}

    companion object {
        fun mapKeyToTaskId(key: String): String {
            val fileName = if (key.startsWith("http")) {
                key.substringAfterLast("/").substringBeforeLast(".")
            } else {
                key.lowercase()
            }
            return when (fileName) {
                "collectible item" -> "item_sprite_1"
                "app_yaml" -> "app_yaml"
                //"particles", "particles.wgsl" -> "particle_shader"
               // "particles_gl" -> "particle_shader_gl"
                else -> ""
            }
        }
    }

    var loadingTasks by mutableStateOf(
        listOf(
            LoadingTask("app_yaml", "Application YAML"),
            LoadingTask("item_sprite_1", "Item Sprite 1"),
            //LoadingTask("particle_shader", "Particle Shader"),
            //LoadingTask("particle_shader_gl", "Particle GL Shaders")
        )
    )

    override fun onTaskCompleted(taskId: String) {
        if (taskId.isBlank()) return
        loadingTasks.firstOrNull { it.id == taskId }?.let { task ->
            logger.info { "Checklist item completed: ${task.id} (${task.label})" }
        }
        loadingTasks = loadingTasks.map {
            if (it.id == taskId) it.copy(status = LoadingTaskStatus.COMPLETED) else it
        }
    }

    override fun onTaskFailed(taskId: String, cause: Throwable?) {
        if (taskId.isBlank()) return
        loadingTasks.firstOrNull { it.id == taskId }?.let { task ->
            logger.error(cause) {
                "Checklist item failed: ${task.id} (${task.label}): ${cause?.message ?: "no message"}"
            }
        }
        loadingTasks = loadingTasks.map {
            if (it.id == taskId) it.copy(status = LoadingTaskStatus.FAILED) else it
        }
    }

}
