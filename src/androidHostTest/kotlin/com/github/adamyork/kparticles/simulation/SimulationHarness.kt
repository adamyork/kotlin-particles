package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.CommonParticleCollision
import com.github.adamyork.kparticles.platform.engine.CommonParticleFactory
import com.github.adamyork.kparticles.platform.engine.CommonParticlePhysics
import com.github.adamyork.kparticles.platform.engine.CommonSpatialGrid
import com.github.adamyork.kparticles.platform.engine.data.CompletedParticleResult
import com.github.adamyork.kparticles.platform.engine.data.Direction
import com.github.adamyork.kparticles.platform.engine.data.ParticleEffect
import com.github.adamyork.kparticles.platform.engine.data.SubframeSchedule
import com.github.adamyork.kparticles.platform.engine.effect.AbstractGenerator
import com.github.adamyork.kparticles.platform.engine.effect.AnimatedItemReturnGenerator
import com.github.adamyork.kparticles.platform.engine.effect.BlackHoleGenerator
import com.github.adamyork.kparticles.platform.engine.effect.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.engine.effect.BubbleGenerator
import com.github.adamyork.kparticles.platform.engine.effect.CollidingBitsGenerator
import com.github.adamyork.kparticles.platform.engine.effect.CollisionGenerator
import com.github.adamyork.kparticles.platform.engine.effect.DripsGenerator
import com.github.adamyork.kparticles.platform.engine.effect.DustGenerator
import com.github.adamyork.kparticles.platform.engine.effect.FireworkBurstGenerator
import com.github.adamyork.kparticles.platform.engine.effect.FireworkTailGenerator
import com.github.adamyork.kparticles.platform.engine.effect.GobblerGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ItemReturnGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ProjectileGenerator
import com.github.adamyork.kparticles.platform.engine.effect.ShatterGenerator
import com.github.adamyork.kparticles.platform.engine.effect.SpinnersGenerator
import com.github.adamyork.kparticles.platform.engine.effect.SpriteExplodeBackdropGenerator
import com.github.adamyork.kparticles.platform.engine.effect.SpriteExplodeGenerator
import com.github.adamyork.kparticles.platform.engine.effect.StressTestGenerator
import com.github.adamyork.kparticles.platform.service.CommonPhysicsSettingsService
import com.github.adamyork.kparticles.platform.service.CommonRuntimeService
import kotlin.math.sqrt
import kotlin.random.Random
import com.github.adamyork.kparticles.platform.engine.EngineDiagnostics

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class SimulationHarness(seed: Long, gravityScale: Double = 1.0) {

    val physicsSettingsService = CommonPhysicsSettingsService()

    private val assetService = FakeAssetService()

    private val platformInterop = FakePlatformInterop()

    private val runtimeService = CommonRuntimeService(assetService, physicsSettingsService)

    private val engineDiagnostics = EngineDiagnostics()

    private val particlePhysics = CommonParticlePhysics({ runtimeService }, physicsSettingsService, engineDiagnostics)

    private val generators: List<AbstractGenerator> = listOf(
        DustGenerator(physicsSettingsService),
        CollisionGenerator(physicsSettingsService),
        ProjectileGenerator(physicsSettingsService),
        BlobProjectileGenerator(physicsSettingsService),
        FireworkBurstGenerator(physicsSettingsService),
        FireworkTailGenerator(physicsSettingsService),
        ItemReturnGenerator(physicsSettingsService),
        AnimatedItemReturnGenerator(physicsSettingsService),
        BubbleGenerator(physicsSettingsService),
        CollidingBitsGenerator(physicsSettingsService),
        GobblerGenerator(physicsSettingsService),
        BlackHoleGenerator(physicsSettingsService),
        StressTestGenerator(platformInterop, physicsSettingsService),
        ShatterGenerator(physicsSettingsService),
        DripsGenerator(physicsSettingsService),
        SpinnersGenerator(physicsSettingsService),
        SpriteExplodeGenerator(physicsSettingsService),
        SpriteExplodeBackdropGenerator()
    )

    private val particleFactory = CommonParticleFactory(generators.toSet())

    private val collision = CommonParticleCollision(
        particlePhysics,
        CommonSpatialGrid(physicsSettingsService, engineDiagnostics),
        physicsSettingsService,
        engineDiagnostics
    )

    private val completedParticleResults = ArrayList<CompletedParticleResult>()

    private var frameTimeMilliseconds = INITIAL_FRAME_TIME_MILLISECONDS

    private var totalSubframes = 0

    private var simulatedTime = 0.0

    private var peakCount = 0

    val viewPort = ViewPort(0, 768, 1024, 768)

    init {
        runtimeService.deriveGravityFromViewportHeight(viewPort.height)
        physicsSettingsService.gravity *= gravityScale
        for (generator in generators) {
            generator.random = Random(seed * SEED_STRIDE + generator.effect.ordinal)
        }
        particleFactory.populateColorMap(assetService)
        runtimeService.lastPaintTime = frameTimeMilliseconds
        runtimeService.setCurrentFrameTime(frameTimeMilliseconds)
    }

    fun spawn(effect: ParticleEffect) {
        particleFactory.create(
            type = effect,
            x = viewPort.x + viewPort.width / 2.0,
            y = viewPort.y + viewPort.height / 2.0,
            viewPort = viewPort,
            direction = Direction.RIGHT
        )
    }

    fun advance(hostDeltaTicks: Double): SubframeSchedule {
        frameTimeMilliseconds += hostDeltaTicks * MILLISECONDS_PER_TICK
        runtimeService.setCurrentFrameTime(frameTimeMilliseconds)
        val subframeSchedule = runtimeService.getSubframeSchedule()
        repeat(subframeSchedule.subframeCount) {
            particlePhysics.applyParticlePhysics(
                particleFactory.generators(), viewPort, completedParticleResults, subframeSchedule.subframeDeltaTime
            )
            collision.applyParticleCollision(particleFactory.generators(), viewPort, subframeSchedule.subframeDeltaTime)
        }
        runtimeService.lastPaintTime = frameTimeMilliseconds
        totalSubframes += subframeSchedule.subframeCount
        simulatedTime += subframeSchedule.subframeCount * subframeSchedule.subframeDeltaTime
        peakCount = maxOf(peakCount, generators.sumOf { generator -> generator.particles.size })
        return subframeSchedule
    }

    fun simulatedTime(): Double = simulatedTime

    fun snapshot(): Map<String, Double> {
        var count = 0
        var nonFinite = 0
        var outside = 0
        var kineticEnergy = 0.0
        var speedSum = 0.0
        var speedMax = 0.0
        var positionXSum = 0.0
        var positionYSum = 0.0
        for (generator in generators) {
            for (particle in generator.particles) {
                val position = particle.transform.position
                val velocity = particle.dynamics.velocity
                count++
                if (!position.x.isFinite() || !position.y.isFinite() || !velocity.x.isFinite() || !velocity.y.isFinite()) {
                    nonFinite++
                    continue
                }
                if (position.x < viewPort.x || position.x > viewPort.x + viewPort.width ||
                    position.y < viewPort.y || position.y > viewPort.y + viewPort.height
                ) {
                    outside++
                }
                val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
                kineticEnergy += 0.5 * particle.inertialMass() * speed * speed
                speedSum += speed
                if (speed > speedMax) speedMax = speed
                positionXSum += position.x
                positionYSum += position.y
            }
        }
        val finiteCount = (count - nonFinite).coerceAtLeast(1)
        return linkedMapOf(
            "count" to count.toDouble(),
            "peakCount" to peakCount.toDouble(),
            "completed" to completedParticleResults.size.toDouble(),
            "nonFinite" to nonFinite.toDouble(),
            "outsideViewport" to outside.toDouble(),
            "kineticEnergy" to kineticEnergy,
            "speedMean" to speedSum / finiteCount,
            "speedMax" to speedMax,
            "centerX" to positionXSum / finiteCount,
            "centerY" to positionYSum / finiteCount,
            "substeps" to totalSubframes.toDouble(),
            "simulatedTime" to simulatedTime
        )
    }

    private companion object {
        const val SEED_STRIDE: Long = 1000L
        const val INITIAL_FRAME_TIME_MILLISECONDS: Double = 1000.0
        const val MILLISECONDS_PER_TICK: Double = 1000.0 / FakeAssetService.TICKS_PER_SECOND
    }

}
