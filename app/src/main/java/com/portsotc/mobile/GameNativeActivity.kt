package com.portsotc.mobile

import android.app.NativeActivity
import android.os.Bundle
import android.util.Log
import com.portsotc.mobile.core.RuntimeEnvironment
import com.portsotc.mobile.nativebridge.NativeCore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hosts the recompiled game runtime.
 *
 * The runtime is a raylib-owned `NativeActivity` library: raylib creates the
 * window, runs the frame loop, polls input, and drives audio, so gameplay is
 * delivered through the runtime's own entry point (D18) instead of the
 * ABI-v1 step/pull contract used by the diagnostics host.
 *
 * Android starts a NativeActivity without argv, so the boot executable path and
 * the control settings are published to the process environment before
 * `super.onCreate` loads the runner library. The launcher only starts this
 * activity when the runner library is packaged and the extracted disc tree is
 * ready, so the checks here are defensive: a missing prerequisite is logged and
 * the runtime reports the failure itself instead of the host pretending to have
 * a session.
 *
 * `super.onCreate` must always be reached: the framework raises
 * `SuperNotCalledException` when an activity returns from `onCreate` without
 * calling it.
 */
class GameNativeActivity : NativeActivity() {

    companion object {
        private const val TAG = "GameNativeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as SotcApplication

        // The runtime reports progress and failures through stdio, and Android
        // discards the stdio of a framework-started app. Redirect it to a file
        // before anything loads the runner, so the first boot leaves a trace.
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val logFile = File(app.paths.logsRoot, "game-$stamp.log")
        if (NativeCore.redirectStdio(logFile)) {
            Log.i(TAG, "Runtime output: ${logFile.absolutePath}")
        } else {
            Log.e(TAG, "Unable to redirect the runtime output to ${logFile.absolutePath}")
        }

        val game = app.library.primaryGame()
        val bootPath = game
            ?.takeIf { it.isDiscTreeReady() }
            ?.bootElfFile
            ?.absolutePath

        if (bootPath == null) {
            Log.e(TAG, "No installed game with a prepared disc tree; the runtime has no boot path")
        }

        // The touch overlay, look knobs, audio volume and graphics/pacing
        // values are read once, at the start of the session (the volume right
        // after the runtime loads its own settings); a settings change applies
        // to the next session.
        val settings = app.settingsStore.load()
        val values = RuntimeEnvironment.variables(bootPath, settings)
        val failed = RuntimeEnvironment.publish(values) { name, value ->
            NativeCore.setEnvironment(name, value)
        }
        val published = values.keys - failed.toSet()
        if (RuntimeEnvironment.BOOT_PATH in published) {
            Log.i(TAG, "Published ${RuntimeEnvironment.BOOT_PATH}=$bootPath")
        }
        Log.i(TAG, "Published runtime settings: ${published.sorted().joinToString(", ")}")
        for (name in failed) {
            Log.e(TAG, "Unable to publish $name; the runtime falls back to its default")
        }

        super.onCreate(savedInstanceState)
    }
}
