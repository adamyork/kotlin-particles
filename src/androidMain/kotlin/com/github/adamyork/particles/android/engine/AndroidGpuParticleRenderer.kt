package com.github.adamyork.particles.android.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLES31
import android.opengl.GLUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.github.adamyork.particles.android.engine.data.AndroidGpuParticleFrame
import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.ParticleEffectGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import com.github.adamyork.particles.engine.shader.GpuBlendMode
import com.github.adamyork.particles.engine.shader.GpuRenderPass
import com.github.adamyork.particles.engine.shader.GpuShaderComposer
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.engine.data.ParticleSpriteSheet

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
internal class AndroidGpuParticleRenderer(
    private val maxParticles: Int,
    private val gpuDataPacketWriter: GpuDataPacketWriter,
    private val computeShaderSource: String,
    private val vertexShaderSource: String,
    private val fragmentShaderSource: String,
    private val renderPasses: List<GpuRenderPass>,
    private val styleShaders: List<GpuStyleShader>
) {

    companion object {
        private const val WORKGROUP_SIZE = 64
        private const val FLOATS_PER_PARTICLE = ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE
        private const val BYTES_PER_FLOAT = 4
        private const val KINEMATICS_BYTES_PER_PARTICLE = 64
        private const val GRID_BUFFER_BYTES = 1048576
        private const val SCRATCH_EXTRA_UNSIGNED_INTEGERS = 1024
        private const val STATE_BUFFER_BINDING = 0
        private const val SPAWN_BUFFER_BINDING = 1
        private const val KINEMATICS_BUFFER_BINDING = 2
        private const val GRID_BUFFER_BINDING = 3
        private const val SCRATCH_BUFFER_BINDING = 4
        private const val SIMULATION_PACKET_BINDING = 0
        private const val RENDER_PACKET_BINDING = 1
        private const val MINIMUM_COMPUTE_STORAGE_BLOCKS = 4
        private const val MINIMUM_VERTEX_STORAGE_BLOCKS = 1
        private const val MINIMUM_STORAGE_BUFFER_BINDINGS = 5
        private const val RENDERER_DIAGNOSTIC_LOG_EVERY_N_FRAMES = 300
        private val SIMULATION_STAGE_NAMES = listOf(
            "SPAWN_INTEGRATE",
            "GRID_CLEAR",
            "GRID_COUNT",
            "GRID_SCAN",
            "GRID_SCATTER",
            "SELECT_GRAVITY_SOURCES",
            "GRAVITY",
            "ACCRETION_OWNER",
            "ACCRETION_APPLY",
            "CONTACT_BEGIN",
            "COUNT_CONTACTS_FROM_A",
            "SOLVE_CONTACTS_A_TO_B",
            "COUNT_CONTACTS_FROM_B",
            "SOLVE_CONTACTS_B_TO_A",
            "CORRECT_POSITIONS_A_TO_B",
            "CORRECT_POSITIONS_B_TO_A",
            "CONTACT_END",
            "FREE_SLOTS",
            "SPLIT",
            "WALL"
        )
        private val SINGLE_WORKGROUP_STAGE_NAMES = setOf("GRID_SCAN", "SELECT_GRAVITY_SOURCES")
    }

    private val logger = KotlinLogging.logger {}

    private val fullWorkgroupCount = ceil(maxParticles.toDouble() / WORKGROUP_SIZE.toDouble()).toInt().coerceAtLeast(1)
    private val simulationPrograms = mutableListOf<Int>()
    private val simulationWorkgroupCounts = mutableListOf<Int>()
    private var renderProgram = 0
    private var vertexArrayObject = 0

    private var stateBuffer = 0
    private var spawnBuffer = 0
    private var kinematicsBuffer = 0
    private var gridBuffer = 0
    private var scratchBuffer = 0
    private var simulationDataPacketBuffer = 0
    private var renderDataPacketBuffer = 0
    private var passTextures: List<Int> = emptyList()

    private var uActiveStyle = -1
    private var uInstanceOffset = -1

    private val simulationDataPacket = FloatArray(GpuDataPacketWriter.SIMULATION_PACKET_FLOATS)
    private val renderDataPacket = FloatArray(GpuDataPacketWriter.RENDER_PACKET_FLOATS)
    private var uploadFloatBuffer: FloatBuffer? = null
    private var packetFloatBuffer: FloatBuffer? = null

    private var frameCounter = 0
    private var rendererReady = false

    fun onSurfaceCreated() {
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
        if (!hasRequiredStorageBlockLimits()) {
            rendererReady = false
            return
        }
        GLES31.glDisable(GLES31.GL_DEPTH_TEST)
        GLES31.glEnable(GLES31.GL_BLEND)
        GLES31.glClearColor(0f, 0f, 0f, 0f)
        try {
            val compileStartNanos = System.nanoTime()
            createSimulationPrograms()
            renderProgram = createRenderProgram()
            logger.info {
                "[GPU][Renderer] compiled ${simulationPrograms.size} simulation stages in " +
                        "${(System.nanoTime() - compileStartNanos) / 1_000_000}ms"
            }
            val vertexArrays = IntArray(1)
            GLES30.glGenVertexArrays(1, vertexArrays, 0)
            vertexArrayObject = vertexArrays[0]
            val buffers = IntArray(7)
            GLES31.glGenBuffers(buffers.size, buffers, 0)
            stateBuffer = buffers[0]
            spawnBuffer = buffers[1]
            kinematicsBuffer = buffers[2]
            gridBuffer = buffers[3]
            scratchBuffer = buffers[4]
            simulationDataPacketBuffer = buffers[5]
            renderDataPacketBuffer = buffers[6]
            val particleBytes = maxParticles * FLOATS_PER_PARTICLE * BYTES_PER_FLOAT
            val scratchBytes = (maxParticles * 4 + SCRATCH_EXTRA_UNSIGNED_INTEGERS) * BYTES_PER_FLOAT
            initializeZeroedBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, stateBuffer, particleBytes)
            initializeZeroedBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, spawnBuffer, particleBytes)
            initializeZeroedBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, kinematicsBuffer, maxParticles * KINEMATICS_BYTES_PER_PARTICLE)
            initializeZeroedBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, gridBuffer, GRID_BUFFER_BYTES)
            initializeZeroedBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, scratchBuffer, scratchBytes)
            initializeZeroedBuffer(
                GLES31.GL_UNIFORM_BUFFER,
                simulationDataPacketBuffer,
                simulationDataPacket.size * BYTES_PER_FLOAT
            )
            initializeZeroedBuffer(
                GLES31.GL_UNIFORM_BUFFER,
                renderDataPacketBuffer,
                renderDataPacket.size * BYTES_PER_FLOAT
            )
            uploadFloatBuffer = createFloatBuffer(maxParticles * FLOATS_PER_PARTICLE)
            packetFloatBuffer = createFloatBuffer(
                maxOf(simulationDataPacket.size, renderDataPacket.size)
            )
            val texturesBySheet = HashMap<ParticleSpriteSheet, Int>()
            var solidTexture = 0
            passTextures = renderPasses.map { renderPass ->
                val spriteSheet = renderPass.spriteSheet
                if (spriteSheet == null || spriteSheet.encodedBytes.isEmpty()) {
                    if (solidTexture == 0) solidTexture = createTexture(null)
                    solidTexture
                } else {
                    texturesBySheet.getOrPut(spriteSheet) { createTexture(spriteSheet.encodedBytes) }
                }
            }
            GLES31.glUseProgram(renderProgram)
            uActiveStyle = GLES31.glGetUniformLocation(renderProgram, "uActiveStyle")
            uInstanceOffset = GLES31.glGetUniformLocation(renderProgram, "uInstanceOffset")
            rendererReady = true
            logger.info { "[GPU][Renderer] GPU particle renderer initialized with $maxParticles slots" }
        } catch (t: Throwable) {
            rendererReady = false
            logger.error(t) { "[GPU][Renderer] Failed to initialize GPU renderer; disabling" }
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES31.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    fun renderFrame(frame: AndroidGpuParticleFrame) {
        if (!rendererReady) return
        frameCounter++
        uploadSpawnRanges(frame.sourceBuffer, frame.dirtySlotRanges)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, STATE_BUFFER_BINDING, stateBuffer)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, SPAWN_BUFFER_BINDING, spawnBuffer)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, KINEMATICS_BUFFER_BINDING, kinematicsBuffer)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, GRID_BUFFER_BINDING, gridBuffer)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, SCRATCH_BUFFER_BINDING, scratchBuffer)
        GLES31.glBindBufferBase(GLES31.GL_UNIFORM_BUFFER, SIMULATION_PACKET_BINDING, simulationDataPacketBuffer)
        for (subframeIndex in 0 until frame.subframeCount) {
            gpuDataPacketWriter.writeSimulationDataPacket(
                simulationDataPacket,
                frame.deltaTime,
                frame.worldTime + subframeIndex * frame.deltaTime,
                frame.spriteTicksPerFrame,
                maxParticles,
                frame.viewPort,
                subframeIndex
            )
            uploadDataPacket(simulationDataPacketBuffer, simulationDataPacket)
            for (stageIndex in simulationPrograms.indices) {
                GLES31.glUseProgram(simulationPrograms[stageIndex])
                GLES31.glDispatchCompute(simulationWorkgroupCounts[stageIndex], 1, 1)
                GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
            }
        }
        gpuDataPacketWriter.writeRenderDataPacket(
            renderDataPacket,
            frame.viewPort,
            frame.viewPort.width,
            frame.viewPort.height,
            1f,
            frame.spriteTicksPerFrame
        )
        uploadDataPacket(renderDataPacketBuffer, renderDataPacket)
        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
        GLES31.glUseProgram(renderProgram)
        GLES31.glBindBufferBase(GLES31.GL_UNIFORM_BUFFER, RENDER_PACKET_BINDING, renderDataPacketBuffer)
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
        GLES30.glBindVertexArray(vertexArrayObject)
        for (passIndex in renderPasses.indices) {
            val renderPass = renderPasses[passIndex]
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, passTextures[passIndex])
            GLES31.glUniform1i(uActiveStyle, renderPass.renderStyle.code.toInt())
            GLES31.glUniform1i(uInstanceOffset, renderPass.firstInstance)
            if (renderPass.blendMode == GpuBlendMode.MAX) {
                GLES31.glBlendFunc(GLES31.GL_ONE, GLES31.GL_ONE)
                GLES31.glBlendEquation(GLES30.GL_MAX)
            } else {
                GLES31.glBlendFuncSeparate(
                    GLES31.GL_SRC_ALPHA,
                    GLES31.GL_ONE_MINUS_SRC_ALPHA,
                    GLES31.GL_ONE,
                    GLES31.GL_ONE_MINUS_SRC_ALPHA
                )
                GLES31.glBlendEquation(GLES31.GL_FUNC_ADD)
            }
            GLES31.glDrawArraysInstanced(GLES31.GL_TRIANGLES, 0, 6, renderPass.instanceCount)
        }
        GLES31.glBlendEquation(GLES31.GL_FUNC_ADD)
        GLES30.glBindVertexArray(0)
        if (frameCounter % RENDERER_DIAGNOSTIC_LOG_EVERY_N_FRAMES == 0) {
            logger.info {
                "[GPU][Renderer] frame=$frameCounter dt=${frame.deltaTime} " +
                        "viewport=${frame.viewPort.width}x${frame.viewPort.height}"
            }
        }
        checkGlError()
    }

    private fun uploadSpawnRanges(source: FloatArray, dirtySlotRanges: List<IntRange>) {
        val stagingBuffer = uploadFloatBuffer ?: return
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, spawnBuffer)
        for (slotRange in dirtySlotRanges) {
            val startSlot = slotRange.first.coerceIn(0, maxParticles)
            val endExclusiveSlot = (slotRange.last + 1).coerceIn(startSlot, maxParticles)
            val slotCount = endExclusiveSlot - startSlot
            if (slotCount <= 0) continue
            val startFloat = startSlot * FLOATS_PER_PARTICLE
            val floatCount = slotCount * FLOATS_PER_PARTICLE
            if (startFloat + floatCount > source.size) continue
            stagingBuffer.clear()
            stagingBuffer.put(source, startFloat, floatCount)
            stagingBuffer.position(0)
            GLES31.glBufferSubData(
                GLES31.GL_SHADER_STORAGE_BUFFER,
                startFloat * BYTES_PER_FLOAT,
                floatCount * BYTES_PER_FLOAT,
                stagingBuffer
            )
        }
    }

    private fun uploadDataPacket(bufferId: Int, data: FloatArray) {
        val stagingBuffer = packetFloatBuffer ?: return
        stagingBuffer.clear()
        stagingBuffer.put(data, 0, data.size)
        stagingBuffer.position(0)
        GLES31.glBindBuffer(GLES31.GL_UNIFORM_BUFFER, bufferId)
        GLES31.glBufferSubData(GLES31.GL_UNIFORM_BUFFER, 0, data.size * BYTES_PER_FLOAT, stagingBuffer)
    }

    private fun createFloatBuffer(floatCount: Int): FloatBuffer = ByteBuffer
        .allocateDirect(floatCount * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private fun initializeZeroedBuffer(target: Int, bufferId: Int, sizeBytes: Int) {
        val zeroData = ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder())
        zeroData.position(0)
        GLES31.glBindBuffer(target, bufferId)
        GLES31.glBufferData(target, sizeBytes, zeroData, GLES31.GL_DYNAMIC_DRAW)
    }

    private fun createTexture(encodedBytes: ByteArray?): Int {
        val textureIds = IntArray(1)
        GLES31.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textureId)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
        val spriteBitmap = decodeSpriteBitmap(encodedBytes)
        GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, spriteBitmap, 0)
        spriteBitmap.recycle()
        return textureId
    }

    private fun decodeSpriteBitmap(encodedBytes: ByteArray?): Bitmap {
        if (encodedBytes == null || encodedBytes.isEmpty()) {
            return createSolidBitmap()
        }
        val decodeOptions = BitmapFactory.Options().apply { inPremultiplied = false }
        return BitmapFactory.decodeByteArray(encodedBytes, 0, encodedBytes.size, decodeOptions)
            ?: createSolidBitmap()
    }

    private fun createSolidBitmap(): Bitmap = createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
        this[0, 0] = Color.WHITE
    }

    private fun createSimulationPrograms() {
        simulationPrograms.clear()
        simulationWorkgroupCounts.clear()
        for (stageName in SIMULATION_STAGE_NAMES) {
            val stageSource = withStageDefinition(computeShaderSource, stageName)
            val shader = compileShader(GLES31.GL_COMPUTE_SHADER, stageSource, stageName)
            val program = linkProgram(listOf(shader), "Compute stage $stageName")
            simulationPrograms += program
            simulationWorkgroupCounts += if (stageName in SINGLE_WORKGROUP_STAGE_NAMES) 1 else fullWorkgroupCount
        }
    }

    private fun withStageDefinition(source: String, stageName: String): String {
        val versionLineEnd = source.indexOf('\n')
        check(versionLineEnd > 0) { "Compute shader source is missing its version line" }
        return source.substring(0, versionLineEnd + 1) +
                "#define STAGE STAGE_$stageName\n" +
                source.substring(versionLineEnd + 1)
    }

    private fun createRenderProgram(): Int {
        val vertex = compileShader(GLES31.GL_VERTEX_SHADER, vertexShaderSource, "vertex")
        val fragment = compileShader(GLES31.GL_FRAGMENT_SHADER, fragmentShaderSource, "fragment")
        return linkProgram(listOf(vertex, fragment), "Render program")
    }

    private fun linkProgram(shaders: List<Int>, label: String): Int {
        val program = GLES31.glCreateProgram()
        for (shader in shaders) {
            GLES31.glAttachShader(program, shader)
        }
        GLES31.glLinkProgram(program)
        val status = IntArray(1)
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES31.glGetProgramInfoLog(program)
            GLES31.glDeleteProgram(program)
            throw IllegalStateException("$label link failed: $info")
        }
        for (shader in shaders) {
            GLES31.glDeleteShader(shader)
        }
        return program
    }

    private fun compileShader(type: Int, source: String, label: String): Int {
        val shader = GLES31.glCreateShader(type)
        GLES31.glShaderSource(shader, source)
        GLES31.glCompileShader(shader)
        val status = IntArray(1)
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES31.glGetShaderInfoLog(shader)
            GLES31.glDeleteShader(shader)
            val annotatedInfo = GpuShaderComposer.annotateCompilationReport(source, styleShaders, info)
            throw IllegalStateException("Shader compile failed ($label): $annotatedInfo")
        }
        return shader
    }

    private fun hasRequiredStorageBlockLimits(): Boolean {
        val computeStorageBlocks = IntArray(1)
        val vertexStorageBlocks = IntArray(1)
        val storageBufferBindings = IntArray(1)
        GLES31.glGetIntegerv(GLES31.GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS, computeStorageBlocks, 0)
        GLES31.glGetIntegerv(GLES31.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS, vertexStorageBlocks, 0)
        GLES31.glGetIntegerv(GLES31.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS, storageBufferBindings, 0)
        logger.info {
            "[GPU][Renderer] maxComputeStorageBlocks=${computeStorageBlocks[0]}, " +
                    "maxVertexStorageBlocks=${vertexStorageBlocks[0]}, " +
                    "maxStorageBufferBindings=${storageBufferBindings[0]}"
        }
        if (computeStorageBlocks[0] < MINIMUM_COMPUTE_STORAGE_BLOCKS ||
            vertexStorageBlocks[0] < MINIMUM_VERTEX_STORAGE_BLOCKS ||
            storageBufferBindings[0] < MINIMUM_STORAGE_BUFFER_BINDINGS
        ) {
            logger.error {
                "[GPU][Renderer] insufficient shader storage block limits; GPU particle renderer is disabled"
            }
            return false
        }
        return true
    }

    private fun checkGlError() {
        var error = GLES20.glGetError()
        while (error != GLES20.GL_NO_ERROR) {
            logger.warn { "OpenGL error at renderFrame: 0x${error.toString(16)}" }
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
