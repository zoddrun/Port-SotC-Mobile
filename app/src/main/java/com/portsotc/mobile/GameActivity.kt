package com.portsotc.mobile

import android.app.Activity
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.portsotc.mobile.core.InstalledGame
import com.portsotc.mobile.core.Settings
import com.portsotc.mobile.input.ControllerMapping
import com.portsotc.mobile.input.GamepadManager
import com.portsotc.mobile.input.PadState
import com.portsotc.mobile.input.Ps2Buttons
import com.portsotc.mobile.input.TouchControllerView
import com.portsotc.mobile.nativebridge.NativeCore
import com.portsotc.mobile.nativebridge.NativeSurfaceView
import org.json.JSONObject

class GameActivity : Activity(), NativeSurfaceView.Callback, InputManager.InputDeviceListener {

    companion object {
        private const val EXTRA_DIAGNOSTICS = "com.portsotc.mobile.extra.DIAGNOSTICS"
        private const val PAD_POLL_INTERVAL_MS = 16L
        private const val STATISTICS_INTERVAL_MS = 500L
        private const val LOG_TAG = "SotcInput"

        fun diagnosticsIntent(context: android.content.Context): Intent =
            Intent(context, GameActivity::class.java).putExtra(EXTRA_DIAGNOSTICS, true)

        fun gameIntent(context: android.content.Context): Intent =
            Intent(context, GameActivity::class.java).putExtra(EXTRA_DIAGNOSTICS, false)
    }

    private lateinit var app: SotcApplication
    private lateinit var settings: Settings
    private lateinit var padState: PadState
    private lateinit var gamepadManager: GamepadManager
    private lateinit var nativeSurface: NativeSurfaceView
    private lateinit var touchController: TouchControllerView
    private lateinit var banner: TextView
    private lateinit var statistics: TextView
    private lateinit var pausePanel: LinearLayout
    private lateinit var pauseDetail: TextView
    private lateinit var pauseButton: ImageButton

    private var game: InstalledGame? = null
    private var diagnosticsMode = false
    private var surfaceActive = false
    private var activityPaused = false
    private var menuPaused = false
    private var gamepadConnected = false
    private var inputManager: InputManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private var padLoopRunning = false
    private var lastLoggedPad: PadState.Snapshot? = null

    private val padRunnable = object : Runnable {
        override fun run() {
            if (!padLoopRunning) return
            val snapshot = padState.snapshot()
            NativeCore.setPad(
                snapshot.buttons,
                snapshot.leftX,
                snapshot.leftY,
                snapshot.rightX,
                snapshot.rightY
            )
            logPadChanges(snapshot)
            handler.postDelayed(this, PAD_POLL_INTERVAL_MS)
        }
    }

    private val statisticsRunnable = object : Runnable {
        override fun run() {
            updateStatistics()
            handler.postDelayed(this, STATISTICS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as SotcApplication
        settings = app.settingsStore.load()
        diagnosticsMode = intent.getBooleanExtra(EXTRA_DIAGNOSTICS, false)
        game = if (diagnosticsMode) null else app.library.primaryGame()

        if (!diagnosticsMode && game == null) {
            finish()
            return
        }

        setContentView(R.layout.activity_game)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        padState = PadState()
        inputManager = getSystemService(INPUT_SERVICE) as? InputManager
        gamepadManager = GamepadManager(
            this,
            padState,
            ControllerMapping(this)
        ) { app.settingsStore.load() }

        nativeSurface = findViewById(R.id.native_surface)
        touchController = findViewById(R.id.touch_controller)
        banner = findViewById(R.id.game_banner)
        statistics = findViewById(R.id.game_stats)
        pausePanel = findViewById(R.id.pause_panel)
        pauseDetail = findViewById(R.id.pause_detail)
        pauseButton = findViewById(R.id.button_pause)

        nativeSurface.callback = this
        touchController.padState = padState
        touchController.listener = null

        findViewById<Button>(R.id.button_resume).setOnClickListener { setMenuPaused(false) }
        findViewById<Button>(R.id.button_pause_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.button_pause_controller).setOnClickListener {
            startActivity(Intent(this, ControllerSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.button_quit).setOnClickListener { finish() }
        pauseButton.setOnClickListener { setMenuPaused(true) }

        NativeCore.configure(settings)
        val installed = game
        if (installed != null) {
            NativeCore.loadGame(installed.directory.absolutePath)
        }
        applySettings()
        updateBanner()
        updatePauseDetail()
    }

    override fun onResume() {
        super.onResume()
        activityPaused = false
        settings = app.settingsStore.load()
        NativeCore.configure(settings)
        inputManager?.registerInputDeviceListener(this, handler)
        refreshGamepadPresence()
        applySettings()
        applyPause()
        startPadLoop()
        updateBanner()
        updatePauseDetail()
        handler.removeCallbacks(statisticsRunnable)
        handler.post(statisticsRunnable)
    }

    override fun onPause() {
        activityPaused = true
        applyPause()
        stopPadLoop()
        handler.removeCallbacks(statisticsRunnable)
        inputManager?.unregisterInputDeviceListener(this)
        touchController.resetInput()
        gamepadManager.clear()
        padState.clearAll()
        super.onPause()
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshGamepadPresence()

    override fun onInputDeviceRemoved(deviceId: Int) = refreshGamepadPresence()

    override fun onInputDeviceChanged(deviceId: Int) = refreshGamepadPresence()

    /**
     * Tracks whether a physical controller is attached so the on-screen pad can
     * step aside when the player prefers it hidden. The listener also handles
     * hot-plugging, which a one-time check at startup would miss.
     */
    private fun refreshGamepadPresence() {
        val connected = gamepadManager.connectedControllers().isNotEmpty()
        if (connected == gamepadConnected) return
        gamepadConnected = connected
        applySettings()
    }

    override fun onDestroy() {
        stopPadLoop()
        handler.removeCallbacksAndMessages(null)
        NativeCore.stop()
        if (isFinishing) {
            NativeCore.unloadGame()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    override fun onNativeSurfaceCreated(surface: android.view.Surface) {
        surfaceActive = true
        NativeCore.configure(app.settingsStore.load())
        NativeCore.start(surface)
        applyPause()
        updateBanner()
    }

    override fun onNativeSurfaceDestroyed() {
        surfaceActive = false
        NativeCore.stop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepadManager.handleKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadManager.handleGenericMotion(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun setMenuPaused(paused: Boolean) {
        menuPaused = paused
        pausePanel.visibility = if (paused) View.VISIBLE else View.GONE
        pauseButton.visibility = if (paused) View.GONE else View.VISIBLE
        applyPause()
        if (paused) {
            updatePauseDetail()
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "Pause panel: ${pauseDetail.text.toString().replace('\n', '|')}")
            }
        }
    }

    /**
     * Debug builds log pad transitions so touch and gamepad input can be
     * verified from adb even when the UI hierarchy cannot be dumped while the
     * render surface is active.
     */
    private fun logPadChanges(snapshot: PadState.Snapshot) {
        if (!BuildConfig.DEBUG) return
        val previous = lastLoggedPad
        val changed = previous == null ||
            previous.buttons != snapshot.buttons ||
            java.lang.Math.abs(previous.leftX - snapshot.leftX) > 0.05f ||
            java.lang.Math.abs(previous.leftY - snapshot.leftY) > 0.05f ||
            java.lang.Math.abs(previous.rightX - snapshot.rightX) > 0.05f ||
            java.lang.Math.abs(previous.rightY - snapshot.rightY) > 0.05f
        if (!changed) return
        lastLoggedPad = snapshot
        Log.d(
            LOG_TAG,
            String.format(
                java.util.Locale.US,
                "Pad %s L(%.2f, %.2f) R(%.2f, %.2f)",
                Ps2Buttons.describe(snapshot.buttons),
                snapshot.leftX,
                snapshot.leftY,
                snapshot.rightX,
                snapshot.rightY
            )
        )
    }

    private fun applyPause() {
        NativeCore.setPaused(activityPaused || menuPaused)
    }

    private fun applySettings() {
        val layout = app.touchLayoutStore.load()
        touchController.layout = layout
        touchController.globalAlpha = settings.touchAlpha
        touchController.globalScale = settings.touchScale
        touchController.lookSensitivity = settings.cameraSensitivity
        touchController.invertLookX = settings.invertLookX
        touchController.invertLookY = settings.invertLookY
        touchController.stickDeadzone = settings.touchDeadzone
        val showTouch = settings.touchControlsEnabled &&
            !(settings.hideTouchWithGamepad && gamepadConnected)
        touchController.controlsVisible = showTouch
        touchController.visibility = if (showTouch) View.VISIBLE else View.GONE
        statistics.visibility = if (settings.showStatistics) View.VISIBLE else View.GONE
    }

    private fun startPadLoop() {
        if (padLoopRunning) return
        padLoopRunning = true
        handler.post(padRunnable)
    }

    private fun stopPadLoop() {
        padLoopRunning = false
        handler.removeCallbacks(padRunnable)
    }

    private fun updateBanner() {
        val coreReady = coreInitialized()
        if (diagnosticsMode) {
            banner.visibility = View.VISIBLE
            banner.text = "ENGINE DIAGNOSTICS · NOT GAME CONTENT"
            return
        }
        if (coreReady) {
            banner.text = "NATIVE CORE · ${game?.serial ?: "SCUS-97472"}"
            banner.visibility = if (settings.diagnosticsOverlay) View.VISIBLE else View.GONE
        } else {
            banner.visibility = View.VISIBLE
            banner.text = "NATIVE GAME CORE NOT BUILT · DIAGNOSTICS ONLY"
        }
    }

    private fun coreInitialized(): Boolean = try {
        JSONObject(NativeCore.statusJson())
            .optJSONObject("core")
            ?.optBoolean("initialized") == true
    } catch (_: Exception) {
        false
    }

    private fun updateStatistics() {
        if (!settings.showStatistics) {
            statistics.visibility = View.GONE
            return
        }
        statistics.visibility = View.VISIBLE
        val status = try {
            JSONObject(NativeCore.statusJson())
        } catch (_: Exception) {
            statistics.text = "ENGINE STATUS UNAVAILABLE"
            return
        }
        val core = status.optJSONObject("core")
        val coreReady = core?.optBoolean("initialized") == true
        statistics.text = buildString {
            append("HOST FPS ")
            append(String.format(java.util.Locale.US, "%.1f", status.optDouble("fps", 0.0)))
            append(" · ")
            append(String.format(java.util.Locale.US, "%.2f ms", status.optDouble("frameMs", 0.0)))
            append(" · ")
            append(status.optInt("width", 0))
            append("×")
            append(status.optInt("height", 0))
            append(" · ")
            append(status.optInt("scalePercent", 100))
            append("%")
            if (coreReady) {
                append(" · CORE")
            } else {
                append(" · DIAG")
            }
        }
    }

    private fun updatePauseDetail() {
        val status = try {
            JSONObject(NativeCore.statusJson())
        } catch (_: Exception) {
            null
        }
        val core = status?.optJSONObject("core")
        val coreReady = core?.optBoolean("initialized") == true
        val snapshot = padState.snapshot()
        pauseDetail.text = buildString {
            appendLine("ENGINE ${if (status?.optBoolean("running") == true) "RUNNING" else "STOPPED"}")
            appendLine("RENDER ${if (coreReady) "NATIVE GAME CORE" else "DIAGNOSTIC SCENE"}")
            appendLine("PAD    ${Ps2Buttons.describe(snapshot.buttons)}")
            if (!coreReady) {
                appendLine()
                append(NativeCore.coreStatus())
            }
        }
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
