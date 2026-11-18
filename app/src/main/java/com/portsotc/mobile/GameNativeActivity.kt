package com.portsotc.mobile

import android.app.NativeActivity
import android.os.Bundle
import android.util.Log
import com.portsotc.mobile.nativebridge.NativeCore

/**
 * Hosts the recompiled game runtime.
 *
 * The runtime is a raylib-owned `NativeActivity` library: raylib creates the
 * window, runs the frame loop, polls input, and drives audio, so gameplay is
 * delivered through the runtime's own entry point (D18) instead of the
 * ABI-v1 step/pull contract used by the diagnostics host.
 *
 * Android starts a NativeActivity without argv, so the boot executable path is
 * published to the process environment before `super.onCreate` loads the
 * runner library. The launcher only starts this activity when the runner
 * library is packaged and the extracted disc tree is ready, so the checks here
 * are defensive: a missing prerequisite is logged and the runtime reports the
 * failure itself instead of the host pretending to have a session.
 *
 * `super.onCreate` must always be reached: the framework raises
 * `SuperNotCalledException` when an activity returns from `onCreate` without
 * calling it.
 */
class GameNativeActivity : NativeActivity() {

    companion object {
        private const val TAG = "GameNativeActivity"

        /** Read by the recompiled runtime before it loads the guest executable. */
        const val BOOT_PATH_VARIABLE = "SOTC_BOOT_ELF"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as SotcApplication
        val game = app.library.primaryGame()
        val bootPath = game
            ?.takeIf { it.isDiscTreeReady() }
            ?.bootElfFile
            ?.absolutePath

        if (bootPath == null) {
            Log.e(TAG, "No installed game with a prepared disc tree; the runtime has no boot path")
        } else if (NativeCore.setEnvironment(BOOT_PATH_VARIABLE, bootPath)) {
            Log.i(TAG, "Published $BOOT_PATH_VARIABLE=$bootPath")
        } else {
            Log.e(TAG, "Unable to publish $BOOT_PATH_VARIABLE; the runtime cannot boot")
        }

        super.onCreate(savedInstanceState)
    }
}
