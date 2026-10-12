package com.github.adamyork.kparticles.platform.engine.effect

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.RGBA
import com.github.adamyork.kparticles.platform.engine.data.*
import kotlin.math.max
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
abstract class AbstractGenerator(
    override val effect: ParticleEffect
) : ParticleEffectGenerator {

    private companion object {
        private const val GPU_PARAMETER_OFFSET: Int = 14
    }

    override fun writeGpuParticle(particle: Particle, targetBuffer: FloatArray, baseIndex: Int) {
        val size = max(particle.width, particle.height).toFloat()
        val usesCenterAnchor = particle.shape == ParticleShape.CIRCLE
        val spawnOffset = gpuSpawnOffset(particle, size)
        val spawnX = particle.x.toFloat() + spawnOffset
        val spawnY = particle.y.toFloat() + spawnOffset
        var writeIndex = baseIndex
        targetBuffer[writeIndex++] = spawnX
        targetBuffer[writeIndex++] = spawnY
        targetBuffer[writeIndex++] = gpuVelocityX(particle)
        targetBuffer[writeIndex++] = gpuVelocityY(particle)
        targetBuffer[writeIndex++] = particle.age.toFloat().coerceAtLeast(0f)
        targetBuffer[writeIndex++] = particle.lifetime.toFloat().coerceAtLeast(1f)
        targetBuffer[writeIndex++] = size
        targetBuffer[writeIndex++] = 1f
        targetBuffer[writeIndex++] = particle.color.red.coerceIn(0f, 1f)
        targetBuffer[writeIndex++] = particle.color.green.coerceIn(0f, 1f)
        targetBuffer[writeIndex++] = particle.color.blue.coerceIn(0f, 1f)
        targetBuffer[writeIndex++] = particle.color.alpha.coerceIn(0f, 1f)
        targetBuffer[writeIndex++] = gpuKind(particle)
        targetBuffer[writeIndex] = if (usesCenterAnchor) 1f else 0f
        val parameterIndex = baseIndex + GPU_PARAMETER_OFFSET
        targetBuffer.fill(0f, parameterIndex, baseIndex + ParticleEffectGenerator.GPU_FLOATS_PER_PARTICLE)
        writeGpuParameters(particle, targetBuffer, parameterIndex)
    }

    protected open fun gpuKind(particle: Particle): Float = 0f

    protected open fun gpuSpawnOffset(particle: Particle, size: Float): Float =
        if (particle.shape == ParticleShape.CIRCLE) size * 0.5f else 0f

    protected open fun gpuVelocityX(particle: Particle): Float = particle.xVelocity.toFloat()

    protected open fun gpuVelocityY(particle: Particle): Float = particle.yVelocity.toFloat()

    protected open fun writeGpuParameters(particle: Particle, targetBuffer: FloatArray, parameterIndex: Int) {}

    protected fun createColor(configuredColor: RGBA): Color {
        return Color(
            configuredColor.r.toFloat() / 255f,
            configuredColor.g.toFloat() / 255f,
            configuredColor.b.toFloat() / 255f,
            configuredColor.a.toFloat() / 255f
        )
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
