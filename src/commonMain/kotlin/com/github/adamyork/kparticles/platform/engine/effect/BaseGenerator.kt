package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.engine.data.*
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class BaseGenerator(
    override val effect: ParticleEffect
) : ParticleEffectGenerator {

    companion object {
        const val DEFAULT_MAX_WIDTH: Double = 10.0
        const val DEFAULT_MAX_HEIGHT: Double = 10.0
        const val DEFAULT_MAX_RADIUS: Double = 5.0
        const val DEFAULT_MAX_VELOCITY: Double = 10.0
        const val GUIDED_LAUNCH_INITIAL_SPEED: Double = 2.8
        const val GUIDED_LAUNCH_MAX_SPEED: Double = 5.25
        const val GUIDED_LAUNCH_THRUST: Double = 0.16
        const val GUIDED_LAUNCH_VELOCITY_HEADROOM: Double = 0.2
        const val MAX_ACTIVE_PROJECTILES: Int = 1
        const val MAX_ACTIVE_MAP_ITEM_RETURN_PARTICLES: Int = 1
        const val MAX_ACTIVE_COLLIDING_BITS_PARTICLES: Int = 512
        const val MAX_ACTIVE_GOBBLER_PARTICLES: Int = 64
        const val MAX_ACTIVE_BLACK_HOLE_CORE_PARTICLES: Int = 8
        const val MAX_ACTIVE_BLACK_HOLE_EXPLOSION_PARTICLES: Int = 1024
        const val MAX_ACTIVE_FIREWORK_BURST_PARTICLES: Int = 512
        const val MAX_ACTIVE_FIREWORK_TAIL_PARTICLES: Int = 1024
        const val MAX_ACTIVE_STRESS_TEST_PARTICLES: Int = 2048
        const val MAX_ACTIVE_BUBBLE_PARTICLES: Int = 32
    }

    protected fun createDistinctColorPair(): Pair<Color, Color> {
        val startColor = randomColor()
        var endColor = randomColor()
        while (endColor == startColor) {
            endColor = randomColor()
        }
        return startColor to endColor
    }

    protected fun randomColor(): Color {
        return Color(
            red = Random.nextFloat(),
            green = Random.nextFloat(),
            blue = Random.nextFloat(),
            alpha = 1.0f
        )
    }

    protected fun <T> shuffle(items: MutableList<T>) {
        for (currentIndex in items.lastIndex downTo 1) {
            val randomIndex = Random.nextInt(currentIndex + 1)
            val swappedItem = items[currentIndex]
            items[currentIndex] = items[randomIndex]
            items[randomIndex] = swappedItem
        }
    }
}
