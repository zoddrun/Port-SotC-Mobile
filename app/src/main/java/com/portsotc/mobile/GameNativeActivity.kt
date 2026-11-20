package com.portsotc.mobile

import android.app.NativeActivity
import android.hardware.input.InputManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
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
 * The floating quick settings button is a host-side Android view. It opens the
 * same settings activity as the diagnostics host and never claims that an
 * already-running runner re-reads the changed environment values.
 */
class GameNativeActivity : NativeActivity() {

    companion object {
        private const val TAG = "GameNativeActivity"
        private const val REQUEST_QUICK_SETTINGS = 4202
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
        val packVersion = try {
            app.shaderPackInstaller.readManifest().version
        } catch (_: Throwable) {
            "unknown"
        }
        val shaderValues = RuntimeEnvironment.hdShader(
            app.installedShaderPackDirectory(),
            settings.hdShaderPreset,
            packVersion
        )
        val values = RuntimeEnvironment.variables(bootPath, settings, shaderValues)
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
        installQuickSettingsButton()
    }

    /**
     * Adds a small host button after the native window exists. The button is a
     * direct child of the decor view, so empty screen areas still reach the
     * runner's own touch handling untouched.
     */
    private fun installQuickSettingsButton() {
        window.decorView.post {
            val decor = window.decorView as? ViewGroup ?: return@post
            val button = Button(this).apply {
                text = "SETTINGS"
                isAllCaps = false
                textSize = 11f
                setBackgroundColor(0xB311151B.toInt())
                setTextColor(0xFFC6B282.toInt())
                setOnClickListener {
                    @Suppress("DEPRECATION")
                    startActivityForResult(
                        QuickSettingsActivity.intent(
                            this@GameNativeActivity,
                            gamepadConnected(),
                            false,
                            (application as SotcApplication).shaderPackReady,
                            (application as SotcApplication).shaderPackDetail
                        ),
                        REQUEST_QUICK_SETTINGS
                    )
                }
            }
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = 24
                rightMargin = 28
            }
            try {
                decor.addView(button, params)
            } catch (error: Throwable) {
                Log.w(TAG, "Unable to add the host quick settings button: ${error.message}")
            }
        }
    }

    private fun gamepadConnected(): Boolean = try {
        val manager = getSystemService(INPUT_SERVICE) as? InputManager
        if (manager == null) {
            false
        } else {
            manager.inputDeviceIds.any { id ->
                val device = manager.getInputDevice(id) ?: return@any false
                device.sources and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0
            }
        }
    } catch (_: Throwable) {
        false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_QUICK_SETTINGS || resultCode != RESULT_OK || data == null) return
        if (data.getBooleanExtra(QuickSettingsActivity.EXTRA_EXIT, false)) {
            finish()
        }
    }
}
