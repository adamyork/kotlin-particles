package com.github.adamyork.kparticles.android.common

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.SystemClock
import android.view.Choreographer
import com.github.adamyork.kparticles.platform.AppScope
import com.github.adamyork.kparticles.platform.common.PlatformInterop
import me.tatarka.inject.annotations.Inject
import java.util.concurrent.atomic.AtomicInteger

@AppScope
@Inject
class AndroidInterop : PlatformInterop {
    private val frameIdCounter = AtomicInteger(1)
    private val frameCallbacks = mutableMapOf<Int, Choreographer.FrameCallback>()
    private val listenersByType = mutableMapOf<String, MutableSet<(Any) -> Unit>>()
    private val callbackAdaptersByType = mutableMapOf<String, MutableMap<Any, (Any) -> Unit>>()

    @Volatile
    private var lastFrameTimeMs: Double = 0.0

    @Volatile
    private var initializedActivityManager: ActivityManager? = null

    fun initialize(context: Context) {
        initializedActivityManager = resolveActivityManager(context)
    }

    override fun onReady(action: () -> Unit) {
        action()
    }

    override fun getWindowHeight(): Double {
        return Resources.getSystem().displayMetrics.heightPixels.toDouble()
    }

    override fun getWindowWidth(): Double {
        return Resources.getSystem().displayMetrics.widthPixels.toDouble()
    }

    override fun hidePlatformLoader() {
        // No Android platform loader to hide.
    }

    override fun getPlatformNowTime(): Double {
        return if (lastFrameTimeMs > 0.0) {
            lastFrameTimeMs
        } else {
            SystemClock.elapsedRealtimeNanos() / 1_000_000.0
        }
    }

    override fun getBlobFromBytes(bytes: ByteArray): Any {
        return bytes
    }

    override fun isTouchDevice(): Boolean {
        return Resources.getSystem().configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH
    }

    override fun isGpuEngineSupported(platformData: Any?): Boolean {
        return false
        val activityManager = when (platformData) {
            is ActivityManager -> platformData
            is Context -> resolveActivityManager(platformData)
            else -> initializedActivityManager
        } ?: return false
        val glEsVersion = activityManager.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        return glEsVersion >= 0x00030001
    }

    override fun requestKeyboardFocus() {
        // Android activity-level key dispatch does not require explicit focus management.
    }

    private fun resolveActivityManager(context: Context): ActivityManager? {
        return context.getSystemService(ActivityManager::class.java)
            ?: (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
    }

    override fun <T> addEventListener(type: String, callback: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val callbackKey = callback as Any
        val adaptedCallback: (Any) -> Unit = { event ->
            @Suppress("UNCHECKED_CAST")
            callback(event as T)
        }
        synchronized(listenersByType) {
            callbackAdaptersByType.getOrPut(type) { mutableMapOf() }[callbackKey] = adaptedCallback
            listenersByType.getOrPut(type) { mutableSetOf() }.add(adaptedCallback)
        }
    }

    override fun <T> removeEventListener(type: String, callback: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val callbackKey = callback as Any
        synchronized(listenersByType) {
            val adapter = callbackAdaptersByType[type]?.remove(callbackKey)
            if (adapter != null) {
                listenersByType[type]?.remove(adapter)
            }
            if (listenersByType[type].isNullOrEmpty()) {
                listenersByType.remove(type)
                callbackAdaptersByType.remove(type)
            }
        }
    }

    override fun requestAnimationFrame(callback: (Double) -> Unit): Int {
        val frameId = frameIdCounter.getAndIncrement()
        val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
            val shouldInvoke = synchronized(frameCallbacks) {
                frameCallbacks.remove(frameId) != null
            }
            if (!shouldInvoke) {
                return@FrameCallback
            }
            val frameTimeMs = frameTimeNanos / 1_000_000.0
            lastFrameTimeMs = frameTimeMs
            callback(frameTimeMs)
        }
        synchronized(frameCallbacks) {
            frameCallbacks[frameId] = frameCallback
        }
        Choreographer.getInstance().postFrameCallback(frameCallback)
        return frameId
    }

    override fun cancelAnimationFrame(handle: Int) {
        val callback = synchronized(frameCallbacks) { frameCallbacks.remove(handle) } ?: return
        Choreographer.getInstance().removeFrameCallback(callback)
    }

}
