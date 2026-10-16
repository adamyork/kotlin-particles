package com.github.adamyork.kparticles.wasm.engine

import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.GpuUniformWriter
import com.github.adamyork.kparticles.platform.engine.effect.ParticleEffectGenerator
import com.github.adamyork.kparticles.wasm.common.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.toInt8Array
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.ceil
import kotlin.math.max

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
@OptIn(ExperimentalWasmJsInterop::class)
class WasmJsGpuParticleRenderer(
    private val gpuUniformWriter: GpuUniformWriter
) {

    companion object {
        private const val WORKGROUP_SIZE = 64
        private const val FLOATS_PER_PARTICLE = ParticleEffectGenerator.GPU_STATE_FLOATS_PER_PARTICLE
        private const val BYTES_PER_FLOAT = 4
        private const val KINEMATICS_BYTES_PER_PARTICLE = 64
        private const val GRID_BUFFER_BYTES = 1048576
        private const val SCRATCH_EXTRA_UNSIGNED_INTEGERS = 1024
        private const val VERTEX_ENTRY_POINT = "vertexMain"
        private const val FRAGMENT_ENTRY_POINT_SHAPES = "fragmentMainShapes"
        private const val FRAGMENT_ENTRY_POINT_DUST = "fragmentMainDust"
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
    private var simulationUniformBuffer: GPUBuffer? = null
    private var renderUniformBuffer: GPUBuffer? = null
    private var mapItemSampler: GPUSampler? = null
    private var mapItemTextureView: GPUTextureView? = null
    private var mapItemSpriteWidth: Int = 1
    private var mapItemSpriteHeight: Int = 1
    private var mapItemSheetFrameCount: Int = 1
    private var lastSpriteTicksPerFrame: Double = 1.0

    private val simulationPipelines = mutableListOf<GPUComputePipeline>()
    private val simulationWorkgroupCounts = mutableListOf<Int>()
    private var renderPipelineShapes: GPURenderPipeline? = null
    private var renderPipelineDust: GPURenderPipeline? = null

    private var simulationBindGroup: GPUBindGroup? = null
    private var renderBindGroupShapes: GPUBindGroup? = null
    private var renderBindGroupDust: GPUBindGroup? = null

    private val simulationUniformData = FloatArray(GpuUniformWriter.SIMULATION_UNIFORM_FLOATS)
    private val renderUniformData = FloatArray(GpuUniformWriter.RENDER_UNIFORM_FLOATS)
    private var uploadScratchBuffer: ArrayBuffer? = null
    private var uploadScratchView: DataView? = null

    suspend fun initialize(
        maxParticleCapacity: Int,
        particlesShaderSource: String,
        overlayCanvas: HTMLCanvasElement,
        mapItemTextureBytes: ByteArray,
        mapItemFirstCellWidth: Int,
        mapItemFirstCellHeight: Int
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
        val createdSimulationUniform = createUniformBuffer(gpuDevice, simulationUniformData.size * BYTES_PER_FLOAT)
        val createdKinematics = createStorageBuffer(gpuDevice, maxParticles * KINEMATICS_BYTES_PER_PARTICLE)
        val createdGrid = createStorageBuffer(gpuDevice, GRID_BUFFER_BYTES)
        val createdScratch = createStorageBuffer(gpuDevice, (maxParticles * 4 + SCRATCH_EXTRA_UNSIGNED_INTEGERS) * BYTES_PER_FLOAT)
        val createdRenderUniform = createUniformBuffer(gpuDevice, renderUniformData.size * BYTES_PER_FLOAT)
        val createdMapItemSampler = createLinearSampler(gpuDevice)
        val deviceQueue = getGpuQueue(gpuDevice)
        var createdSheetFrameCount = 1
        val createdMapItemTextureView = if (mapItemTextureBytes.isNotEmpty()) {
            try {
                val textureResult = createTextureViewFromEncodedBytes(
                    device = gpuDevice,
                    queue = deviceQueue,
                    bytes = mapItemTextureBytes.toInt8Array(),
                    firstCellWidth = 0,
                    firstCellHeight = 0
                ).await()
                createdSheetFrameCount = (textureResult.width / mapItemFirstCellWidth.coerceAtLeast(1)).coerceAtLeast(1)
                textureResult.view
            } catch (t: Throwable) {
                logger.error(t) { "Failed to create map item return texture for WebGPU particles" }
                return false
            }
        } else {
            createSolidTextureView(gpuDevice, deviceQueue, 255, 255, 255, 255)
        }

        val shaderModule = createShaderModule(gpuDevice, particlesShaderSource)
        val compilationReport = getShaderCompilationReport(shaderModule).await().toString()
        if (compilationReport.isNotBlank()) {
            logger.error { "particles.wgsl compilation report:\n$compilationReport" }
        }
        if (compilationReport.contains(SHADER_ERROR_MARKER)) {
            return false
        }

        val simulationLayout = createSimulationBindGroupLayout(gpuDevice)
        val simulationPipelineLayout = createPipelineLayout(gpuDevice, simulationLayout)
        val createdSimulationPipelines = SIMULATION_STAGE_ENTRY_POINTS.map { entryPoint ->
            createComputePipelineWithLayout(gpuDevice, shaderModule, entryPoint, simulationPipelineLayout)
        }
        val createdRenderPipelineShapes = createRenderPipeline(
            gpuDevice,
            shaderModule,
            shaderModule,
            preferredFormat,
            VERTEX_ENTRY_POINT,
            FRAGMENT_ENTRY_POINT_SHAPES
        )
        val createdRenderPipelineDust = createRenderPipelineMaxBlend(
            gpuDevice,
            shaderModule,
            shaderModule,
            preferredFormat,
            VERTEX_ENTRY_POINT,
            FRAGMENT_ENTRY_POINT_DUST
        )

        val createdSimulationBindGroup = createSimulationBindGroup(
            gpuDevice,
            simulationLayout,
            createdState,
            createdSpawn,
            createdSimulationUniform,
            createdKinematics,
            createdGrid,
            createdScratch
        )
        val createdRenderBindGroupShapes = createRenderBindGroup(
            gpuDevice,
            createdRenderPipelineShapes,
            createdState,
            createdRenderUniform,
            createdMapItemSampler,
            createdMapItemTextureView
        )
        val createdRenderBindGroupDust = createRenderBindGroup(
            gpuDevice,
            createdRenderPipelineDust,
            createdState,
            createdRenderUniform,
            createdMapItemSampler,
            createdMapItemTextureView
        )

        device = gpuDevice
        context = webGpuContext
        queue = deviceQueue
        presentationFormat = preferredFormat
        mapItemSpriteWidth = mapItemFirstCellWidth.coerceAtLeast(1)
        mapItemSpriteHeight = mapItemFirstCellHeight.coerceAtLeast(1)
        mapItemSheetFrameCount = createdSheetFrameCount
        stateBuffer = createdState
        spawnBuffer = createdSpawn
        simulationUniformBuffer = createdSimulationUniform
        renderUniformBuffer = createdRenderUniform
        mapItemSampler = createdMapItemSampler
        mapItemTextureView = createdMapItemTextureView
        simulationPipelines.clear()
        simulationPipelines += createdSimulationPipelines
        simulationWorkgroupCounts.clear()
        simulationWorkgroupCounts += SIMULATION_STAGE_ENTRY_POINTS.map { entryPoint ->
            if (entryPoint in SINGLE_WORKGROUP_ENTRY_POINTS) 1 else computeWorkgroupCount
        }
        renderPipelineShapes = createdRenderPipelineShapes
        renderPipelineDust = createdRenderPipelineDust
        simulationBindGroup = createdSimulationBindGroup
        renderBindGroupShapes = createdRenderBindGroupShapes
        renderBindGroupDust = createdRenderBindGroupDust
        initialized = true
        logger.info { "WebGPU particle renderer initialized with $maxParticles slots" }
        return true
    }

    fun updateGpuParticleBuffer(
        sourceBuffer: FloatArray,
        dirtySlotRanges: List<IntRange>,
        deltaTime: Double,
        worldTime: Double,
        spriteTicksPerFrame: Double,
        viewPort: ViewPort
    ) {
        check(initialized) { "WebGPU renderer must be initialized before updateGpuParticleBuffer" }
        val gpuDevice = device ?: return
        val gpuQueue = queue ?: return
        val spawn = spawnBuffer ?: return
        val simulationUniform = simulationUniformBuffer ?: return
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
        gpuUniformWriter.writeSimulationUniforms(
            simulationUniformData, deltaTime, worldTime, spriteTicksPerFrame, maxParticles, viewPort
        )
        writeFloatArrayBuffer(gpuQueue, simulationUniform, simulationUniformData)

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

    fun draw(viewPort: ViewPort) {
        check(initialized) { "WebGPU renderer must be initialized before draw" }
        val gpuDevice = device ?: return
        val webGpuContext = context ?: return
        val gpuQueue = queue ?: return
        val shapesPipeline = renderPipelineShapes ?: return
        val dustPipeline = renderPipelineDust ?: return
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

        gpuUniformWriter.writeRenderUniforms(
            renderUniformData,
            viewPort,
            targetCanvas.width,
            targetCanvas.height,
            getRenderScale(),
            mapItemSpriteWidth,
            mapItemSpriteHeight,
            mapItemSheetFrameCount,
            lastSpriteTicksPerFrame
        )
        val renderUniform = renderUniformBuffer ?: return
        writeFloatArrayBuffer(gpuQueue, renderUniform, renderUniformData)

        val textureView: GPUTextureView = getCurrentTextureView(webGpuContext)
        val shapesBindGroup = renderBindGroupShapes ?: return
        val dustBindGroup = renderBindGroupDust ?: return

        val encoder: GPUCommandEncoder = createCommandEncoder(gpuDevice)
        val renderPass: GPURenderPassEncoder = beginRenderPass(encoder, textureView)
        setRenderPipeline(renderPass, shapesPipeline)
        setRenderBindGroup(renderPass, shapesBindGroup)
        drawRenderPassInstanced(renderPass, 6, maxParticles)
        setRenderPipeline(renderPass, dustPipeline)
        setRenderBindGroup(renderPass, dustBindGroup)
        drawRenderPassInstanced(renderPass, 6, maxParticles)
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
