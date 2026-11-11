package com.github.adamyork.kparticles.simulation

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.gui.data.HarnessViewPort
import com.github.adamyork.particles.engine.CommonParticleCollision
import com.github.adamyork.particles.engine.CommonParticlePhysics
import com.github.adamyork.particles.engine.CommonSpatialGrid
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.data.CircleDimensions
import com.github.adamyork.particles.engine.data.Particle
import com.github.adamyork.particles.engine.data.ParticleAppearance
import com.github.adamyork.particles.engine.data.ParticleDimensions
import com.github.adamyork.particles.engine.data.ParticleDynamics
import com.github.adamyork.particles.engine.data.ParticleLifecycle
import com.github.adamyork.particles.engine.data.ParticleMaterial
import com.github.adamyork.particles.engine.data.ParticleTransform
import com.github.adamyork.particles.engine.data.ParticleVector
import com.github.adamyork.particles.engine.data.ParticleRectDimensions
import com.github.adamyork.particles.service.CommonParticlePhysicsSettingsService
import com.github.adamyork.particles.service.CommonRuntimeService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.github.adamyork.particles.engine.CommonParticleEngineDiagnostics
import com.github.adamyork.kparticles.platform.effects.ParticleEffects

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class ContactPhysicsTest {

    private class Outcome(
        val closingSpeed: Double,
        val separatingSpeed: Double,
        val momentumError: Double,
        val energyBefore: Double,
        val energyAfter: Double
    )

    private fun collide(
        firstDimensions: ParticleDimensions,
        secondDimensions: ParticleDimensions,
        secondDensity: Double,
        restitution: Double,
        separation: Double
    ): Outcome {
        val settings = CommonParticlePhysicsSettingsService()
        settings.gravity = 0.0
        settings.drag = 0.0
        settings.dragCoefficient = 0.0
        settings.mediumDensity = 0.0
        settings.gravitationalConstant = 0.0
        val runtimeService = CommonRuntimeService(FixedEngineTiming(), settings)
        val particleEffectRegistry = ParticleEffectRegistry(ParticleEffects.ALL)
        val particleEngineDiagnostics = CommonParticleEngineDiagnostics(particleEffectRegistry)
        val physics = CommonParticlePhysics({ runtimeService }, settings, particleEngineDiagnostics, particleEffectRegistry)
        val collision = CommonParticleCollision(
            physics,
            CommonSpatialGrid(settings),
            settings
        )
        val generator = ManualGenerator()
        val first = particle(0L, firstDimensions, FIRST_DENSITY, restitution, 500.0, CLOSING_SPEED / 2.0)
        val second = particle(1L, secondDimensions, secondDensity, restitution, 500.0 + separation, -CLOSING_SPEED / 2.0)
        generator.particles.add(first)
        generator.particles.add(second)
        val viewPort = HarnessViewPort(0, 768, 1024, 768)
        val momentumBefore = momentum(first, second)
        val energyBefore = energy(first, second)
        collision.applyParticleCollision(listOf(generator), viewPort, 1.0)
        val momentumAfter = momentum(first, second)
        return Outcome(
            closingSpeed = CLOSING_SPEED,
            separatingSpeed = second.dynamics.velocity.x - first.dynamics.velocity.x,
            momentumError = momentumAfter - momentumBefore,
            energyBefore = energyBefore,
            energyAfter = energy(first, second)
        )
    }

    private fun particle(
        id: Long,
        dimensions: ParticleDimensions,
        density: Double,
        restitution: Double,
        positionX: Double,
        velocityX: Double
    ): Particle = Particle(
        id = id,
        lifecycle = ParticleLifecycle(lifetime = 10000.0),
        appearance = ParticleAppearance(color = Color.White),
        dimensions = dimensions,
        transform = ParticleTransform(position = ParticleVector(positionX, 1150.0, 0.0)),
        material = ParticleMaterial(density = density, restitution = restitution),
        dynamics = ParticleDynamics(velocity = ParticleVector(velocityX, 0.0, 0.0))
    )

    private fun momentum(first: Particle, second: Particle): Double =
        first.inertialMass() * first.dynamics.velocity.x + second.inertialMass() * second.dynamics.velocity.x

    private fun energy(first: Particle, second: Particle): Double =
        0.5 * first.inertialMass() * first.dynamics.velocity.x * first.dynamics.velocity.x +
            0.5 * second.inertialMass() * second.dynamics.velocity.x * second.dynamics.velocity.x

    private fun circle(radius: Double): ParticleDimensions = CircleDimensions(radius = radius, maxRadius = radius)

    private fun box(size: Double): ParticleDimensions =
        ParticleRectDimensions(width = size, height = size, maxWidth = size, maxHeight = size)

    private fun assertPhysical(label: String, outcome: Outcome, restitution: Double) {
        assertEquals(0.0, outcome.momentumError, 1e-6 * (1.0 + outcome.energyBefore), "$label momentum not conserved")
        assertEquals(
            restitution * outcome.closingSpeed,
            outcome.separatingSpeed,
            1e-6,
            "$label separation speed should be restitution times closing speed"
        )
        assertTrue(
            outcome.energyAfter <= outcome.energyBefore * (1.0 + 1e-9),
            "$label gained energy: ${outcome.energyBefore} -> ${outcome.energyAfter}"
        )
    }

    @Test
    fun equalCirclesObeyRestitution() {
        for (restitution in RESTITUTIONS) {
            assertPhysical(
                "circle e=$restitution",
                collide(circle(10.0), circle(10.0), 1.0, restitution, 19.0),
                restitution
            )
        }
    }

    @Test
    fun unequalCirclesObeyRestitution() {
        for (restitution in RESTITUTIONS) {
            assertPhysical(
                "unequal circle e=$restitution",
                collide(circle(10.0), circle(6.0), 3.0, restitution, 15.0),
                restitution
            )
        }
    }

    @Test
    fun equalBoxesObeyRestitution() {
        for (restitution in RESTITUTIONS) {
            assertPhysical(
                "box e=$restitution",
                collide(box(20.0), box(20.0), 1.0, restitution, 19.0),
                restitution
            )
        }
    }

    @Test
    fun unequalBoxesObeyRestitution() {
        for (restitution in RESTITUTIONS) {
            assertPhysical(
                "unequal box e=$restitution",
                collide(box(20.0), box(12.0), 3.0, restitution, 15.0),
                restitution
            )
        }
    }

    private companion object {
        val RESTITUTIONS = listOf(0.0, 0.5, 1.0)
        const val FIRST_DENSITY: Double = 1.0
        const val CLOSING_SPEED: Double = 20.0
    }

}
