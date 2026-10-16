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

    override val particles: ArrayList<Particle> = ArrayList()

    private var nextParticleIdValue: Long = 0L

    override fun adopt(particle: Particle) {
        particles.add(particle.copy(id = nextParticleId()))
    }

    protected fun nextParticleId(): Long = nextParticleIdValue++

    private companion object {
        private const val GPU_PARAMETER_OFFSET: Int = 14
    }

    override fun writeGpuParticle(particle: Particle, targetBuffer: FloatArray, baseIndex: Int) {
        val size = max(particle.dimensions.boundingWidth(), particle.dimensions.boundingHeight()).toFloat()
        val usesCenterAnchor = particle.dimensions is CircleDimensions
        val spawnOffset = gpuSpawnOffset(particle, size)
        val spawnX = particle.transform.position.x.toFloat() + spawnOffset
        val spawnY = particle.transform.position.y.toFloat() + spawnOffset
        var writeIndex = baseIndex
        targetBuffer[writeIndex++] = spawnX
        targetBuffer[writeIndex++] = spawnY
        targetBuffer[writeIndex++] = gpuVelocityX(particle)
        targetBuffer[writeIndex++] = gpuVelocityY(particle)
        targetBuffer[writeIndex++] = particle.lifecycle.age.toFloat().coerceAtLeast(0f)
        targetBuffer[writeIndex++] = particle.lifecycle.lifetime.toFloat().coerceAtLeast(1f)
        targetBuffer[writeIndex++] = size
        targetBuffer[writeIndex++] = 1f
        targetBuffer[writeIndex++] = particle.appearance.color.red.coerceIn(0f, 1f)
        targetBuffer[writeIndex++] = particle.appearance.color.green.coerceIn(0f, 1f)
        targetBuffer[writeIndex++] = particle.appearance.color.blue.coerceIn(0f, 1f)
        targetBuffer[writeIndex++] = particle.appearance.color.alpha.coerceIn(0f, 1f)
        targetBuffer[writeIndex++] = gpuKind(particle)
        targetBuffer[writeIndex] = if (usesCenterAnchor) 1f else 0f
        val parameterIndex = baseIndex + GPU_PARAMETER_OFFSET
        targetBuffer.fill(0f, parameterIndex, baseIndex + ParticleEffectGenerator.GPU_FLOATS_PER_PARTICLE)
        writeGpuParameters(particle, targetBuffer, parameterIndex)
    }

    override fun writeGpuState(particle: Particle, targetBuffer: FloatArray, baseIndex: Int) {
        targetBuffer.fill(0f, baseIndex, baseIndex + ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE)
        val dimensions = particle.dimensions
        val isRectangle = dimensions is RectDimensions
        val sizeA = when (dimensions) {
            is RectDimensions -> dimensions.width
            is CircleDimensions -> dimensions.radius
        }
        val sizeB = when (dimensions) {
            is RectDimensions -> dimensions.height
            is CircleDimensions -> dimensions.radius
        }
        val scaleRate = when (dimensions) {
            is RectDimensions -> dimensions.scaleRate
            is CircleDimensions -> dimensions.scaleRate
        }
        val maximumSizeA = when (dimensions) {
            is RectDimensions -> dimensions.maxWidth
            is CircleDimensions -> dimensions.maxRadius
        }
        val maximumSizeB = when (dimensions) {
            is RectDimensions -> dimensions.maxHeight
            is CircleDimensions -> dimensions.maxRadius
        }
        val appearance = particle.appearance
        var writeIndex = baseIndex
        targetBuffer[writeIndex++] = particle.transform.position.x.toFloat()
        targetBuffer[writeIndex++] = particle.transform.position.y.toFloat()
        targetBuffer[writeIndex++] = particle.transform.position.z.toFloat()
        targetBuffer[writeIndex++] = particle.transform.angle.toFloat()
        targetBuffer[writeIndex++] = particle.dynamics.velocity.x.toFloat()
        targetBuffer[writeIndex++] = particle.dynamics.velocity.y.toFloat()
        targetBuffer[writeIndex++] = particle.dynamics.velocity.z.toFloat()
        targetBuffer[writeIndex++] = particle.dynamics.angularVelocity.toFloat()
        targetBuffer[writeIndex++] = particle.dynamics.thrust.x.toFloat()
        targetBuffer[writeIndex++] = particle.dynamics.thrust.y.toFloat()
        targetBuffer[writeIndex++] = particle.dynamics.thrust.z.toFloat()
        targetBuffer[writeIndex++] = particle.material.density.toFloat()
        targetBuffer[writeIndex++] = particle.lifecycle.age.toFloat()
        targetBuffer[writeIndex++] = particle.lifecycle.delay.toFloat()
        targetBuffer[writeIndex++] = particle.lifecycle.lifetime.toFloat()
        targetBuffer[writeIndex++] = 1f
        targetBuffer[writeIndex++] = if (isRectangle) 1f else 0f
        targetBuffer[writeIndex++] = sizeA.toFloat()
        targetBuffer[writeIndex++] = sizeB.toFloat()
        targetBuffer[writeIndex++] = scaleRate.toFloat()
        targetBuffer[writeIndex++] = maximumSizeA.toFloat()
        targetBuffer[writeIndex++] = maximumSizeB.toFloat()
        targetBuffer[writeIndex++] = effect.gpuRenderKind.code
        targetBuffer[writeIndex++] = particle.id.toFloat()
        targetBuffer[writeIndex++] = particle.material.restitution.toFloat()
        targetBuffer[writeIndex++] = particle.material.hardness.toFloat()
        targetBuffer[writeIndex++] = particle.material.adhesion.toFloat()
        targetBuffer[writeIndex++] = particle.material.friction.toFloat()
        targetBuffer[writeIndex++] = particle.material.staticFriction.toFloat()
        targetBuffer[writeIndex++] = particle.material.rollingResistance.toFloat()
        targetBuffer[writeIndex++] = if (gpuSplitFragmentsUseWallPlane()) 1f else 0f
        writeIndex += 1
        targetBuffer[writeIndex++] = appearance.color.red
        targetBuffer[writeIndex++] = appearance.color.green
        targetBuffer[writeIndex++] = appearance.color.blue
        targetBuffer[writeIndex++] = appearance.alpha.toFloat()
        targetBuffer[writeIndex++] = appearance.startColor.red
        targetBuffer[writeIndex++] = appearance.startColor.green
        targetBuffer[writeIndex++] = appearance.startColor.blue
        targetBuffer[writeIndex++] = appearance.initialAlpha.toFloat()
        targetBuffer[writeIndex++] = appearance.endColor.red
        targetBuffer[writeIndex++] = appearance.endColor.green
        targetBuffer[writeIndex++] = appearance.endColor.blue
        targetBuffer[writeIndex++] = appearance.endAlpha.toFloat()
        targetBuffer[writeIndex++] = particle.transform.origin.x.toFloat()
        targetBuffer[writeIndex++] = particle.transform.origin.y.toFloat()
        targetBuffer[writeIndex++] = (particle.animation?.frame ?: 0).toFloat()
        targetBuffer[writeIndex] = (particle.animation?.maxFrames ?: 0).toFloat()
    }

    protected open fun gpuKind(particle: Particle): Float = 0f

    protected open fun gpuSplitFragmentsUseWallPlane(): Boolean = false

    protected open fun gpuSpawnOffset(particle: Particle, size: Float): Float =
        if (particle.dimensions is CircleDimensions) size * 0.5f else 0f

    protected open fun gpuVelocityX(particle: Particle): Float = particle.dynamics.velocity.x.toFloat()

    protected open fun gpuVelocityY(particle: Particle): Float = particle.dynamics.velocity.y.toFloat()

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
