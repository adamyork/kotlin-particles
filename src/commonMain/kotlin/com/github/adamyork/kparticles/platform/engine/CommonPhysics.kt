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

        for (particleIndex in mapParticles.lastIndex downTo 0) {
            val particle = mapParticles[particleIndex]
            if (particle.age >= particle.lifetime + particle.delay) {
                completedParticleResults.add(CompletedParticleResult(particle.x.toInt(), particle.y.toInt(), particle.color))
                mapParticles.removeAt(particleIndex)
                continue
            }

            particle.age += deltaTime
            particle.isVisible = particle.age >= particle.delay
            if (particle.age < particle.delay) continue

            val activeAge = particle.age - particle.delay
            val progress = (activeAge / particle.lifetime).coerceIn(0.0, 1.0)

            particle.xVelocity += particle.xAcceleration * deltaTime
            particle.yVelocity += particle.yAcceleration * deltaTime
            particle.zVelocity += particle.zAcceleration * deltaTime

            val mass = particle.mass
            if (mass > 0.0) {
                val gravityScale = min(1.0, mass)
                particle.yVelocity += gravity * gravityScale * deltaTime
            }

            val totalDrag = max(0.0, particle.drag)
            if (totalDrag > 0.0) {
                val dragFactor = max(0.0, 1 - totalDrag * deltaTime)
                particle.xVelocity *= dragFactor
                particle.yVelocity *= dragFactor
                particle.zVelocity *= dragFactor
            }

            if (particle.maxXVelocity > 0.0) {
                particle.xVelocity = particle.xVelocity.coerceIn(-particle.maxXVelocity, particle.maxXVelocity)
            }
            if (particle.maxYVelocity > 0.0) {
                particle.yVelocity = particle.yVelocity.coerceIn(-particle.maxYVelocity, particle.maxYVelocity)
            }
            if (particle.maxZVelocity > 0.0) {
                particle.zVelocity = particle.zVelocity.coerceIn(-particle.maxZVelocity, particle.maxZVelocity)
            }

            particle.x += particle.xVelocity * deltaTime
            particle.y += particle.yVelocity * deltaTime
            particle.z += particle.zVelocity * deltaTime

            if (particle.growthRate != 0.0) {
                particle.radius = (particle.radius + particle.growthRate * deltaTime).coerceIn(0.0, particle.maxRadius)
                particle.width = (particle.width + particle.growthRate * deltaTime).coerceIn(0.0, particle.maxWidth)
                particle.height = (particle.height + particle.growthRate * deltaTime).coerceIn(0.0, particle.maxHeight)
            }

            particle.color = interpolateColor(particle.startColor, particle.endColor, progress)
            val startAlpha = particle.initialAlpha
            val endAlpha = particle.endAlpha
            particle.alphaMultiplier = progress
            particle.alpha = startAlpha + (endAlpha - startAlpha) * particle.alphaMultiplier
        }

    }

    override fun applyParticleCollisionPhysics(
        firstParticle: Particle,
        secondParticle: Particle,
        overlapDistance: Double,
        normalX: Double,
        normalY: Double
    ) {
        val firstParticleMass = firstParticle.mass
        val secondParticleMass = secondParticle.mass
        val totalMass = firstParticleMass + secondParticleMass
        if (totalMass <= 0.0) {
            return
        }
        firstParticle.x -= normalX * overlapDistance * (secondParticleMass / totalMass)
        firstParticle.y -= normalY * overlapDistance * (secondParticleMass / totalMass)
        secondParticle.x += normalX * overlapDistance * (firstParticleMass / totalMass)
        secondParticle.y += normalY * overlapDistance * (firstParticleMass / totalMass)
        val relativeVelocityX = firstParticle.xVelocity - secondParticle.xVelocity
        val relativeVelocityY = firstParticle.yVelocity - secondParticle.yVelocity
        val impulse = 2 * (normalX * relativeVelocityX + normalY * relativeVelocityY) / totalMass
        val restitution = min(firstParticle.restitution, secondParticle.restitution)
        firstParticle.xVelocity -= impulse * secondParticleMass * normalX * (1 + restitution)
        firstParticle.yVelocity -= impulse * secondParticleMass * normalY * (1 + restitution)
        secondParticle.xVelocity += impulse * firstParticleMass * normalX * (1 + restitution)
        secondParticle.yVelocity += impulse * firstParticleMass * normalY * (1 + restitution)
    }

    private fun interpolateColor(color1: Color, color2: Color, factor: Double): Color {
        val interpolationFactor = factor.coerceIn(0.0, 1.0).toFloat()
        return Color(
            red = color1.red + (color2.red - color1.red) * interpolationFactor,
            green = color1.green + (color2.green - color1.green) * interpolationFactor,
            blue = color1.blue + (color2.blue - color1.blue) * interpolationFactor,
            alpha = color1.alpha + (color2.alpha - color1.alpha) * interpolationFactor
        )
    }
}

