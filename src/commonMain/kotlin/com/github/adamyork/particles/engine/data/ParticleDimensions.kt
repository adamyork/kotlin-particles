package com.github.adamyork.particles.engine.data

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public sealed interface ParticleDimensions {

    public fun boundingWidth(): Double

    public fun boundingHeight(): Double

    public fun collisionRadius(): Double

    public fun boundingRadius(): Double

    public fun area(): Double

    public fun advance(deltaTime: Double)

    public fun fitArea(area: Double)

    public fun scaleBy(factor: Double)

    public fun scaledBy(factor: Double): ParticleDimensions
}
