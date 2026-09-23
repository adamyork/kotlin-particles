@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.adamyork.kparticles.wasm.common

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLCanvasElement
import org.w3c.files.Blob
import kotlin.js.Promise

external interface VisualViewport {
    val height: Double
    val width: Double
}

external interface GPU : JsAny
external interface GPUAdapter : JsAny
external interface GPUDevice : JsAny
external interface GPUQueue : JsAny
external interface GPUBuffer : JsAny
external interface GPUCanvasContext : JsAny
external interface GPUShaderModule : JsAny
external interface GPUComputePipeline : JsAny
external interface GPURenderPipeline : JsAny
external interface GPUBindGroup : JsAny
external interface GPUCommandEncoder : JsAny
external interface GPUComputePassEncoder : JsAny
external interface GPURenderPassEncoder : JsAny
external interface GPUCommandBuffer : JsAny
external interface GPUTextureView : JsAny
external interface GPUSampler : JsAny
external interface ProjectileHitSnapshot : JsAny {
    val hitCount: Int
    val hitX: Float
    val hitY: Float
    val hitSize: Float
}

fun createBlobFromInt8Array(@Suppress("UNUSED_PARAMETER") int8Array: Int8Array): Blob =
    js("new Blob([int8Array])")

fun getVisualViewport(): VisualViewport = js(
    """(window.visualViewport || {
        height: window.innerHeight,
        width: window.innerWidth
    })"""
)

fun hasWebGpuSupport(): Boolean = js("typeof navigator !== 'undefined' && !!navigator.gpu")

fun getNavigatorGpu(): GPU = js("navigator.gpu")

fun requestGpuAdapter(@Suppress("UNUSED_PARAMETER") gpu: GPU): Promise<GPUAdapter?> = js("gpu.requestAdapter()")

fun requestGpuDevice(@Suppress("UNUSED_PARAMETER") adapter: GPUAdapter): Promise<GPUDevice> =
    js("adapter.requestDevice()")

fun getPreferredCanvasFormat(@Suppress("UNUSED_PARAMETER") gpu: GPU): String = js("gpu.getPreferredCanvasFormat()")

fun getWebGpuContext(@Suppress("UNUSED_PARAMETER") canvas: HTMLCanvasElement): GPUCanvasContext? =
    js("canvas.getContext('webgpu')")

fun configureWebGpuContext(
    @Suppress("UNUSED_PARAMETER") context: GPUCanvasContext,
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") format: String
) {
    js("context.configure({ device: device, format: format, alphaMode: 'premultiplied' })")
}

fun createStorageBuffer(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Int
): GPUBuffer = js(
    "device.createBuffer({ size: sizeBytes, usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST | GPUBufferUsage.COPY_SRC })"
)

fun createReadbackBuffer(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Int
): GPUBuffer = js(
    "device.createBuffer({ size: sizeBytes, usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ })"
)

fun createUniformBuffer(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Int
): GPUBuffer = js(
    "device.createBuffer({ size: sizeBytes, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST })"
)

fun createShaderModule(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") shaderCode: String
): GPUShaderModule = js(
    "device.createShaderModule({ code: shaderCode })"
)

fun createComputePipeline(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") shaderModule: GPUShaderModule,
    @Suppress("UNUSED_PARAMETER") entryPoint: String
): GPUComputePipeline = js(
    "device.createComputePipeline({ layout: 'auto', compute: { module: shaderModule, entryPoint: entryPoint } })"
)

fun createRenderPipeline(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") vertexModule: GPUShaderModule,
    @Suppress("UNUSED_PARAMETER") fragmentModule: GPUShaderModule,
    @Suppress("UNUSED_PARAMETER") format: String,
    @Suppress("UNUSED_PARAMETER") vertexEntryPoint: String,
    @Suppress("UNUSED_PARAMETER") fragmentEntryPoint: String
): GPURenderPipeline = js(
    "device.createRenderPipeline({ layout: 'auto', vertex: { module: vertexModule, entryPoint: vertexEntryPoint }, fragment: { module: fragmentModule, entryPoint: fragmentEntryPoint, targets: [{ format: format, blend: { color: { srcFactor: 'src-alpha', dstFactor: 'one-minus-src-alpha', operation: 'add' }, alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' } } }] }, primitive: { topology: 'triangle-list' } })"
)

fun createRenderPipelineMaxBlend(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") vertexModule: GPUShaderModule,
    @Suppress("UNUSED_PARAMETER") fragmentModule: GPUShaderModule,
    @Suppress("UNUSED_PARAMETER") format: String,
    @Suppress("UNUSED_PARAMETER") vertexEntryPoint: String,
    @Suppress("UNUSED_PARAMETER") fragmentEntryPoint: String
): GPURenderPipeline = js(
    "device.createRenderPipeline({ layout: 'auto', vertex: { module: vertexModule, entryPoint: vertexEntryPoint }, fragment: { module: fragmentModule, entryPoint: fragmentEntryPoint, targets: [{ format: format, blend: { color: { srcFactor: 'one', dstFactor: 'one', operation: 'max' }, alpha: { srcFactor: 'one', dstFactor: 'one', operation: 'max' } } }] }, primitive: { topology: 'triangle-list' } })"
)

fun createComputeBindGroup(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") pipeline: GPUComputePipeline,
    @Suppress("UNUSED_PARAMETER") srcBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") dstBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") spawnBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") uniformBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") collisionBuffer: GPUBuffer
): GPUBindGroup = js(
    "device.createBindGroup({ layout: pipeline.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: srcBuffer } }, { binding: 1, resource: { buffer: dstBuffer } }, { binding: 2, resource: { buffer: spawnBuffer } }, { binding: 3, resource: { buffer: uniformBuffer } }, { binding: 4, resource: { buffer: collisionBuffer } }] })"
)

fun createRenderBindGroup(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") pipeline: GPURenderPipeline,
    @Suppress("UNUSED_PARAMETER") stateBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") uniformBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") sampler: GPUSampler,
    @Suppress("UNUSED_PARAMETER") textureView: GPUTextureView
): GPUBindGroup = js(
    "device.createBindGroup({ layout: pipeline.getBindGroupLayout(0), entries: [{ binding: 0, resource: { buffer: stateBuffer } }, { binding: 1, resource: { buffer: uniformBuffer } }, { binding: 2, resource: sampler }, { binding: 3, resource: textureView }] })"
)

fun createLinearSampler(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice
): GPUSampler = js(
    "device.createSampler({ magFilter: 'linear', minFilter: 'linear', mipmapFilter: 'linear' })"
)

fun createTextureViewFromEncodedBytes(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") bytes: Int8Array,
    @Suppress("UNUSED_PARAMETER") firstCellWidth: Int,
    @Suppress("UNUSED_PARAMETER") firstCellHeight: Int
): Promise<GPUTextureView> = js(
    "(async () => { const blob = new Blob([bytes]); const fullBitmap = await createImageBitmap(blob); const cellWidth = Math.max(1, Math.min(firstCellWidth || fullBitmap.width, fullBitmap.width)); const cellHeight = Math.max(1, Math.min(firstCellHeight || fullBitmap.height, fullBitmap.height)); const imageBitmap = (cellWidth === fullBitmap.width && cellHeight === fullBitmap.height) ? fullBitmap : await createImageBitmap(fullBitmap, 0, 0, cellWidth, cellHeight); if (imageBitmap !== fullBitmap) { fullBitmap.close(); } const texture = device.createTexture({ size: { width: imageBitmap.width, height: imageBitmap.height, depthOrArrayLayers: 1 }, format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT }); queue.copyExternalImageToTexture({ source: imageBitmap }, { texture: texture }, { width: imageBitmap.width, height: imageBitmap.height, depthOrArrayLayers: 1 }); imageBitmap.close(); return texture.createView(); })()"
)

fun createSolidTextureView(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") r: Int,
    @Suppress("UNUSED_PARAMETER") g: Int,
    @Suppress("UNUSED_PARAMETER") b: Int,
    @Suppress("UNUSED_PARAMETER") a: Int
): GPUTextureView = js(
    "(() => { const texture = device.createTexture({ size: { width: 1, height: 1, depthOrArrayLayers: 1 }, format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST }); queue.writeTexture({ texture: texture }, new Uint8Array([r, g, b, a]), { bytesPerRow: 4 }, { width: 1, height: 1, depthOrArrayLayers: 1 }); return texture.createView(); })()"
)

fun getGpuQueue(@Suppress("UNUSED_PARAMETER") device: GPUDevice): GPUQueue = js("device.queue")

fun queueWriteBuffer(
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") buffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") bufferOffsetBytes: Int,
    @Suppress("UNUSED_PARAMETER") data: ArrayBuffer,
    @Suppress("UNUSED_PARAMETER") dataSizeBytes: Int
) {
    js("queue.writeBuffer(buffer, bufferOffsetBytes, data, 0, dataSizeBytes)")
}

fun createCommandEncoder(@Suppress("UNUSED_PARAMETER") device: GPUDevice): GPUCommandEncoder =
    js("device.createCommandEncoder()")

fun copyBufferToBuffer(
    @Suppress("UNUSED_PARAMETER") encoder: GPUCommandEncoder,
    @Suppress("UNUSED_PARAMETER") srcBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") dstBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Int
) {
    js("encoder.copyBufferToBuffer(srcBuffer, 0, dstBuffer, 0, sizeBytes)")
}

fun readProjectileHitSnapshotFromReadback(
    @Suppress("UNUSED_PARAMETER") readbackBuffer: GPUBuffer
): Promise<ProjectileHitSnapshot> = js(
    "(async () => { try { await readbackBuffer.mapAsync(GPUMapMode.READ); const raw = new Uint32Array(readbackBuffer.getMappedRange()); const floatView = new Float32Array(raw.buffer); const hitCount = raw[0] || 0; const hitX = floatView[1] || 0; const hitY = floatView[2] || 0; const hitSize = floatView[3] || 1; readbackBuffer.unmap(); return { hitCount, hitX, hitY, hitSize }; } catch (_) { return { hitCount: 0, hitX: 0, hitY: 0, hitSize: 1 }; } })()"
)

fun beginComputePass(@Suppress("UNUSED_PARAMETER") encoder: GPUCommandEncoder): GPUComputePassEncoder =
    js("encoder.beginComputePass()")

fun setComputePipeline(
    @Suppress("UNUSED_PARAMETER") pass: GPUComputePassEncoder,
    @Suppress("UNUSED_PARAMETER") pipeline: GPUComputePipeline
) {
    js("pass.setPipeline(pipeline)")
}

fun setComputeBindGroup(
    @Suppress("UNUSED_PARAMETER") pass: GPUComputePassEncoder,
    @Suppress("UNUSED_PARAMETER") bindGroup: GPUBindGroup
) {
    js("pass.setBindGroup(0, bindGroup)")
}

fun dispatchCompute(
    @Suppress("UNUSED_PARAMETER") pass: GPUComputePassEncoder,
    @Suppress("UNUSED_PARAMETER") workgroups: Int
) {
    js("pass.dispatchWorkgroups(workgroups)")
}

fun endComputePass(@Suppress("UNUSED_PARAMETER") pass: GPUComputePassEncoder) {
    js("pass.end()")
}

fun finishCommandEncoder(@Suppress("UNUSED_PARAMETER") encoder: GPUCommandEncoder): GPUCommandBuffer =
    js("encoder.finish()")

fun submitCommandBuffer(
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") commandBuffer: GPUCommandBuffer
) {
    js("queue.submit([commandBuffer])")
}

fun getCurrentTextureView(@Suppress("UNUSED_PARAMETER") context: GPUCanvasContext): GPUTextureView =
    js("context.getCurrentTexture().createView()")

fun beginRenderPass(
    @Suppress("UNUSED_PARAMETER") encoder: GPUCommandEncoder,
    @Suppress("UNUSED_PARAMETER") textureView: GPUTextureView
): GPURenderPassEncoder = js(
    "encoder.beginRenderPass({ colorAttachments: [{ view: textureView, clearValue: { r: 0, g: 0, b: 0, a: 0 }, loadOp: 'clear', storeOp: 'store' }] })"
)

fun setRenderPipeline(
    @Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder,
    @Suppress("UNUSED_PARAMETER") pipeline: GPURenderPipeline
) {
    js("pass.setPipeline(pipeline)")
}

fun setRenderBindGroup(
    @Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder,
    @Suppress("UNUSED_PARAMETER") bindGroup: GPUBindGroup
) {
    js("pass.setBindGroup(0, bindGroup)")
}

fun drawRenderPass(
    @Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder,
    @Suppress("UNUSED_PARAMETER") vertexCount: Int
) {
    js("pass.draw(vertexCount, 1, 0, 0)")
}

fun drawRenderPassInstanced(
    @Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder,
    @Suppress("UNUSED_PARAMETER") vertexCount: Int,
    @Suppress("UNUSED_PARAMETER") instanceCount: Int
) {
    js("pass.draw(vertexCount, instanceCount, 0, 0)")
}

fun endRenderPass(@Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder) {
    js("pass.end()")
}

