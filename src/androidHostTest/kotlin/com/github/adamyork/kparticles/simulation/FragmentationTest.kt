package com.github.adamyork.kparticles.simulation

import androidx.compose.ui.graphics.Color
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.CommonParticleCollision
import com.github.adamyork.kparticles.platform.engine.CommonParticlePhysics
import com.github.adamyork.kparticles.platform.engine.CommonSpatialGrid
import com.github.adamyork.kparticles.platform.engine.data.CircleDimensions
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.engine.data.ParticleAppearance
import com.github.adamyork.kparticles.platform.engine.data.ParticleDynamics
import com.github.adamyork.kparticles.platform.engine.data.ParticleLifecycle
import com.github.adamyork.kparticles.platform.engine.data.ParticleMaterial
import com.github.adamyork.kparticles.platform.engine.data.ParticleTransform
import com.github.adamyork.kparticles.platform.engine.data.ParticleVector
import com.github.adamyork.kparticles.platform.service.CommonPhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.CommonRuntimeService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.github.adamyork.kparticles.platform.engine.EngineDiagnostics

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class FragmentationTest {

    private val settings = CommonPhysicsSettingsService().also { configuredSettings ->
        configuredSettings.gravity = 0.0
        configuredSettings.drag = 0.0
        configuredSettings.dragCoefficient = 0.0
        configuredSettings.mediumDensity = 0.0
        configuredSettings.gravitationalConstant = 0.0
    }

    private val generator = ManualGenerator()

    private val engineDiagnostics = EngineDiagnostics()

    private val collision = CommonParticleCollision(
        CommonParticlePhysics({ CommonRuntimeService(FakeAssetService(), settings) }, settings, engineDiagnostics),
        CommonSpatialGrid(settings, engineDiagnostics),
        settings,
        engineDiagnostics
    )

    private val viewPort = ViewPort(0, 768, 1024, 768)

    private fun circle(id: Long, hardness: Int, positionX: Double, velocityX: Double): Particle = Particle(
        id = id,
        lifecycle = ParticleLifecycle(lifetime = 10000.0),
        appearance = ParticleAppearance(color = Color.White),
        dimensions = CircleDimensions(radius = 12.0, maxRadius = 12.0),
        transform = ParticleTransform(position = ParticleVector(positionX, 1150.0, 0.0)),
        material = ParticleMaterial(density = 1.0, restitution = 0.5, hardness = hardness),
        dynamics = ParticleDynamics(velocity = ParticleVector(velocityX, 0.0, 0.0))
    )

    private fun impact(closingSpeed: Double): Pair<Particle, Particle> {
        val soft = circle(0L, 0, 500.0, closingSpeed / 2.0)
        val hard = circle(1L, 2, 523.0, -closingSpeed / 2.0)
        generator.particles.add(soft)
        generator.particles.add(hard)
        return Pair(soft, hard)
    }

    private fun energy(): Double = generator.particles.sumOf { particle ->
        0.5 * particle.inertialMass() *
            (particle.dynamics.velocity.x * particle.dynamics.velocity.x +
                particle.dynamics.velocity.y * particle.dynamics.velocity.y)
    }

    @Test
    fun restingContactNeverFragments() {
        impact(0.0)
        repeat(50) {
            collision.applyParticleCollision(listOf(generator), viewPort, 1.0)
        }
        assertEquals(2, generator.particles.size)
    }

    @Test
    fun gentleImpactDoesNotFragment() {
        impact(4.0)
        collision.applyParticleCollision(listOf(generator), viewPort, 1.0)
        assertEquals(2, generator.particles.size)
    }

    @Test
    fun fastImpactFragmentsWithoutGainingEnergy() {
        impact(40.0)
        val energyBefore = energy()
        collision.applyParticleCollision(listOf(generator), viewPort, 1.0)
        assertTrue(generator.particles.size > 2, "a fast impact should fragment the softer particle")
        assertTrue(energy() <= energyBefore * (1.0 + 1e-9), "fragmentation gained energy: $energyBefore -> ${energy()}")
    }

    @Test
    fun fragmentsDoNotCascadeWithoutNewEnergy() {
        impact(40.0)
        collision.applyParticleCollision(listOf(generator), viewPort, 1.0)
        val countAfterImpact = generator.particles.size
        repeat(60) {
            for (particle in generator.particles) {
                particle.transform.position.x += particle.dynamics.velocity.x
                particle.transform.position.y += particle.dynamics.velocity.y
            }
            collision.applyParticleCollision(listOf(generator), viewPort, 1.0)
        }
        assertTrue(
            generator.particles.size <= countAfterImpact * 2,
            "fragments kept shattering: $countAfterImpact -> ${generator.particles.size}"
        )
    }

}
