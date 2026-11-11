package com.github.adamyork.particles.engine

import androidx.compose.ui.graphics.Color
import com.github.adamyork.particles.engine.data.*
import kotlin.random.Random

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public abstract class AbstractGenerator(
    override val effect: ParticleEffect
) : ParticleEffectGenerator {

    private companion object {
        private const val GPU_PINNED_UNTIL_OFFSET: Int = 60
        private const val GPU_NON_GRAVITATING_OFFSET: Int = 61
    }

    override val particles: ArrayList<Particle> = ArrayList()

    public var random: Random = Random.Default

    private var nextParticleIdValue: Long = 0L

    override fun adopt(particle: Particle) {
        particles.add(particle.copy(id = nextParticleId()))
    }

    override fun writeGpuState(particle: Particle, targetBuffer: FloatArray, baseIndex: Int) {
        targetBuffer.fill(0f, baseIndex, baseIndex + ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE)
        val dimensions = particle.dimensions
        val isRectangle = dimensions is ParticleRectDimensions
        val sizeA = when (dimensions) {
            is ParticleRectDimensions -> dimensions.width
            is CircleDimensions -> dimensions.radius
        }
        val sizeB = when (dimensions) {
            is ParticleRectDimensions -> dimensions.height
            is CircleDimensions -> dimensions.radius
        }
        val scaleRate = when (dimensions) {
            is ParticleRectDimensions -> dimensions.scaleRate
            is CircleDimensions -> dimensions.scaleRate
        }
        val maximumSizeA = when (dimensions) {
            is ParticleRectDimensions -> dimensions.maxWidth
            is CircleDimensions -> dimensions.maxRadius
        }
        val maximumSizeB = when (dimensions) {
            is ParticleRectDimensions -> dimensions.maxHeight
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
        targetBuffer[writeIndex++] = effect.renderStyle.code
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
        targetBuffer[baseIndex + GPU_PINNED_UNTIL_OFFSET] = particle.lifecycle.pinnedUntil.toFloat()
        targetBuffer[baseIndex + GPU_NON_GRAVITATING_OFFSET] = if (particle.material.exertsGravity) 0f else 1f
    }

    protected fun nextParticleId(): Long = nextParticleIdValue++

    @Suppress("FunctionOnlyReturningConstant", "SameReturnValue")
    protected open fun gpuSplitFragmentsUseWallPlane(): Boolean = false

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
            red = random.nextFloat(),
            green = random.nextFloat(),
            blue = random.nextFloat(),
            alpha = 1.0f
        )
    }

    protected fun <T> shuffle(items: MutableList<T>) {
        for (currentIndex in items.lastIndex downTo 1) {
            val randomIndex = random.nextInt(currentIndex + 1)
            val swappedItem = items[currentIndex]
            items[currentIndex] = items[randomIndex]
            items[randomIndex] = swappedItem
        }
    }
}
