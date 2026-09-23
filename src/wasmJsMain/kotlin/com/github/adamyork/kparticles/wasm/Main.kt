package com.github.adamyork.kparticles.wasm

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.github.adamyork.kparticles.platform.LogConfig
import com.github.adamyork.kparticles.platform.engine.data.Particle
import com.github.adamyork.kparticles.platform.gui.UiScaffold
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import kotlin.math.min
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    LogConfig.initialize(minimumLevel = Level.DEBUG)
    logger.info { "Wasm main invoked" }
    val component = AppConfig::class.create()
    component.platformInterop.onReady {
        component.screenDimensionsService.initialize(
            component.platformInterop.getWindowWidth().toInt(),
            component.platformInterop.getWindowHeight().toInt()
        )
        (document.getElementById("ComposeTarget") as? HTMLElement)?.apply {
            setAttribute("tabindex", "0")
            style.apply {
                width = "${component.platformInterop.getWindowWidth().toInt()}px"
                height = "${component.platformInterop.getWindowHeight().toInt()}px"
                minWidth = "${component.platformInterop.getWindowWidth().toInt()}px"
                minHeight = "${component.platformInterop.getWindowHeight().toInt()}px"
                maxWidth = "${component.platformInterop.getWindowWidth().toInt()}px"
                maxHeight = "${component.platformInterop.getWindowHeight().toInt()}px"
            }
        }
        component.platformInterop.hidePlatformLoader()
        val gameLayer = component.testBed
        val sparrowColorScheme = component.testBedColorScheme
        ComposeViewport(viewportContainerId = "ComposeTarget") {
            UiScaffold().BuildGui(gameLayer, sparrowColorScheme)
        }
    }
//    val canvas = document.getElementById("particleCanvas") as HTMLCanvasElement
//    val modeSelect = document.getElementById("particleMode") as HTMLSelectElement
//    val createButton = document.getElementById("createBtn") as HTMLButtonElement
//    val fpsLabel = document.getElementById("fpsLabel") as HTMLDivElement
//
//    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
//    val engine = ParticleEngine(gravity = ParticleVector(0.0, 0.05, 0.0), globalDrag = 0.01)
//    val factory = ParticleFactory(imageSrc = "image.png")
//    val image = document.createElement("img") as HTMLImageElement
//    image.src = "image.png"
//
//    var particles = factory.create("dust", canvas.width / 2.0, canvas.height / 2.0)
//    var frameCount = 0
//    var lastTime = window.performance.now()
//
//    createButton.addEventListener("click", {
//        val mode = modeSelect.value
//        val centerX = canvas.width / 2.0
//        val centerY = canvas.height / 2.0
//
//        particles = when (mode) {
//            "projectile", "itemReturn" -> factory.create(mode, centerX, centerY, canvas.width.toDouble(), 0.0)
//            "collision" -> factory.create(mode, centerX, centerY, direction = "left")
//            else -> factory.create(mode, centerX, centerY)
//        }
//    })
//
//    fun animate(currentTime: Double) {
//        frameCount++
//        if (currentTime - lastTime >= 1000) {
//            val fps = ((frameCount * 1000) / (currentTime - lastTime)).toInt()
//            fpsLabel.textContent = "FPS: $fps | Particles: ${particles.size}"
//            frameCount = 0
//            lastTime = currentTime
//        }
//
//        ctx.clearRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
//
//        val expired = engine.update(particles, 1.0)
//        expired.forEach { p ->
//            if (p.type == ParticleType.FIREWORK_TAIL) {
//                particles.addAll(factory.create("fireworkBurst", p.x, p.y))
//            }
//        }
//
//        handleCollisions(particles)
//
//        particles.forEach { p ->
//            if (!p.isVisible) return@forEach
//            ctx.save()
//            ctx.globalAlpha = max(0.0, min(1.0, p.alpha))
//
//            if (p.type == ParticleType.ITEM_RETURN && image.complete) {
//                val w = if (p.width > 0) p.width else p.radius * 2
//                val h = if (p.height > 0) p.height else p.radius * 2
//                ctx.drawImage(image, p.x - w / 2, p.y - h / 2, w, h)
//            } else {
//                ctx.fillStyle = p.color.toJsString()
//                if (p.shape == ParticleShape.CIRCLE) {
//                    ctx.beginPath()
//                    ctx.arc(p.x, p.y, max(0.0, p.radius), 0.0, PI * 2)
//                    ctx.fill()
//                } else {
//                    ctx.fillRect(p.x - p.width / 2, p.y - p.height / 2, p.width, p.height)
//                }
//            }
//            ctx.restore()
//        }
//
//        window.requestAnimationFrame(::animate)
//    }
//
//    window.requestAnimationFrame(::animate)
}

private fun handleCollisions(particles: MutableList<Particle>) {
    for (i in particles.indices) {
        for (j in i + 1 until particles.size) {
            val p1 = particles[i]
            val p2 = particles[j]
            if (!p1.canCollide || !p2.canCollide) continue

            val r1 = if (p1.radius > 0) p1.radius else p1.width / 2
            val r2 = if (p2.radius > 0) p2.radius else p2.width / 2

            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val dist = sqrt(dx * dx + dy * dy)
            val minDist = r1 + r2

            if (dist < minDist && dist > 0) {
                val overlap = minDist - dist
                val nx = dx / dist
                val ny = dy / dist

                val m1 = p1.mass ?: 1.0
                val m2 = p2.mass ?: 1.0
                val totalMass = m1 + m2

                p1.x -= nx * overlap * (m2 / totalMass)
                p1.y -= ny * overlap * (m2 / totalMass)
                p2.x += nx * overlap * (m1 / totalMass)
                p2.y += ny * overlap * (m1 / totalMass)

                val kx = p1.xVelocity - p2.xVelocity
                val ky = p1.yVelocity - p2.yVelocity
                val impulse = 2 * (nx * kx + ny * ky) / totalMass

                val restitution = min(p1.restitution, p2.restitution)

                p1.xVelocity -= impulse * m2 * nx * (1 + restitution)
                p1.yVelocity -= impulse * m2 * ny * (1 + restitution)
                p2.xVelocity += impulse * m1 * nx * (1 + restitution)
                p2.yVelocity += impulse * m1 * ny * (1 + restitution)
            }
        }
    }
}
