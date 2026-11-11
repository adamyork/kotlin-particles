@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.adamyork.particles.wasm.common

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.JsNumber
import kotlin.js.Promise

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
public external interface GPU : JsAny
internal external interface GPUAdapter : JsAny
internal external interface GPUDevice : JsAny
internal external interface GPUQueue : JsAny
internal external interface GPUBuffer : JsAny
internal external interface GPUCanvasContext : JsAny
internal external interface GPUShaderModule : JsAny
internal external interface GPUComputePipeline : JsAny
internal external interface GPURenderPipeline : JsAny
internal external interface GPUBindGroup : JsAny
internal external interface GPUCommandEncoder : JsAny
internal external interface GPUComputePassEncoder : JsAny
internal external interface GPURenderPassEncoder : JsAny
internal external interface GPUCommandBuffer : JsAny
internal external interface GPUTextureView : JsAny
internal external interface GPUSampler : JsAny
internal external interface GPUBindGroupLayout : JsAny
internal external interface GPUPipelineLayout : JsAny

internal external interface GpuTextureResult : JsAny {
    val view: GPUTextureView
    val width: Int
    val height: Int
}
public fun hasWebGpuSupport(): Boolean = js("typeof navigator !== 'undefined' && !!navigator.gpu")

internal fun getNavigatorGpu(): GPU = js("navigator.gpu")

internal fun requestGpuAdapter(@Suppress("UNUSED_PARAMETER") gpu: GPU): Promise<GPUAdapter?> = js("gpu.requestAdapter()")

internal fun requestGpuDevice(@Suppress("UNUSED_PARAMETER") adapter: GPUAdapter): Promise<GPUDevice> =
    js("adapter.requestDevice()")

internal fun getPreferredCanvasFormat(@Suppress("UNUSED_PARAMETER") gpu: GPU): String = js("gpu.getPreferredCanvasFormat()")

internal fun getWebGpuContext(@Suppress("UNUSED_PARAMETER") canvas: HTMLCanvasElement): GPUCanvasContext? =
    js("canvas.getContext('webgpu')")

internal fun configureWebGpuContext(
    @Suppress("UNUSED_PARAMETER") context: GPUCanvasContext,
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") format: String
) {
    js("context.configure({ device: device, format: format, alphaMode: 'premultiplied' })")
}

internal fun createStorageBuffer(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Int
): GPUBuffer = js(
    "device.createBuffer({ size: sizeBytes, usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST | GPUBufferUsage.COPY_SRC })"
)

internal fun createReadbackBuffer(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Int
): GPUBuffer = js(
    "device.createBuffer({ size: sizeBytes, usage: GPUBufferUsage.MAP_READ | GPUBufferUsage.COPY_DST })"
)

internal fun readLiveParticleCount(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") sourceBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") readbackBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Int,
    @Suppress("UNUSED_PARAMETER") floatsPerParticle: Int
): Promise<JsNumber> = js(
    "(async () => { const encoder = device.createCommandEncoder(); encoder.copyBufferToBuffer(sourceBuffer, 0, readbackBuffer, 0, sizeBytes); queue.submit([encoder.finish()]); await readbackBuffer.mapAsync(GPUMapMode.READ); const data = new Float32Array(readbackBuffer.getMappedRange()); let liveCount = 0; for (let index = 15; index < data.length; index += floatsPerParticle) { if (data[index] > 0.5) { liveCount++; } } readbackBuffer.unmap(); return liveCount; })()"
)

internal fun createDataPacketBuffer(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Int
): GPUBuffer = js(
    "device.createBuffer({ size: sizeBytes, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST })"
)

internal fun createShaderModule(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") shaderCode: String
): GPUShaderModule = js(
    "device.createShaderModule({ code: shaderCode })"
)

internal fun createComputePipelineWithLayout(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") shaderModule: GPUShaderModule,
    @Suppress("UNUSED_PARAMETER") entryPoint: String,
    @Suppress("UNUSED_PARAMETER") pipelineLayout: GPUPipelineLayout
): GPUComputePipeline = js(
    "device.createComputePipeline({ layout: pipelineLayout, compute: { module: shaderModule, entryPoint: entryPoint } })"
)

internal fun createSimulationBindGroupLayout(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice
): GPUBindGroupLayout = js(
    "device.createBindGroupLayout({ entries: [{ binding: 0, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'storage' } }, { binding: 1, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'read-only-storage' } }, { binding: 2, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'uniform' } }, { binding: 3, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'storage' } }, { binding: 4, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'storage' } }, { binding: 5, visibility: GPUShaderStage.COMPUTE, buffer: { type: 'storage' } }] })"
)

internal fun createPipelineLayout(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") bindGroupLayout: GPUBindGroupLayout
): GPUPipelineLayout = js(
    "device.createPipelineLayout({ bindGroupLayouts: [bindGroupLayout] })"
)

internal fun createSimulationBindGroup(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") bindGroupLayout: GPUBindGroupLayout,
    @Suppress("UNUSED_PARAMETER") stateBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") spawnBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") dataPacketBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") kinematicsBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") gridBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") scratchBuffer: GPUBuffer
): GPUBindGroup = js(
    "device.createBindGroup({ layout: bindGroupLayout, entries: [{ binding: 0, resource: { buffer: stateBuffer } }, { binding: 1, resource: { buffer: spawnBuffer } }, { binding: 2, resource: { buffer: dataPacketBuffer } }, { binding: 3, resource: { buffer: kinematicsBuffer } }, { binding: 4, resource: { buffer: gridBuffer } }, { binding: 5, resource: { buffer: scratchBuffer } }] })"
)

internal fun getShaderCompilationReport(
    @Suppress("UNUSED_PARAMETER") shaderModule: GPUShaderModule
): Promise<JsString> = js(
    "(async () => { const info = await shaderModule.getCompilationInfo(); return info.messages.map(m => '[' + m.type + '] line ' + m.lineNum + ':' + m.linePos + ' ' + m.message).join('\\n'); })()"
)

internal fun installGpuErrorLogging(@Suppress("UNUSED_PARAMETER") device: GPUDevice) {
    js("device.addEventListener('uncapturederror', (event) => { console.error('[webgpu] uncaptured error: ' + event.error.message); }); device.lost.then((info) => { console.error('[webgpu] device lost: ' + info.reason + ' ' + info.message); });")
}

internal fun createRenderBindGroupLayout(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice
): GPUBindGroupLayout = js(
    "device.createBindGroupLayout({ entries: [{ binding: 0, visibility: GPUShaderStage.VERTEX, buffer: { type: 'read-only-storage' } }, { binding: 1, visibility: GPUShaderStage.VERTEX | GPUShaderStage.FRAGMENT, buffer: { type: 'uniform' } }, { binding: 2, visibility: GPUShaderStage.FRAGMENT, sampler: { type: 'filtering' } }, { binding: 3, visibility: GPUShaderStage.FRAGMENT, texture: { sampleType: 'float' } }] })"
)

internal fun createStyleRenderPipeline(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") shaderModule: GPUShaderModule,
    @Suppress("UNUSED_PARAMETER") pipelineLayout: GPUPipelineLayout,
    @Suppress("UNUSED_PARAMETER") format: String,
    @Suppress("UNUSED_PARAMETER") vertexEntryPoint: String,
    @Suppress("UNUSED_PARAMETER") fragmentEntryPoint: String,
    @Suppress("UNUSED_PARAMETER") styleCode: Double,
    @Suppress("UNUSED_PARAMETER") useMaxBlend: Boolean
): GPURenderPipeline = js(
    "device.createRenderPipeline({ layout: pipelineLayout, vertex: { module: shaderModule, entryPoint: vertexEntryPoint, constants: { activeStyleCode: styleCode } }, fragment: { module: shaderModule, entryPoint: fragmentEntryPoint, constants: { activeStyleCode: styleCode }, targets: [{ format: format, blend: useMaxBlend ? { color: { srcFactor: 'one', dstFactor: 'one', operation: 'max' }, alpha: { srcFactor: 'one', dstFactor: 'one', operation: 'max' } } : { color: { srcFactor: 'src-alpha', dstFactor: 'one-minus-src-alpha', operation: 'add' }, alpha: { srcFactor: 'one', dstFactor: 'one-minus-src-alpha', operation: 'add' } } }] }, primitive: { topology: 'triangle-list' } })"
)

internal fun createRenderBindGroupWithLayout(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") bindGroupLayout: GPUBindGroupLayout,
    @Suppress("UNUSED_PARAMETER") stateBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") dataPacketBuffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") sampler: GPUSampler,
    @Suppress("UNUSED_PARAMETER") textureView: GPUTextureView
): GPUBindGroup = js(
    "device.createBindGroup({ layout: bindGroupLayout, entries: [{ binding: 0, resource: { buffer: stateBuffer } }, { binding: 1, resource: { buffer: dataPacketBuffer } }, { binding: 2, resource: sampler }, { binding: 3, resource: textureView }] })"
)

internal fun createLinearSampler(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice
): GPUSampler = js(
    "device.createSampler({ magFilter: 'linear', minFilter: 'linear', mipmapFilter: 'linear' })"
)

internal fun createTextureViewFromEncodedBytes(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") bytes: Int8Array,
    @Suppress("UNUSED_PARAMETER") firstCellWidth: Int,
    @Suppress("UNUSED_PARAMETER") firstCellHeight: Int
): Promise<GpuTextureResult> = js(
    "(async () => { const blob = new Blob([bytes]); const fullBitmap = await createImageBitmap(blob); const cellWidth = Math.max(1, Math.min(firstCellWidth || fullBitmap.width, fullBitmap.width)); const cellHeight = Math.max(1, Math.min(firstCellHeight || fullBitmap.height, fullBitmap.height)); const imageBitmap = (cellWidth === fullBitmap.width && cellHeight === fullBitmap.height) ? fullBitmap : await createImageBitmap(fullBitmap, 0, 0, cellWidth, cellHeight); if (imageBitmap !== fullBitmap) { fullBitmap.close(); } const texture = device.createTexture({ size: { width: imageBitmap.width, height: imageBitmap.height, depthOrArrayLayers: 1 }, format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT }); queue.copyExternalImageToTexture({ source: imageBitmap }, { texture: texture }, { width: imageBitmap.width, height: imageBitmap.height, depthOrArrayLayers: 1 }); const resultWidth = imageBitmap.width; const resultHeight = imageBitmap.height; imageBitmap.close(); return { view: texture.createView(), width: resultWidth, height: resultHeight }; })()"
)

internal fun createSolidTextureView(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") r: Int,
    @Suppress("UNUSED_PARAMETER") g: Int,
    @Suppress("UNUSED_PARAMETER") b: Int,
    @Suppress("UNUSED_PARAMETER") a: Int
): GPUTextureView = js(
    "(() => { const texture = device.createTexture({ size: { width: 1, height: 1, depthOrArrayLayers: 1 }, format: 'rgba8unorm', usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST }); queue.writeTexture({ texture: texture }, new Uint8Array([r, g, b, a]), { bytesPerRow: 4 }, { width: 1, height: 1, depthOrArrayLayers: 1 }); return texture.createView(); })()"
)

internal fun getGpuQueue(@Suppress("UNUSED_PARAMETER") device: GPUDevice): GPUQueue = js("device.queue")

internal fun queueWriteBuffer(
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") buffer: GPUBuffer,
    @Suppress("UNUSED_PARAMETER") bufferOffsetBytes: Int,
    @Suppress("UNUSED_PARAMETER") data: ArrayBuffer,
    @Suppress("UNUSED_PARAMETER") dataSizeBytes: Int
) {
    js("queue.writeBuffer(buffer, bufferOffsetBytes, data, 0, dataSizeBytes)")
}

internal fun createCommandEncoder(@Suppress("UNUSED_PARAMETER") device: GPUDevice): GPUCommandEncoder =
    js("device.createCommandEncoder()")

internal fun beginComputePass(@Suppress("UNUSED_PARAMETER") encoder: GPUCommandEncoder): GPUComputePassEncoder =
    js("encoder.beginComputePass()")

internal fun setComputePipeline(
    @Suppress("UNUSED_PARAMETER") pass: GPUComputePassEncoder,
    @Suppress("UNUSED_PARAMETER") pipeline: GPUComputePipeline
) {
    js("pass.setPipeline(pipeline)")
}

internal fun setComputeBindGroup(
    @Suppress("UNUSED_PARAMETER") pass: GPUComputePassEncoder,
    @Suppress("UNUSED_PARAMETER") bindGroup: GPUBindGroup
) {
    js("pass.setBindGroup(0, bindGroup)")
}

internal fun dispatchCompute(
    @Suppress("UNUSED_PARAMETER") pass: GPUComputePassEncoder,
    @Suppress("UNUSED_PARAMETER") workgroups: Int
) {
    js("pass.dispatchWorkgroups(workgroups)")
}

internal fun endComputePass(@Suppress("UNUSED_PARAMETER") pass: GPUComputePassEncoder) {
    js("pass.end()")
}

internal fun finishCommandEncoder(@Suppress("UNUSED_PARAMETER") encoder: GPUCommandEncoder): GPUCommandBuffer =
    js("encoder.finish()")

internal fun submitCommandBuffer(
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") commandBuffer: GPUCommandBuffer
) {
    js("queue.submit([commandBuffer])")
}

internal fun getCurrentTextureView(@Suppress("UNUSED_PARAMETER") context: GPUCanvasContext): GPUTextureView =
    js("context.getCurrentTexture().createView()")

internal fun beginRenderPass(
    @Suppress("UNUSED_PARAMETER") encoder: GPUCommandEncoder,
    @Suppress("UNUSED_PARAMETER") textureView: GPUTextureView
): GPURenderPassEncoder = js(
    "encoder.beginRenderPass({ colorAttachments: [{ view: textureView, clearValue: { r: 0, g: 0, b: 0, a: 0 }, loadOp: 'clear', storeOp: 'store' }] })"
)

internal fun setRenderPipeline(
    @Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder,
    @Suppress("UNUSED_PARAMETER") pipeline: GPURenderPipeline
) {
    js("pass.setPipeline(pipeline)")
}

internal fun setRenderBindGroup(
    @Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder,
    @Suppress("UNUSED_PARAMETER") bindGroup: GPUBindGroup
) {
    js("pass.setBindGroup(0, bindGroup)")
}

internal fun drawRenderPassInstanced(
    @Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder,
    @Suppress("UNUSED_PARAMETER") vertexCount: Int,
    @Suppress("UNUSED_PARAMETER") instanceCount: Int,
    @Suppress("UNUSED_PARAMETER") firstInstance: Int
) {
    js("pass.draw(vertexCount, instanceCount, 0, firstInstance)")
}

internal fun endRenderPass(@Suppress("UNUSED_PARAMETER") pass: GPURenderPassEncoder) {
    js("pass.end()")
}

