package com.github.adamyork.particles.wasm.engine

import com.github.adamyork.particles.common.data.ParticleViewPort
import com.github.adamyork.particles.engine.GpuDataPacketWriter
import com.github.adamyork.particles.engine.ParticleEffectGenerator
import com.github.adamyork.particles.wasm.common.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.toInt8Array
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.JsNumber
import kotlin.js.toInt
import kotlin.math.ceil
import kotlin.math.max
import com.github.adamyork.particles.engine.shader.GpuBlendMode
import com.github.adamyork.particles.engine.shader.GpuRenderPass
import com.github.adamyork.particles.engine.shader.GpuShaderComposer
import com.github.adamyork.particles.engine.shader.GpuStyleShader
import com.github.adamyork.particles.engine.data.ParticleSpriteSheet

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@OptIn(ExperimentalWasmJsInterop::class)
public class WasmJsGpuParticleRenderer(
    private val gpuDataPacketWriter: GpuDataPacketWriter
) {

    public companion object {
        private const val WORKGROUP_SIZE = 64
        private const val FLOATS_PER_PARTICLE = ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE
        private const val BYTES_PER_FLOAT = 4
        private const val KINEMATICS_BYTES_PER_PARTICLE = 64
        private const val GRID_BUFFER_BYTES = 1048576
        private const val SCRATCH_EXTRA_UNSIGNED_INTEGERS = 1024
        private const val VERTEX_ENTRY_POINT = "vertexMain"
        private const val FRAGMENT_ENTRY_POINT = "fragmentMain"
        private const val SHADER_ERROR_MARKER = "[error]"
        private val SIMULATION_STAGE_ENTRY_POINTS = listOf(
            "spawnIntegrateMain",
            "gridClearMain",
            "gridCountMain",
            "gridScanMain",
            "gridScatterMain",
            "selectGravitySourcesMain",
            "gravityMain",
            "accretionOwnerMain",
            "accretionApplyMain",
            "contactBeginMain",
            "countContactsFromAMain",
            "solveContactsAToBMain",
            "countContactsFromBMain",
            "solveContactsBToAMain",
            "correctPositionsAToBMain",
            "correctPositionsBToAMain",
            "contactEndMain",
            "freeSlotsMain",
            "splitMain",
            "wallMain"
        )
        private val SINGLE_WORKGROUP_ENTRY_POINTS = setOf("gridScanMain", "selectGravitySourcesMain")
    }

    private val logger = KotlinLogging.logger {}

    private var canvas: HTMLCanvasElement? = null
    private var maxParticles: Int = 0
    private var initialized: Boolean = false

    private var device: GPUDevice? = null
    private var context: GPUCanvasContext? = null
    private var queue: GPUQueue? = null
    private var presentationFormat: String = ""
    private var computeWorkgroupCount: Int = 1
    private var lastConfiguredCanvasWidth: Int = -1
    private var lastConfiguredCanvasHeight: Int = -1

    private var stateBuffer: GPUBuffer? = null
    private var spawnBuffer: GPUBuffer? = null
    private var readbackBuffer: GPUBuffer? = null
    private var readbackInFlight: Boolean = false
    private var lastSampledLiveParticleCount: Int = 0
    private var simulationDataPacketBuffer: GPUBuffer? = null
    private var renderDataPacketBuffer: GPUBuffer? = null
    private var mapItemSampler: GPUSampler? = null
    private var lastSpriteTicksPerFrame: Double = 1.0

    private val simulationPipelines = mutableListOf<GPUComputePipeline>()
    private val simulationWorkgroupCounts = mutableListOf<Int>()
    private val renderPassPipelines = ArrayList<RenderPassPipeline>()

    private var simulationBindGroup: GPUBindGroup? = null

    private class RenderPassPipeline(
        val pipeline: GPURenderPipeline,
        val bindGroup: GPUBindGroup,
        val firstInstance: Int,
        val instanceCount: Int
    )

    private val simulationDataPacket = FloatArray(GpuDataPacketWriter.SIMULATION_PACKET_FLOATS)
    private val renderDataPacket = FloatArray(GpuDataPacketWriter.RENDER_PACKET_FLOATS)
    private var uploadScratchBuffer: ArrayBuffer? = null
    private var uploadScratchView: DataView? = null

    public suspend fun initialize(
        maxParticleCapacity: Int,
        particlesShaderSource: String,
        overlayCanvas: HTMLCanvasElement,
        renderPasses: List<GpuRenderPass>,
        styleShaders: List<GpuStyleShader>
    ): Boolean {
        if (initialized) return true
        if (!hasWebGpuSupport()) {
            logger.error { "WebGPU is unavailable in this browser/runtime" }
            return false
        }
        if (particlesShaderSource.isBlank()) {
            logger.error { "particles.wgsl source is empty" }
            return false
        }
        canvas = overlayCanvas
        maxParticles = maxParticleCapacity.coerceAtLeast(1)
        val gpu = getNavigatorGpu()
        val adapter = requestGpuAdapter(gpu).await() ?: run {
            logger.error { "WebGPU adapter request returned null" }
            return false
        }
        val gpuDevice = requestGpuDevice(adapter).await()
        installGpuErrorLogging(gpuDevice)
        val webGpuContext = getWebGpuContext(overlayCanvas) ?: run {
            logger.error { "Unable to acquire webgpu canvas context" }
            return false
        }
        val preferredFormat = getPreferredCanvasFormat(gpu)
        configureWebGpuContext(webGpuContext, gpuDevice, preferredFormat)
        val particleBufferSize = maxParticles * FLOATS_PER_PARTICLE * BYTES_PER_FLOAT
        computeWorkgroupCount = ceil(maxParticles.toDouble() / WORKGROUP_SIZE.toDouble()).toInt().coerceAtLeast(1)
        val createdState = createStorageBuffer(gpuDevice, particleBufferSize)
        val createdSpawn = createStorageBuffer(gpuDevice, particleBufferSize)
        val createdSimulationDataPacketBuffer = createDataPacketBuffer(gpuDevice, simulationDataPacket.size * BYTES_PER_FLOAT)
        val createdKinematics = createStorageBuffer(gpuDevice, maxParticles * KINEMATICS_BYTES_PER_PARTICLE)
        val createdGrid = createStorageBuffer(gpuDevice, GRID_BUFFER_BYTES)
        val createdScratch = createStorageBuffer(gpuDevice, (maxParticles * 4 + SCRATCH_EXTRA_UNSIGNED_INTEGERS) * BYTES_PER_FLOAT)
        val createdRenderDataPacketBuffer = createDataPacketBuffer(gpuDevice, renderDataPacket.size * BYTES_PER_FLOAT)
        val createdMapItemSampler = createLinearSampler(gpuDevice)
        val deviceQueue = getGpuQueue(gpuDevice)
        val solidTextureView = createSolidTextureView(gpuDevice, deviceQueue, 255, 255, 255, 255)
        val textureViewsBySheet = HashMap<ParticleSpriteSheet, GPUTextureView>()
        for (renderPass in renderPasses) {
            val spriteSheet = renderPass.spriteSheet ?: continue
            if (textureViewsBySheet.containsKey(spriteSheet) || spriteSheet.encodedBytes.isEmpty()) continue
            try {
                val textureResult = createTextureViewFromEncodedBytes(
                    device = gpuDevice,
                    queue = deviceQueue,
                    bytes = spriteSheet.encodedBytes.toInt8Array(),
                    firstCellWidth = 0,
                    firstCellHeight = 0
                ).await()
                textureViewsBySheet[spriteSheet] = textureResult.view
            } catch (throwable: Throwable) {
                currentCoroutineContext().ensureActive()
                logger.error(throwable) { "Failed to create the texture for render style ${renderPass.renderStyle.id}" }
                return false
            }
        }
        val shaderModule = createShaderModule(gpuDevice, particlesShaderSource)
        val compilationReport = getShaderCompilationReport(shaderModule).await().toString()
        if (compilationReport.isNotBlank()) {
            logger.error {
                "particles.wgsl compilation report:\n" +
                        GpuShaderComposer.annotateCompilationReport(particlesShaderSource, styleShaders, compilationReport)
            }
        }
        if (compilationReport.contains(SHADER_ERROR_MARKER)) {
            return false
        }
        val simulationLayout = createSimulationBindGroupLayout(gpuDevice)
        val simulationPipelineLayout = createPipelineLayout(gpuDevice, simulationLayout)
        val createdSimulationPipelines = SIMULATION_STAGE_ENTRY_POINTS.map { entryPoint ->
            createComputePipelineWithLayout(gpuDevice, shaderModule, entryPoint, simulationPipelineLayout)
        }
        val renderBindGroupLayout = createRenderBindGroupLayout(gpuDevice)
        val renderPipelineLayout = createPipelineLayout(gpuDevice, renderBindGroupLayout)
        val createdRenderPassPipelines = renderPasses.map { renderPass ->
            RenderPassPipeline(
                createStyleRenderPipeline(
                    gpuDevice,
                    shaderModule,
                    renderPipelineLayout,
                    preferredFormat,
                    VERTEX_ENTRY_POINT,
                    FRAGMENT_ENTRY_POINT,
                    renderPass.renderStyle.code.toDouble(),
                    renderPass.blendMode == GpuBlendMode.MAX
                ),
                createRenderBindGroupWithLayout(
                    gpuDevice,
                    renderBindGroupLayout,
                    createdState,
                    createdRenderDataPacketBuffer,
                    createdMapItemSampler,
                    textureViewsBySheet[renderPass.spriteSheet] ?: solidTextureView
                ),
                renderPass.firstInstance,
                renderPass.instanceCount
            )
        }
        val createdSimulationBindGroup = createSimulationBindGroup(
            gpuDevice,
            simulationLayout,
            createdState,
            createdSpawn,
            createdSimulationDataPacketBuffer,
            createdKinematics,
            createdGrid,
            createdScratch
        )
        device = gpuDevice
        context = webGpuContext
        queue = deviceQueue
        presentationFormat = preferredFormat
        stateBuffer = createdState
        spawnBuffer = createdSpawn
        simulationDataPacketBuffer = createdSimulationDataPacketBuffer
        renderDataPacketBuffer = createdRenderDataPacketBuffer
        mapItemSampler = createdMapItemSampler
        simulationPipelines.clear()
        simulationPipelines += createdSimulationPipelines
        simulationWorkgroupCounts.clear()
        simulationWorkgroupCounts += SIMULATION_STAGE_ENTRY_POINTS.map { entryPoint ->
            if (entryPoint in SINGLE_WORKGROUP_ENTRY_POINTS) 1 else computeWorkgroupCount
        }
        renderPassPipelines.clear()
        renderPassPipelines += createdRenderPassPipelines
        simulationBindGroup = createdSimulationBindGroup
        initialized = true
        logger.info { "WebGPU particle renderer initialized with $maxParticles slots" }
        return true
    }

    public suspend fun sampleLiveParticleCount(): Int {
        val gpuDevice = device ?: return lastSampledLiveParticleCount
        val gpuQueue = queue ?: return lastSampledLiveParticleCount
        val state = stateBuffer ?: return lastSampledLiveParticleCount
        if (readbackInFlight) return lastSampledLiveParticleCount
        readbackInFlight = true
        try {
            val particleBufferSize = maxParticles * FLOATS_PER_PARTICLE * BYTES_PER_FLOAT
            val stagingBuffer = readbackBuffer ?: createReadbackBuffer(gpuDevice, particleBufferSize).also { createdBuffer -> readbackBuffer = createdBuffer }
            lastSampledLiveParticleCount = readLiveParticleCount(
                gpuDevice,
                gpuQueue,
                state,
                stagingBuffer,
                particleBufferSize,
                FLOATS_PER_PARTICLE
            ).await<JsNumber>().toInt()
        } finally {
            readbackInFlight = false
        }
        return lastSampledLiveParticleCount
    }

    public fun updateGpuParticleBuffer(
        sourceBuffer: FloatArray,
        dirtySlotRanges: List<IntRange>,
        subframeCount: Int,
        deltaTime: Double,
        worldTime: Double,
        spriteTicksPerFrame: Double,
        viewPort: ParticleViewPort
    ) {
        check(initialized) { "WebGPU renderer must be initialized before updateGpuParticleBuffer" }
        val gpuDevice = device ?: return
        val gpuQueue = queue ?: return
        val spawn = spawnBuffer ?: return
        val activeSimulationBuffer = simulationDataPacketBuffer ?: return
        val bindGroup = simulationBindGroup ?: return
        for (slotRange in dirtySlotRanges) {
            val clampedStartSlot = slotRange.first.coerceIn(0, maxParticles)
            val clampedEndExclusive = (slotRange.last + 1).coerceIn(clampedStartSlot, maxParticles)
            val slotCount = clampedEndExclusive - clampedStartSlot
            if (slotCount <= 0) continue
            val rangeStartFloat = clampedStartSlot * FLOATS_PER_PARTICLE
            val rangeFloatCount = slotCount * FLOATS_PER_PARTICLE
            writeFloatArrayBufferRange(
                queue = gpuQueue,
                targetBuffer = spawn,
                data = sourceBuffer,
                sourceStartFloat = rangeStartFloat,
                floatCount = rangeFloatCount,
                targetOffsetBytes = rangeStartFloat * BYTES_PER_FLOAT
            )
        }
        lastSpriteTicksPerFrame = spriteTicksPerFrame
        for (subframeIndex in 0 until subframeCount) {
            gpuDataPacketWriter.writeSimulationDataPacket(
                simulationDataPacket,
                deltaTime,
                worldTime + subframeIndex * deltaTime,
                spriteTicksPerFrame,
                maxParticles,
                viewPort,
                subframeIndex
            )
            writeFloatArrayBuffer(gpuQueue, activeSimulationBuffer, simulationDataPacket)
            val encoder: GPUCommandEncoder = createCommandEncoder(gpuDevice)
            val computePass: GPUComputePassEncoder = beginComputePass(encoder)
            setComputeBindGroup(computePass, bindGroup)
            for (stageIndex in simulationPipelines.indices) {
                setComputePipeline(computePass, simulationPipelines[stageIndex])
                dispatchCompute(computePass, simulationWorkgroupCounts[stageIndex])
            }
            endComputePass(computePass)
            val commandBuffer: GPUCommandBuffer = finishCommandEncoder(encoder)
            submitCommandBuffer(gpuQueue, commandBuffer)
        }
    }

    public fun draw(viewPort: ParticleViewPort) {
        check(initialized) { "WebGPU renderer must be initialized before draw" }
        val gpuDevice = device ?: return
        val webGpuContext = context ?: return
        val gpuQueue = queue ?: return
        val targetCanvas = canvas ?: return
        if (targetCanvas.width <= 0 || targetCanvas.height <= 0) return
        if (targetCanvas.width != lastConfiguredCanvasWidth || targetCanvas.height != lastConfiguredCanvasHeight) {
            configureWebGpuContext(webGpuContext, gpuDevice, presentationFormat)
            lastConfiguredCanvasWidth = targetCanvas.width
            lastConfiguredCanvasHeight = targetCanvas.height
            logger.info {
                "gpu particle renderer (re)configured: canvasBackingBuffer=${targetCanvas.width}x${targetCanvas.height} " +
                        "viewPort=(${viewPort.x},${viewPort.y},${viewPort.width}x${viewPort.height}) " +
                        "renderScale=${getRenderScale()}"
            }
        }
        gpuDataPacketWriter.writeRenderDataPacket(
            renderDataPacket,
            viewPort,
            targetCanvas.width,
            targetCanvas.height,
            getRenderScale(),
            lastSpriteTicksPerFrame
        )
        val activeRenderBuffer = renderDataPacketBuffer ?: return
        writeFloatArrayBuffer(gpuQueue, activeRenderBuffer, renderDataPacket)
        val textureView: GPUTextureView = getCurrentTextureView(webGpuContext)
        val encoder: GPUCommandEncoder = createCommandEncoder(gpuDevice)
        val renderPass: GPURenderPassEncoder = beginRenderPass(encoder, textureView)
        for (passPipeline in renderPassPipelines) {
            setRenderPipeline(renderPass, passPipeline.pipeline)
            setRenderBindGroup(renderPass, passPipeline.bindGroup)
            drawRenderPassInstanced(renderPass, 6, passPipeline.instanceCount, passPipeline.firstInstance)
        }
        endRenderPass(renderPass)
        val commandBuffer: GPUCommandBuffer = finishCommandEncoder(encoder)
        submitCommandBuffer(gpuQueue, commandBuffer)
    }

    private fun writeFloatArrayBuffer(queue: GPUQueue, targetBuffer: GPUBuffer, data: FloatArray) {
        writeFloatArrayBufferRange(
            queue = queue,
            targetBuffer = targetBuffer,
            data = data,
            sourceStartFloat = 0,
            floatCount = data.size,
            targetOffsetBytes = 0
        )
    }

    private fun writeFloatArrayBufferRange(
        queue: GPUQueue,
        targetBuffer: GPUBuffer,
        data: FloatArray,
        sourceStartFloat: Int,
        floatCount: Int,
        targetOffsetBytes: Int
    ) {
        if (floatCount <= 0 || sourceStartFloat < 0 || (sourceStartFloat + floatCount) > data.size) return
        val requiredBytes = floatCount * BYTES_PER_FLOAT
        val (buffer, view) = getOrCreateUploadScratch(requiredBytes)
        var floatIndex = 0
        while (floatIndex < floatCount) {
            view.setFloat32(floatIndex * BYTES_PER_FLOAT, data[sourceStartFloat + floatIndex], true)
            floatIndex++
        }
        queueWriteBuffer(queue, targetBuffer, targetOffsetBytes, buffer, requiredBytes)
    }

    private fun getOrCreateUploadScratch(requiredBytes: Int): Pair<ArrayBuffer, DataView> {
        val currentBuffer = uploadScratchBuffer
        val currentView = uploadScratchView
        if (currentBuffer != null && currentView != null && currentBuffer.byteLength >= requiredBytes) {
            return currentBuffer to currentView
        }
        val resizedBuffer = ArrayBuffer(max(requiredBytes, 1))
        val resizedView = DataView(resizedBuffer)
        uploadScratchBuffer = resizedBuffer
        uploadScratchView = resizedView
        return resizedBuffer to resizedView
    }

    private fun getRenderScale(): Float = window.devicePixelRatio.toFloat().coerceAtLeast(1f)

}
