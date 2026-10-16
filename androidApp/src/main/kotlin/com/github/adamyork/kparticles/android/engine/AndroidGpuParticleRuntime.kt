package com.github.adamyork.kparticles.android.engine

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.*
import android.view.TextureView
import com.github.adamyork.kparticles.android.engine.data.AndroidGpuParticleFrame
import com.github.adamyork.kparticles.platform.common.data.ViewPort
import com.github.adamyork.kparticles.platform.engine.GpuUniformWriter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class AndroidGpuParticleRuntime(
    private val gpuUniformWriter: GpuUniformWriter
) {

    private val logger = KotlinLogging.logger {}

    companion object {
        @Volatile
        private var activeRuntime: AndroidGpuParticleRuntime? = null
        private val activeRuntimeFlow = MutableStateFlow<AndroidGpuParticleRuntime?>(null)
        fun observeActiveRuntime(): StateFlow<AndroidGpuParticleRuntime?> = activeRuntimeFlow
    }

    @Volatile
    private var enabled = false

    @Volatile
    private var maxParticles = 0
    private var computeShaderSource: String = ""
    private var vertexShaderSource: String = ""
    private var fragmentShaderSource: String = ""
    private var mapItemTextureBytes: ByteArray = ByteArray(0)
    private var mapItemSpriteWidth: Int = 1
    private var mapItemSpriteHeight: Int = 1

    private var renderer: AndroidGpuParticleRenderer? = null
    private var textureViewRef: WeakReference<TextureView>? = null

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun setAsActiveRuntime() {
        activeRuntime = this
        activeRuntimeFlow.value = this
        logger.info { "[GPU][Runtime] Active runtime published for overlay" }
    }

    @Synchronized
    fun enable(
        maxParticleCapacity: Int,
        computeShader: String,
        vertexShader: String,
        fragmentShader: String,
        mapItemTextureBytes: ByteArray,
        mapItemSpriteWidth: Int,
        mapItemSpriteHeight: Int
    ) {
        maxParticles = maxParticleCapacity.coerceAtLeast(1)
        computeShaderSource = computeShader
        vertexShaderSource = vertexShader
        fragmentShaderSource = fragmentShader
        this.mapItemTextureBytes = mapItemTextureBytes
        this.mapItemSpriteWidth = mapItemSpriteWidth.coerceAtLeast(1)
        this.mapItemSpriteHeight = mapItemSpriteHeight.coerceAtLeast(1)
        enabled = true
        renderer = createRenderer()
    }

    fun isEnabled(): Boolean = enabled

    fun submitFrame(
        sourceBuffer: FloatArray,
        dirtySlotRanges: List<IntRange>,
        deltaTime: Double,
        worldTime: Double,
        spriteTicksPerFrame: Double,
        viewPort: ViewPort
    ) {
        if (!enabled) return
        val view = textureViewRef?.get() ?: return
        if (!view.isAvailable) return
        renderFrame(
            AndroidGpuParticleFrame(
                sourceBuffer = sourceBuffer,
                dirtySlotRanges = dirtySlotRanges,
                deltaTime = deltaTime,
                worldTime = worldTime,
                spriteTicksPerFrame = spriteTicksPerFrame,
                viewPort = viewPort
            )
        )
    }

    private fun createRenderer(): AndroidGpuParticleRenderer = AndroidGpuParticleRenderer(
        maxParticles = maxParticles.coerceAtLeast(1),
        gpuUniformWriter = gpuUniformWriter,
        computeShaderSource = computeShaderSource,
        vertexShaderSource = vertexShaderSource,
        fragmentShaderSource = fragmentShaderSource,
        mapItemTextureBytes = mapItemTextureBytes,
        mapItemSpriteWidth = mapItemSpriteWidth,
        mapItemSpriteHeight = mapItemSpriteHeight
    )

    private fun renderFrame(frame: AndroidGpuParticleFrame) {
        val glRenderer = renderer ?: return
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            logger.warn { "[GPU][Runtime] eglMakeCurrent failed during frame render" }
            return
        }
        glRenderer.renderFrame(frame)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    @Synchronized
    fun createTextureView(context: Context): TextureView {
        logger.info { "[GPU][Runtime] Creating TextureView (enabled=$enabled, maxParticles=$maxParticles)" }
        val glRenderer = renderer ?: createRenderer().also { renderer = it }
        return TextureView(context).apply {
            isOpaque = false
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    logger.info { "[GPU][Runtime] SurfaceTexture available ($width x $height)" }
                    initEgl(surface)
                    glRenderer.onSurfaceCreated()
                    glRenderer.onSurfaceChanged(width, height)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    logger.info { "[GPU][Runtime] SurfaceTexture size changed ($width x $height)" }
                    glRenderer.onSurfaceChanged(width, height)
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    logger.info { "[GPU][Runtime] SurfaceTexture destroyed" }
                    releaseEgl()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                }
            }
            textureViewRef = WeakReference(this)
        }
    }

    private fun initEgl(surface: SurfaceTexture) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed")
        }
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or 0x40,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
            throw RuntimeException("eglChooseConfig failed")
        }
        val eglConfig = configs[0]!!
        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("eglCreateContext failed")
        }
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface failed")
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    private fun releaseEgl() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
