package com.github.adamyork.kparticles.platform.assets

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
interface LoadingProgressListener {

    fun onTaskCompleted(taskId: String)

    fun onTaskFailed(taskId: String, cause: Throwable? = null)

}
