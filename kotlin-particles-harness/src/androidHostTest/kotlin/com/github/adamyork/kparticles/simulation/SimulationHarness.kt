package com.github.adamyork.kparticles.simulation

import com.github.adamyork.kparticles.platform.gui.data.HarnessViewPort
import com.github.adamyork.particles.engine.CommonParticleCollision
import com.github.adamyork.particles.engine.CommonParticlePhysicsEngine
import com.github.adamyork.particles.engine.CommonParticlePhysics
import com.github.adamyork.particles.engine.CommonSpatialGrid
import com.github.adamyork.particles.engine.ParticleEffectRegistry
import com.github.adamyork.particles.engine.data.ParticleDirection
import com.github.adamyork.particles.engine.data.ParticleEffect
import com.github.adamyork.particles.engine.data.ParticleSubframeSchedule
import com.github.adamyork.particles.engine.AbstractGenerator
import com.github.adamyork.kparticles.platform.effects.AnimatedItemReturnGenerator
import com.github.adamyork.kparticles.platform.effects.BlackHoleGenerator
import com.github.adamyork.kparticles.platform.effects.BlobProjectileGenerator
import com.github.adamyork.kparticles.platform.effects.BubbleGenerator
import com.github.adamyork.kparticles.platform.effects.CollidingBitsGenerator
import com.github.adamyork.kparticles.platform.effects.CollisionGenerator
import com.github.adamyork.kparticles.platform.effects.DripsGenerator
import com.github.adamyork.kparticles.platform.effects.DustGenerator
import com.github.adamyork.kparticles.platform.effects.FireworkGenerator
import com.github.adamyork.kparticles.platform.effects.GobblerGenerator
import com.github.adamyork.kparticles.platform.effects.ItemReturnGenerator
import com.github.adamyork.kparticles.platform.effects.ProjectileGenerator
import com.github.adamyork.kparticles.platform.effects.ShatterGenerator
import com.github.adamyork.kparticles.platform.effects.SpinnersGenerator
import com.github.adamyork.kparticles.platform.effects.SpriteExplodeBackdropGenerator
import com.github.adamyork.kparticles.platform.effects.SpriteExplodeGenerator
import com.github.adamyork.kparticles.platform.effects.StressTestGenerator
import com.github.adamyork.kparticles.platform.assets.CollectibleImageStore
import com.github.adamyork.kparticles.platform.assets.data.ImageAndBytes
import com.github.adamyork.kparticles.platform.assets.data.ImageAsset
import com.github.adamyork.particles.service.CommonParticlePhysicsSettingsService
import com.github.adamyork.particles.service.CommonRuntimeService
import kotlin.math.sqrt
import kotlin.random.Random
import com.github.adamyork.particles.engine.CommonParticleEngineDiagnostics
import com.github.adamyork.particles.engine.NoOpParticleEngineDiagnostics
import com.github.adamyork.particles.engine.ParticleEngineDiagnostics
import com.github.adamyork.kparticles.platform.effects.ParticleEffects

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class SimulationHarness(seed: Long, gravityScale: Double = 1.0, metricsEnabled: Boolean = true) {

    val physicsSettingsService = CommonParticlePhysicsSettingsService()

    private val assetService = FakeAssetService()

    private val platformInterop = FakePlatformInterop()

    private val runtimeService = CommonRuntimeService(FixedEngineTiming(), physicsSettingsService)

    private val collectibleImageStore = CollectibleImageStore()

    val particleEffectRegistry = ParticleEffectRegistry(ParticleEffects.ALL)

    private val particleEngineDiagnostics: ParticleEngineDiagnostics =
        if (metricsEnabled) CommonParticleEngineDiagnostics(particleEffectRegistry) else NoOpParticleEngineDiagnostics()

    private val particlePhysics = CommonParticlePhysics(
        { runtimeService },
        physicsSettingsService,
        particleEngineDiagnostics,
        particleEffectRegistry
    )

    private val generators: List<AbstractGenerator> = listOf(
        DustGenerator(physicsSettingsService, assetService),
        CollisionGenerator(physicsSettingsService, assetService),
        ProjectileGenerator(physicsSettingsService, assetService),
        BlobProjectileGenerator(physicsSettingsService, assetService),
        FireworkGenerator(physicsSettingsService),
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
        SpriteExplodeGenerator(physicsSettingsService, collectibleImageStore),
        SpriteExplodeBackdropGenerator(collectibleImageStore)
    )

    private val collision = CommonParticleCollision(
        particlePhysics,
        CommonSpatialGrid(physicsSettingsService),
        physicsSettingsService
    )

    private val particlePhysicsEngine = CommonParticlePhysicsEngine(
        particlePhysics,
        collision,
        particleEffectRegistry,
        runtimeService,
        particleEngineDiagnostics
    )

    private var frameTimeMilliseconds = INITIAL_FRAME_TIME_MILLISECONDS

    private var totalSubframes = 0

    private var simulatedTime = 0.0

    private var peakCount = 0

    val viewPort = HarnessViewPort(0, 768, 1024, 768)

    init {
        for (generator in generators) {
            particleEffectRegistry.register(generator)
        }
        runtimeService.deriveGravityFromViewportHeight(viewPort.height)
        physicsSettingsService.gravity *= gravityScale
        for (generator in generators) {
            generator.random = Random(seed * SEED_STRIDE + particleEffectRegistry.indexOf(generator.effect))
        }
        collectibleImageStore.image = ImageAsset(
            COLLECTIBLE_IMAGE_SIZE,
            COLLECTIBLE_IMAGE_SIZE,
            ImageAndBytes(ByteArray(0), FakeImageBitmap(COLLECTIBLE_IMAGE_SIZE, COLLECTIBLE_IMAGE_SIZE))
        )
        particleEffectRegistry.notifyAssetsReady()
        runtimeService.lastPaintTime = frameTimeMilliseconds
        runtimeService.setCurrentFrameTime(frameTimeMilliseconds)
    }

    fun spawn(effect: ParticleEffect) {
        particleEffectRegistry.spawn(
            effect = effect,
            x = viewPort.x + viewPort.width / 2.0,
            y = viewPort.y + viewPort.height / 2.0,
            viewPort = viewPort,
            direction = ParticleDirection.RIGHT
        )
    }

    fun advance(hostDeltaTicks: Double): ParticleSubframeSchedule {
        frameTimeMilliseconds += hostDeltaTicks * MILLISECONDS_PER_TICK
        runtimeService.setCurrentFrameTime(frameTimeMilliseconds)
        val subframeSchedule = particlePhysicsEngine.applyPhysics(viewPort)
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
        const val COLLECTIBLE_IMAGE_SIZE: Int = 32
        const val INITIAL_FRAME_TIME_MILLISECONDS: Double = 1000.0
        const val MILLISECONDS_PER_TICK: Double = 1000.0 / FakeAssetService.TICKS_PER_SECOND
    }

}
