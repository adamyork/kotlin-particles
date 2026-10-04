package com.github.adamyork.kparticles.android.engine

import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import com.github.adamyork.kparticles.android.engine.data.AndroidGpuParticleFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil

/**
 * OpenGL ES 3.1 renderer that runs particle compute + draw passes each frame.
 */
internal class AndroidGpuParticleRenderer(
    private val maxParticles: Int,
    private val frameProvider: () -> AndroidGpuParticleFrame?,
    private val computeShaderSource: String,
    private val vertexShaderSource: String,
    private val fragmentShaderSource: String
) : GLSurfaceView.Renderer {

    companion object {
        private const val FLOATS_PER_PARTICLE = 16
        private const val IDX_ALIVE = 7
        private const val IDX_KIND = 12
        private const val KIND_DUST = 1f
        private const val KIND_PROJECTILE = 2f
        private const val KIND_MAP_ITEM_RETURN = 3f
        private const val RENDERER_DIAGNOSTIC_LOG_EVERY_N_FRAMES = 60
    }

    private val logger = KotlinLogging.logger {}

    private val workgroupCount = ceil(maxParticles.toDouble() / 64.0).toInt().coerceAtLeast(1)
    private var computeProgram = 0
    private var renderProgram = 0
    private var vao = 0

    private var stateBufferA = 0
    private var stateBufferB = 0
    private var spawnBuffer = 0
    private var useStateAAsSource = true

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    private var uDeltaTime = -1
    private var uGravity = -1
    private var uBurstFrameGrowthMultiplier = -1
    private var uBurstSpeedCoefficient = -1
    private var uProjectileSpeed = -1
    private var uMapItemReturnSpeed = -1
    private var uMapItemReturnMinTravelDist = -1
    private var uPlayerRect = -1
    private var uViewPortRect = -1
    private var uMaxParticles = -1

    private var uViewPort = -1
    private var uSurfaceSize = -1
    private var uViewPortSize = -1
    private var uSizeScale = -1
    private var frameCounter = 0
    private var emptyFrameCounter = 0
    private var rendererReady = false
    private var renderedDebugFirstFrame = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        logger.info { "[GPU][Renderer] onSurfaceCreated" }
        check(computeShaderSource.isNotBlank()) { "Compute shader source is empty" }
        check(vertexShaderSource.isNotBlank()) { "Vertex shader source is empty" }
        check(fragmentShaderSource.isNotBlank()) { "Fragment shader source is empty" }

        val glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
        val glslVersion = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION).orEmpty()
        logger.info { "[GPU][Renderer] GL_VERSION='$glVersion', GLSL='$glslVersion'" }
        if (!supportsComputeShaders(glVersion)) {
            logger.error {
                "[GPU][Renderer] OpenGL ES 3.1+ compute is unavailable on this runtime; " +
                    "GPU particle renderer is disabled"
            }
            rendererReady = false
            return
        }

        GLES31.glDisable(GLES31.GL_DEPTH_TEST)
        GLES31.glEnable(GLES31.GL_BLEND)
        GLES31.glBlendFunc(GLES31.GL_SRC_ALPHA, GLES31.GL_ONE_MINUS_SRC_ALPHA)
        GLES31.glClearColor(0f, 0f, 0f, 0f)

        try {
            computeProgram = createComputeProgram()
            renderProgram = createRenderProgram()
            vao = createVertexArrayObject()

            val buffers = IntArray(3)
            GLES31.glGenBuffers(3, buffers, 0)
            stateBufferA = buffers[0]
            stateBufferB = buffers[1]
            spawnBuffer = buffers[2]

            val particleBytes = maxParticles * FLOATS_PER_PARTICLE * 4
            initStorageBuffer(stateBufferA, particleBytes)
            initStorageBuffer(stateBufferB, particleBytes)
            initStorageBuffer(spawnBuffer, particleBytes)
            clearStorageBuffer(stateBufferA, particleBytes)
            clearStorageBuffer(stateBufferB, particleBytes)
            clearStorageBuffer(spawnBuffer, particleBytes)

            GLES31.glUseProgram(computeProgram)
            uDeltaTime = GLES31.glGetUniformLocation(computeProgram, "uDeltaTime")
            uGravity = GLES31.glGetUniformLocation(computeProgram, "uGravity")
            uBurstFrameGrowthMultiplier = GLES31.glGetUniformLocation(computeProgram, "uBurstFrameGrowthMultiplier")
            uBurstSpeedCoefficient = GLES31.glGetUniformLocation(computeProgram, "uBurstSpeedCoefficient")
            uProjectileSpeed = GLES31.glGetUniformLocation(computeProgram, "uProjectileSpeed")
            uMapItemReturnSpeed = GLES31.glGetUniformLocation(computeProgram, "uMapItemReturnSpeed")
            uMapItemReturnMinTravelDist = GLES31.glGetUniformLocation(computeProgram, "uMapItemReturnMinTravelDist")
            uPlayerRect = GLES31.glGetUniformLocation(computeProgram, "uPlayerRect")
            uViewPortRect = GLES31.glGetUniformLocation(computeProgram, "uViewPortRect")
            uMaxParticles = GLES31.glGetUniformLocation(computeProgram, "uMaxParticles")

            GLES31.glUseProgram(renderProgram)
            uViewPort = GLES31.glGetUniformLocation(renderProgram, "uViewPort")
            uSurfaceSize = GLES31.glGetUniformLocation(renderProgram, "uSurfaceSize")
            uViewPortSize = GLES31.glGetUniformLocation(renderProgram, "uViewPortSize")
            uSizeScale = GLES31.glGetUniformLocation(renderProgram, "uSizeScale")
            rendererReady = true
            logger.info { "[GPU][Renderer] GPU particle renderer initialized" }
        } catch (t: Throwable) {
            rendererReady = false
            logger.error(t) { "[GPU][Renderer] Failed to initialize GPU renderer; disabling" }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES31.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!rendererReady) return
        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)

        val frame = frameProvider() ?: run {
            emptyFrameCounter++
            if (emptyFrameCounter % RENDERER_DIAGNOSTIC_LOG_EVERY_N_FRAMES == 0) {
                logger.info { "[GPU][Renderer] No frame payload available yet" }
            }
            if (!renderedDebugFirstFrame) {
                // One-time visual probe: if this magenta triangle is visible, composition is working.
                GLES31.glEnable(GLES31.GL_SCISSOR_TEST)
                GLES31.glScissor(0, 0, 24, 24)
                GLES31.glClearColor(1f, 0f, 1f, 0.45f)
                GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
                GLES31.glDisable(GLES31.GL_SCISSOR_TEST)
                GLES31.glClearColor(0f, 0f, 0f, 0f)
                renderedDebugFirstFrame = true
            }
            return
        }
        emptyFrameCounter = 0
        frameCounter++
        if (frameCounter % RENDERER_DIAGNOSTIC_LOG_EVERY_N_FRAMES == 0) {
            val (alive, dust, projectile, mapItemReturn) = countKinds(frame.sourceBuffer)
            logger.info {
                "[GPU][Renderer] incoming alive=$alive, dust=$dust, projectile=$projectile, mapItemReturn=$mapItemReturn, " +
                    "sizeMultiplier=${frame.sizeMultiplier}, dt=${frame.deltaTimeSeconds}"
            }
        }
        uploadSpawnBuffer(frame.sourceBuffer)

        val source = if (useStateAAsSource) stateBufferA else stateBufferB
        val target = if (useStateAAsSource) stateBufferB else stateBufferA

        GLES31.glUseProgram(computeProgram)
        GLES31.glUniform1f(uDeltaTime, frame.deltaTimeSeconds)
        GLES31.glUniform1f(uGravity, frame.gravity)
        GLES31.glUniform1f(uBurstFrameGrowthMultiplier, frame.burstFrameGrowthMultiplier)
        GLES31.glUniform1f(uBurstSpeedCoefficient, frame.burstSpeedCoefficient)
        GLES31.glUniform1f(uProjectileSpeed, frame.projectileSpeed)
        GLES31.glUniform1f(uMapItemReturnSpeed, frame.mapItemReturnSpeed)
        GLES31.glUniform1f(uMapItemReturnMinTravelDist, frame.mapItemReturnMinTravelDist)
        GLES31.glUniform4f(uViewPortRect, frame.viewPortX, frame.viewPortY, frame.viewPortWidth, frame.viewPortHeight)
        GLES31.glUniform1i(uMaxParticles, maxParticles)

        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, source)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, target)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, spawnBuffer)
        GLES31.glDispatchCompute(workgroupCount, 1, 1)
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)

        GLES31.glUseProgram(renderProgram)
        GLES31.glUniform2f(uViewPort, frame.viewPortX, frame.viewPortY)
        GLES31.glUniform2f(uSurfaceSize, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        GLES31.glUniform2f(uViewPortSize, frame.viewPortWidth, frame.viewPortHeight)
        GLES31.glUniform1f(uSizeScale, (frame.sizeMultiplier.coerceAtLeast(1).toFloat() / 14f).coerceAtLeast(0.1f))

        GLES30.glBindVertexArray(vao)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, target)
        GLES31.glDrawArraysInstanced(GLES31.GL_TRIANGLES, 0, 6, maxParticles)
        GLES30.glBindVertexArray(0)

        useStateAAsSource = !useStateAAsSource
        checkGlError()
    }

    private fun uploadSpawnBuffer(source: FloatArray) {
        if (source.isEmpty()) return
        val maxFloats = maxParticles * FLOATS_PER_PARTICLE
        val clampedFloatCount = source.size.coerceAtMost(maxFloats)
        val directBuffer = ByteBuffer
            .allocateDirect(clampedFloatCount * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        directBuffer.put(source, 0, clampedFloatCount)
        directBuffer.position(0)
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, spawnBuffer)
        GLES31.glBufferSubData(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            0,
            clampedFloatCount * 4,
            directBuffer
        )
    }

    private fun initStorageBuffer(bufferId: Int, sizeBytes: Int) {
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, bufferId)
        GLES31.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            sizeBytes,
            null,
            GLES31.GL_DYNAMIC_DRAW
        )
    }

    private fun clearStorageBuffer(bufferId: Int, sizeBytes: Int) {
        val zeroData = ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder())
        zeroData.position(0)
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, bufferId)
        GLES31.glBufferSubData(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            0,
            sizeBytes,
            zeroData
        )
    }

    private fun createComputeProgram(): Int {
        val shader = compileShader(GLES31.GL_COMPUTE_SHADER, computeShaderSource)
        val program = GLES31.glCreateProgram()
        GLES31.glAttachShader(program, shader)
        GLES31.glLinkProgram(program)
        val status = IntArray(1)
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES31.glGetProgramInfoLog(program)
            GLES31.glDeleteProgram(program)
            throw IllegalStateException("Compute program link failed: $info")
        }
        GLES31.glDeleteShader(shader)
        return program
    }

    private fun createRenderProgram(): Int {
        val vertex = compileShader(GLES31.GL_VERTEX_SHADER, vertexShaderSource)
        val fragment = compileShader(GLES31.GL_FRAGMENT_SHADER, fragmentShaderSource)
        val program = GLES31.glCreateProgram()
        GLES31.glAttachShader(program, vertex)
        GLES31.glAttachShader(program, fragment)
        GLES31.glLinkProgram(program)
        val status = IntArray(1)
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES31.glGetProgramInfoLog(program)
            GLES31.glDeleteProgram(program)
            throw IllegalStateException("Render program link failed: $info")
        }
        GLES31.glDeleteShader(vertex)
        GLES31.glDeleteShader(fragment)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES31.glCreateShader(type)
        GLES31.glShaderSource(shader, source)
        GLES31.glCompileShader(shader)
        val status = IntArray(1)
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES31.glGetShaderInfoLog(shader)
            GLES31.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed ($type): $info")
        }
        return shader
    }

    private fun createVertexArrayObject(): Int {
        val arrays = IntArray(1)
        GLES30.glGenVertexArrays(1, arrays, 0)
        return arrays[0]
    }

    private fun checkGlError() {
        var error = GLES20.glGetError()
        while (error != GLES20.GL_NO_ERROR) {
            logger.warn { "OpenGL error at onDrawFrame: 0x${error.toString(16)}" }
            error = GLES20.glGetError()
        }
    }

    private fun countKinds(buffer: FloatArray): KindCounts {
        if (buffer.isEmpty()) return KindCounts(0, 0, 0, 0)
        var alive = 0
        var dust = 0
        var projectile = 0
        var mapItemReturn = 0
        var base = 0
        while ((base + IDX_KIND) < buffer.size) {
            if (buffer[base + IDX_ALIVE] > 0.5f) {
                alive++
                when (buffer[base + IDX_KIND]) {
                    KIND_DUST -> dust++
                    KIND_PROJECTILE -> projectile++
                    KIND_MAP_ITEM_RETURN -> mapItemReturn++
                }
            }
            base += FLOATS_PER_PARTICLE
        }
        return KindCounts(alive, dust, projectile, mapItemReturn)
    }

    private fun supportsComputeShaders(glVersion: String): Boolean {
        val esPrefix = "OpenGL ES "
        val versionText = glVersion.substringAfter(esPrefix, "")
        if (versionText.isBlank()) return false
        val majorMinor = versionText.split(" ").firstOrNull().orEmpty().split(".")
        val major = majorMinor.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = majorMinor.getOrNull(1)?.toIntOrNull() ?: return false
        return major > 3 || (major == 3 && minor >= 1)
    }

    private data class KindCounts(
        val alive: Int,
        val dust: Int,
        val projectile: Int,
        val mapItemReturn: Int
    )
}
