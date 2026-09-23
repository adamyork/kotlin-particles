package com.github.adamyork.kparticles.platform.service.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
enum class LoadingTaskStatus {
	PENDING,
	COMPLETED,
	FAILED
}

data class LoadingTask(
	val id: String,
	val label: String,
	val status: LoadingTaskStatus = LoadingTaskStatus.PENDING
) {
	val isCompleted: Boolean
		get() = status == LoadingTaskStatus.COMPLETED
}
