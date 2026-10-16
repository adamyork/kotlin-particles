package com.github.adamyork.kparticles.platform.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
sealed interface ParticleDimensions {

    fun boundingWidth(): Double

    fun boundingHeight(): Double

    fun collisionRadius(): Double

    fun boundingRadius(): Double

    fun area(): Double

    fun advance(deltaTime: Double)

    fun fitArea(area: Double)

    fun scaleBy(factor: Double)

    fun scaledBy(factor: Double): ParticleDimensions
}
