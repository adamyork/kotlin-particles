package com.github.adamyork.kparticles.platform.engine

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.service.CommonRuntimeService
import com.github.adamyork.kparticles.platform.service.PhysicsSettingsService
import io.github.oshai.kotlinlogging.KotlinLogging
import me.tatarka.inject.annotations.Inject
import kotlin.math.max
import kotlin.math.min

@AppScope
@Inject
class CommonPhysics(
    private val statusProviderFactory: () -> CommonRuntimeService,
    val physicsSettingsService: PhysicsSettingsService
) : Physics {

    private val logger = KotlinLogging.logger {}

    private val statusProvider: CommonRuntimeService
        get() = statusProviderFactory()

    override fun applyParticlePhysics(
        mapParticles: ArrayList<Particle>,
        viewPort: ViewPort,
        completedParticleResults: ArrayList<CompletedParticleResult>
    ) {
        val deltaTime = statusProvider.getDeltaTimeCoefficient()
        val gravity = physicsSettingsService.gravity
        val globalDrag = physicsSettingsService.drag

        for (i in mapParticles.lastIndex downTo 0) {
            val p = mapParticles[i]
            if (p.age >= p.lifetime + p.delay) {
                completedParticleResults.add(CompletedParticleResult(p.x.toInt(), p.y.toInt(), p.color))
                mapParticles.removeAt(i)
                continue
            }

            p.age += deltaTime
            p.isVisible = p.age >= p.delay
            if (p.age < p.delay) continue

            val activeAge = p.age - p.delay
            val progress = (activeAge / p.lifetime).coerceIn(0.0, 1.0)

            p.xVelocity += p.xAcceleration * deltaTime
            p.yVelocity += p.yAcceleration * deltaTime
            p.zVelocity += p.zAcceleration * deltaTime

            val mass = p.mass
            if (mass != null && mass > 0.0) {
                val gravityScale = min(1.0, mass)
                p.xVelocity += gravity * gravityScale * deltaTime
                p.yVelocity += gravity * gravityScale * deltaTime
                p.zVelocity += gravity * gravityScale * deltaTime
            }

            val totalDrag = max(0.0, p.drag + globalDrag)
            if (totalDrag > 0.0) {
                val dragFactor = max(0.0, 1 - totalDrag * deltaTime)
                p.xVelocity *= dragFactor
                p.yVelocity *= dragFactor
                p.zVelocity *= dragFactor
            }

            if (p.maxXVelocity > 0.0) {
                p.xVelocity = p.xVelocity.coerceIn(-p.maxXVelocity, p.maxXVelocity)
            }
            if (p.maxYVelocity > 0.0) {
                p.yVelocity = p.yVelocity.coerceIn(-p.maxYVelocity, p.maxYVelocity)
            }
            if (p.maxZVelocity > 0.0) {
                p.zVelocity = p.zVelocity.coerceIn(-p.maxZVelocity, p.maxZVelocity)
            }

            p.x += p.xVelocity * deltaTime
            p.y += p.yVelocity * deltaTime
            p.z += p.zVelocity * deltaTime

            if (p.growthRate != 0.0) {
                p.radius = (p.radius + p.growthRate * deltaTime).coerceIn(0.0, p.maxRadius)
                p.width = (p.width + p.growthRate * deltaTime).coerceIn(0.0, p.maxWidth)
                p.height = (p.height + p.growthRate * deltaTime).coerceIn(0.0, p.maxHeight)
            }

            p.color = interpolateColor(p.startColor, p.endColor, progress)
            p.endAlpha?.let { end ->
                val start = p.initialAlpha ?: p.alpha
                p.alpha = start + (end - start) * progress
            }
        }

    }

    private fun interpolateColor(color1: Color, color2: Color, factor: Double): Color {
        val t = factor.coerceIn(0.0, 1.0).toFloat()
        return Color(
            red = color1.red + (color2.red - color1.red) * t,
            green = color1.green + (color2.green - color1.green) * t,
            blue = color1.blue + (color2.blue - color1.blue) * t,
            alpha = color1.alpha + (color2.alpha - color1.alpha) * t
        )
    }
}

