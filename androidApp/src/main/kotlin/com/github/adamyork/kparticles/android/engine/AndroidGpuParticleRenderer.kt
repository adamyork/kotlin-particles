package com.github.adamyork.kparticles.android.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import com.github.adamyork.kparticles.android.engine.data.AndroidGpuParticleFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal class AndroidGpuParticleRenderer(
    private val maxParticles: Int,
    private val frameProvider: () -> AndroidGpuParticleFrame?,
    private val computeShaderSource: String,
    private val vertexShaderSource: String,
    private val fragmentShaderSource: String,
    private val mapItemTextureBytes: ByteArray,
    private val mapItemSpriteWidth: Int,
    private val mapItemSpriteHeight: Int
) : GLSurfaceView.Renderer {

    companion object {
        private const val FLOATS_PER_PARTICLE = 24
        private const val BYTES_PER_FLOAT = 4
        private const val COLLISION_SIGNAL_UINT_COUNT = 4
        private const val COLLISION_SIGNAL_BYTES = COLLISION_SIGNAL_UINT_COUNT * 4
        private const val RENDERER_DIAGNOSTIC_LOG_EVERY_N_FRAMES = 60
    }

    private val logger = KotlinLogging.logger {}

    private val workgroupCount = ceil(maxParticles.toDouble() / 64.0).toInt().coerceAtLeast(1)
    private var computeProgram = 0
    private var renderProgram = 0
    private var vao = 0
    private var unusedAttributeBuffer = 0

    private var stateBufferA = 0
    private var stateBufferB = 0
    private var spawnBuffer = 0
    private var collisionSignalBuffer = 0
    private var mapItemTexture = 0
    private var useStateAAsSource = true

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    private var uComputeDeltaTimeSeconds = -1
    private var uComputeGravity = -1
    private var uComputeTickRate = -1
    private var uComputeSimulationSpeed = -1
    private var uComputeGravityBoost = -1
    private var uComputeLifetimeDecay = -1
    private var uComputeViewportX = -1
    private var uComputeViewportY = -1
    private var uComputeViewportWidth = -1
    private var uComputeViewportHeight = -1
    private var uComputePlayerX = -1
    private var uComputePlayerY = -1
    private var uComputePlayerWidth = -1
    private var uComputePlayerHeight = -1
    private var uComputeMaxParticles = -1

    private var uRenderViewportX = -1
    private var uRenderViewportY = -1
    private var uRenderViewportWidth = -1
    private var uRenderViewportHeight = -1
    private var uRenderSizeScale = -1
    private var uRenderMapItemSpriteWidth = -1
    private var uRenderMapItemSpriteHeight = -1
    private var uRenderMapItemTexture = -1
    private var uRenderDustPass = -1

    private var frameCounter = 0
    private var emptyFrameCounter = 0
    private var rendererReady = false
    private var collisionResetBuffer: ByteBuffer? = null

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
        GLES31.glBlendEquation(GLES31.GL_FUNC_ADD)
        GLES31.glClearColor(0f, 0f, 0f, 0f)
        try {
            computeProgram = createComputeProgram()
            renderProgram = createRenderProgram()
            vao = createVertexArrayObject()
            val buffers = IntArray(4)
            GLES31.glGenBuffers(4, buffers, 0)
            stateBufferA = buffers[0]
            stateBufferB = buffers[1]
            spawnBuffer = buffers[2]
            collisionSignalBuffer = buffers[3]
            val particleBytes = maxParticles * FLOATS_PER_PARTICLE * BYTES_PER_FLOAT
            initStorageBuffer(stateBufferA, particleBytes)
            initStorageBuffer(stateBufferB, particleBytes)
            initStorageBuffer(spawnBuffer, particleBytes)
            initStorageBuffer(collisionSignalBuffer, COLLISION_SIGNAL_BYTES)
            clearStorageBuffer(stateBufferA, particleBytes)
            clearStorageBuffer(stateBufferB, particleBytes)
            clearStorageBuffer(spawnBuffer, particleBytes)
            clearStorageBuffer(collisionSignalBuffer, COLLISION_SIGNAL_BYTES)
            mapItemTexture = createMapItemTexture()
            GLES31.glUseProgram(computeProgram)
            uComputeDeltaTimeSeconds = GLES31.glGetUniformLocation(computeProgram, "uDeltaTimeSeconds")
            uComputeGravity = GLES31.glGetUniformLocation(computeProgram, "uGravity")
            uComputeTickRate = GLES31.glGetUniformLocation(computeProgram, "uTickRate")
            uComputeSimulationSpeed = GLES31.glGetUniformLocation(computeProgram, "uSimulationSpeed")
            uComputeGravityBoost = GLES31.glGetUniformLocation(computeProgram, "uGravityBoost")
            uComputeLifetimeDecay = GLES31.glGetUniformLocation(computeProgram, "uLifetimeDecay")
            uComputeViewportX = GLES31.glGetUniformLocation(computeProgram, "uViewportX")
            uComputeViewportY = GLES31.glGetUniformLocation(computeProgram, "uViewportY")
            uComputeViewportWidth = GLES31.glGetUniformLocation(computeProgram, "uViewportWidth")
            uComputeViewportHeight = GLES31.glGetUniformLocation(computeProgram, "uViewportHeight")
            uComputePlayerX = GLES31.glGetUniformLocation(computeProgram, "uPlayerX")
            uComputePlayerY = GLES31.glGetUniformLocation(computeProgram, "uPlayerY")
            uComputePlayerWidth = GLES31.glGetUniformLocation(computeProgram, "uPlayerWidth")
            uComputePlayerHeight = GLES31.glGetUniformLocation(computeProgram, "uPlayerHeight")
            uComputeMaxParticles = GLES31.glGetUniformLocation(computeProgram, "uMaxParticles")
            GLES31.glUseProgram(renderProgram)
            uRenderViewportX = GLES31.glGetUniformLocation(renderProgram, "uViewportX")
            uRenderViewportY = GLES31.glGetUniformLocation(renderProgram, "uViewportY")
            uRenderViewportWidth = GLES31.glGetUniformLocation(renderProgram, "uViewportWidth")
            uRenderViewportHeight = GLES31.glGetUniformLocation(renderProgram, "uViewportHeight")
            uRenderSizeScale = GLES31.glGetUniformLocation(renderProgram, "uSizeScale")
            uRenderMapItemSpriteWidth = GLES31.glGetUniformLocation(renderProgram, "uMapItemSpriteWidth")
            uRenderMapItemSpriteHeight = GLES31.glGetUniformLocation(renderProgram, "uMapItemSpriteHeight")
            uRenderMapItemTexture = GLES31.glGetUniformLocation(renderProgram, "uMapItemTexture")
            uRenderDustPass = GLES31.glGetUniformLocation(renderProgram, "uDustPass")
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
            return
        }
        emptyFrameCounter = 0
        frameCounter++
        uploadSpawnBuffer(frame.sourceBuffer)
        resetCollisionSignalBuffer()
        val source = if (useStateAAsSource) stateBufferA else stateBufferB
        val target = if (useStateAAsSource) stateBufferB else stateBufferA
        GLES31.glUseProgram(computeProgram)
        GLES31.glUniform1f(uComputeDeltaTimeSeconds, frame.deltaTimeSeconds)
        GLES31.glUniform1f(uComputeGravity, frame.gravity)
        GLES31.glUniform1f(uComputeTickRate, frame.tickRate)
        GLES31.glUniform1f(uComputeSimulationSpeed, frame.simulationSpeed)
        GLES31.glUniform1f(uComputeGravityBoost, frame.gravityBoost)
        GLES31.glUniform1f(uComputeLifetimeDecay, frame.lifetimeDecay)
        GLES31.glUniform1f(uComputeViewportX, frame.viewPortX)
        GLES31.glUniform1f(uComputeViewportY, frame.viewPortY)
        GLES31.glUniform1f(uComputeViewportWidth, frame.viewPortWidth)
        GLES31.glUniform1f(uComputeViewportHeight, frame.viewPortHeight)
        GLES31.glUniform1f(uComputePlayerX, 0f)
        GLES31.glUniform1f(uComputePlayerY, 0f)
        GLES31.glUniform1f(uComputePlayerWidth, 0f)
        GLES31.glUniform1f(uComputePlayerHeight, 0f)
        GLES31.glUniform1i(uComputeMaxParticles, maxParticles)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, source)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, target)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, spawnBuffer)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, collisionSignalBuffer)
        GLES31.glDispatchCompute(workgroupCount, 1, 1)
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
        readCollisionSignalBuffer()
        GLES31.glUseProgram(renderProgram)
        GLES31.glUniform1f(uRenderViewportX, frame.viewPortX)
        GLES31.glUniform1f(uRenderViewportY, frame.viewPortY)
        GLES31.glUniform1f(uRenderViewportWidth, frame.viewPortWidth)
        GLES31.glUniform1f(uRenderViewportHeight, frame.viewPortHeight)
        GLES31.glUniform1f(uRenderSizeScale, (frame.sizeMultiplier.coerceAtLeast(1).toFloat() / 14f).coerceAtLeast(0.1f))
        GLES31.glUniform1f(uRenderMapItemSpriteWidth, mapItemSpriteWidth.toFloat())
        GLES31.glUniform1f(uRenderMapItemSpriteHeight, mapItemSpriteHeight.toFloat())
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mapItemTexture)
        GLES31.glUniform1i(uRenderMapItemTexture, 0)
        GLES30.glBindVertexArray(vao)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, target)
        GLES31.glUniform1i(uRenderDustPass, 0)
        GLES31.glBlendFunc(GLES31.GL_SRC_ALPHA, GLES31.GL_ONE_MINUS_SRC_ALPHA)
        GLES31.glBlendEquation(GLES31.GL_FUNC_ADD)
        GLES31.glDrawArraysInstanced(GLES31.GL_TRIANGLES, 0, 6, maxParticles)
        GLES31.glUniform1i(uRenderDustPass, 1)
        GLES31.glBlendFunc(GLES31.GL_ONE, GLES31.GL_ONE)
        GLES31.glBlendEquation(GLES30.GL_MAX)
        GLES31.glDrawArraysInstanced(GLES31.GL_TRIANGLES, 0, 6, maxParticles)
        GLES31.glBlendEquation(GLES31.GL_FUNC_ADD)
        GLES30.glBindVertexArray(0)
        if (frameCounter % RENDERER_DIAGNOSTIC_LOG_EVERY_N_FRAMES == 0) {
            logger.info {
                "[GPU][Renderer] frame=$frameCounter activeParticleCount=? dt=${frame.deltaTimeSeconds} " +
                        "tickRate=${frame.tickRate} viewport=${frame.viewPortWidth}x${frame.viewPortHeight}"
            }
        }
        useStateAAsSource = !useStateAAsSource
        checkGlError()
    }

    private fun uploadSpawnBuffer(source: FloatArray) {
        if (source.isEmpty()) return
        val maxFloats = maxParticles * FLOATS_PER_PARTICLE
        val clampedFloatCount = source.size.coerceAtMost(maxFloats)
        val directBuffer = ByteBuffer
            .allocateDirect(clampedFloatCount * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        directBuffer.put(source, 0, clampedFloatCount)
        directBuffer.position(0)
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, spawnBuffer)
        GLES31.glBufferSubData(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            0,
            clampedFloatCount * BYTES_PER_FLOAT,
            directBuffer
        )
    }

    private fun resetCollisionSignalBuffer() {
        val resetBuffer = collisionResetBuffer ?: ByteBuffer
            .allocateDirect(COLLISION_SIGNAL_BYTES)
            .order(ByteOrder.nativeOrder())
            .also { collisionResetBuffer = it }
        resetBuffer.position(0)
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, collisionSignalBuffer)
        GLES31.glBufferSubData(GLES31.GL_SHADER_STORAGE_BUFFER, 0, COLLISION_SIGNAL_BYTES, resetBuffer)
    }

    private fun readCollisionSignalBuffer() {
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, collisionSignalBuffer)
        val mapped = GLES30.glMapBufferRange(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            0,
            COLLISION_SIGNAL_BYTES,
            GLES30.GL_MAP_READ_BIT
        ) as? ByteBuffer
        if (mapped != null) {
            mapped.order(ByteOrder.nativeOrder())
            val hitCount = mapped.getInt(0)
            if (hitCount > 0) {
                logger.info { "[GPU] Projectile collision detected with player, count=$hitCount" }
            }
        }
        GLES30.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER)
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

    private fun createMapItemTexture(): Int {
        val textureIds = IntArray(1)
        GLES31.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textureId)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
        val spriteBitmap = decodeMapItemSpriteBitmap()
        GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, spriteBitmap, 0)
        spriteBitmap.recycle()
        return textureId
    }

    private fun decodeMapItemSpriteBitmap(): Bitmap {
        if (mapItemTextureBytes.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                setPixel(0, 0, android.graphics.Color.WHITE)
            }
        }
        return BitmapFactory.decodeByteArray(mapItemTextureBytes, 0, mapItemTextureBytes.size)
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                setPixel(0, 0, android.graphics.Color.WHITE)
            }
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
        val arrayId = arrays[0]
        GLES30.glBindVertexArray(arrayId)
        val unusedBuffers = IntArray(1)
        GLES31.glGenBuffers(1, unusedBuffers, 0)
        unusedAttributeBuffer = unusedBuffers[0]
        val unusedData = ByteBuffer.allocateDirect(2 * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder())
        unusedData.position(0)
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, unusedAttributeBuffer)
        GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, 2 * BYTES_PER_FLOAT, unusedData, GLES31.GL_STATIC_DRAW)
        GLES31.glEnableVertexAttribArray(0)
        GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        return arrayId
    }

    private fun checkGlError() {
        var error = GLES20.glGetError()
        while (error != GLES20.GL_NO_ERROR) {
            logger.warn { "OpenGL error at onDrawFrame: 0x${error.toString(16)}" }
            error = GLES20.glGetError()
        }
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
}
