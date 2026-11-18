package com.portsotc.mobile.nativebridge

import android.os.Build
import android.view.Surface
import com.portsotc.mobile.core.Settings

/**
 * Thin, failure-tolerant Kotlin facade over the native engine.
 *
 * The library is loaded lazily so the rest of the application (import,
 * settings, diagnostics) still works on a device where the native build is
 * unavailable, and no JNI failure can crash the UI thread.
 */
object NativeCore {

    private var loadFailure: String? = null

    var lastError: String? = null
        private set

    val available: Boolean get() = loadFailure == null
    val loadError: String? get() = loadFailure

    val deviceAbi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    init {
        try {
            System.loadLibrary("sotc_core")
        } catch (error: Throwable) {
            loadFailure = error.message ?: error.javaClass.simpleName
        }
    }

    fun start(surface: Surface) {
        guardUnit("start") { nativeStart(surface) }
    }

    fun stop() {
        guardUnit("stop") { nativeStop() }
    }

    fun setPaused(paused: Boolean) {
        guardUnit("setPaused") { nativeSetPaused(paused) }
    }

    fun setPad(buttons: Int, leftX: Float, leftY: Float, rightX: Float, rightY: Float) {
        guardUnit("setPad") { nativeSetPad(buttons, leftX, leftY, rightX, rightY) }
    }

    fun configure(settings: Settings) {
        guardUnit("configure") {
            nativeConfigure(
                settings.resolutionScalePercent,
                settings.linearFiltering,
                settings.audioEnabled,
                settings.audioVolume
            )
        }
    }

    fun loadGame(directory: String): Boolean =
        guard("loadGame") { nativeLoadGame(directory) } ?: false

    fun unloadGame() {
        guardUnit("unloadGame") { nativeUnloadGame() }
    }

    fun statusJson(): String = guard("status") { nativeStatus() } ?: failureJson()

    fun coreStatus(): String =
        guard("coreStatus") { nativeCoreStatus() }
            ?: (loadFailure ?: "Native core status unavailable")

    /**
     * Publishes an environment variable for other native libraries in this
     * process. Used to hand the boot executable path to the recompiled runtime
     * before Android loads it, because a NativeActivity receives no argv.
     */
    fun setEnvironment(name: String, value: String): Boolean =
        guard("setEnvironment") { nativeSetEnvironment(name, value) } ?: false

    private inline fun <T> guard(operation: String, block: () -> T): T? {
        if (!available) return null
        return try {
            block()
        } catch (error: Throwable) {
            lastError = "$operation failed: ${error.message ?: error.javaClass.simpleName}"
            null
        }
    }

    private inline fun guardUnit(operation: String, block: () -> Unit) {
        guard(operation, block)
    }

    private fun failureJson(): String =
        "{\"running\":false,\"paused\":false,\"width\":0,\"height\":0,\"fps\":0.0," +
            "\"frameMs\":0.0,\"scalePercent\":100,\"linearFiltering\":true," +
            "\"audioEnabled\":false,\"padButtons\":0,\"frameCount\":0," +
            "\"core\":{\"initialized\":false,\"libraryPresent\":false," +
            "\"name\":\"unavailable\",\"serial\":\"-\",\"buildId\":\"-\"," +
            "\"status\":\"Native library not loaded: ${loadFailure ?: "unknown"}\"}}"

    private external fun nativeStart(surface: Surface)
    private external fun nativeStop()
    private external fun nativeSetPaused(paused: Boolean)
    private external fun nativeSetPad(
        buttons: Int,
        leftX: Float,
        leftY: Float,
        rightX: Float,
        rightY: Float
    )
    private external fun nativeConfigure(
        resolutionScalePercent: Int,
        linearFiltering: Boolean,
        audioEnabled: Boolean,
        volume: Float
    )
    private external fun nativeLoadGame(directory: String): Boolean
    private external fun nativeUnloadGame()
    private external fun nativeStatus(): String
    private external fun nativeCoreStatus(): String
    private external fun nativeSetEnvironment(name: String, value: String): Boolean
}
