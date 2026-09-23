package com.github.adamyork.kparticles.platform

import io.github.oshai.kotlinlogging.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
object LogConfig {

    private val timeZone = TimeZone.currentSystemDefault()
    private var isInitialized = false
    fun initialize(minimumLevel: Level = Level.INFO) {
        if (isInitialized) return
        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
        KotlinLoggingConfiguration.direct.apply {
            logLevel = minimumLevel
            appender = object : Appender {
                override fun log(loggingEvent: KLoggingEvent) {
                    try {
                        val now = Clock.System.now()
                        val timestamp = now.toLocalDateTime(timeZone)
                        val level = loggingEvent.level.name.lowercase()
                        val loggerName = loggingEvent.loggerName.substringAfterLast('.').lowercase()
                        val message = loggingEvent.message?.lowercase() ?: ""
                        val color = when (loggingEvent.level) {
                            Level.ERROR -> "\u001B[31m"
                            Level.WARN -> "\u001B[33m"
                            Level.INFO -> "\u001B[32m"
                            else -> "\u001B[36m"
                        }
                        println("$color[$timestamp] [sparrow-wasm] [kotlin] [$level] [$loggerName] -> $message")
                    } catch (e: Exception) {
                        println("\u001B[31mLogging error: ${e.message}\u001B[0m")
                    }
                }
            }
        }
        isInitialized = true
    }
}
