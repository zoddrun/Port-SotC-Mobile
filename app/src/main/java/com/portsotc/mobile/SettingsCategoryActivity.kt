package com.portsotc.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.portsotc.mobile.core.CoreProbe
import com.portsotc.mobile.core.DiscTree
import com.portsotc.mobile.core.DiscTreeExtractor
import com.portsotc.mobile.core.GameImporter
import com.portsotc.mobile.core.GameLogs
import com.portsotc.mobile.core.GraphicsPreset
import com.portsotc.mobile.core.InstalledGame
import com.portsotc.mobile.core.Settings
import com.portsotc.mobile.core.TouchPreset
import com.portsotc.mobile.input.TouchLayout
import com.portsotc.mobile.nativebridge.NativeCore
import com.portsotc.mobile.ui.Ui
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** One settings category. Kept separate so no screen becomes a wall of options. */
class SettingsCategoryActivity : Activity() {

    companion object {
        private const val EXTRA_CATEGORY = "com.portsotc.mobile.extra.SETTINGS_CATEGORY"

        fun intent(context: Context, category: SettingsCategory): Intent =
            Intent(context, SettingsCategoryActivity::class.java)
                .putExtra(EXTRA_CATEGORY, category.key)
    }

    private lateinit var app: SotcApplication
    private lateinit var settings: Settings
    private lateinit var container: LinearLayout
    private lateinit var category: SettingsCategory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        app = application as SotcApplication
        category = SettingsCategory.fromKey(intent.getStringExtra(EXTRA_CATEGORY))
        settings = app.settingsStore.load()
        container = findViewById(R.id.settings_container)
        buildUi()
    }

    private fun buildUi() {
        container.removeAllViews()
        container.addView(Ui.title(this, category.title.uppercase(Locale.US)))
        container.addView(Ui.caption(this, category.summary))

        when (category) {
            SettingsCategory.GENERAL -> buildGeneral()
            SettingsCategory.GRAPHICS -> buildGraphics()
            SettingsCategory.PERFORMANCE -> buildPerformance()
            SettingsCategory.AUDIO -> buildAudio()
            SettingsCategory.CONTROLS -> buildControls()
            SettingsCategory.STORAGE -> buildStorage()
            SettingsCategory.ADVANCED -> buildAdvanced()
        }
    }

    // ---------------------------------------------------------------- General

    private fun buildGeneral() {
        container.addView(Ui.section(this, "APPLICATION"))
        val application = Ui.card(this)
        application.addView(Ui.mono(this, CoreProbe.buildSummary(this)))
        application.addView(
            Ui.caption(
                this,
                "Application ID com.portsotc.mobile · target game SCUS-97472 (USA retail)."
            )
        )
        container.addView(application)

        container.addView(Ui.section(this, "INTERFACE"))
        val interfaceCard = Ui.card(this)
        Ui.addToggle(
            parent = interfaceCard,
            context = this,
            label = "Touch controls",
            description = "Shows the on-screen controller during play. " +
                "Gameplay uses landscape orientation and immersive mode.",
            initial = settings.touchControlsEnabled
        ) { checked -> applySettings { it.copy(touchControlsEnabled = checked) } }
        container.addView(interfaceCard)

        addResetButton()
    }

    // --------------------------------------------------------------- Graphics

    private fun buildGraphics() {
        container.addView(Ui.section(this, "RENDERING"))
        val rendering = Ui.card(this)
        Ui.addSlider(
            parent = rendering,
            context = this,
            label = "INTERNAL RESOLUTION",
            min = 25,
            max = 100,
            initial = settings.resolutionScalePercent,
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(resolutionScalePercent = value) } }
        )
        Ui.addToggle(
            parent = rendering,
            context = this,
            label = "Linear filtering",
            description = "Smooth the upscaled image. Disable for a sharper, pixel-like result.",
            initial = settings.linearFiltering
        ) { checked -> applySettings { it.copy(linearFiltering = checked) } }
        Ui.addToggle(
            parent = rendering,
            context = this,
            label = "Adaptive resolution",
            description = "Lowers the internal resolution when frames miss their time budget and " +
                "restores it when there is headroom. The slider above is the ceiling.",
            initial = settings.adaptiveResolution
        ) { checked -> applySettings { it.copy(adaptiveResolution = checked) } }
        rendering.addView(
            Ui.caption(
                this,
                "These apply to the native engine view. It renders into an offscreen target at " +
                    "the selected scale, then presents it. The game runtime uses its own " +
                    "presentation, with fit, stretch and integer modes in its settings menu."
            )
        )
        container.addView(rendering)

        container.addView(Ui.section(this, "BACKEND"))
        val backend = Ui.card(this)
        backend.addView(Ui.mono(this, glesSummary()))
        backend.addView(gpuReportView(), params(topMarginDp = 12f))
        Ui.addToggle(
            parent = backend,
            context = this,
            label = "Force OpenGL ES 2.0",
            description = "Uses the ES 2 compatibility path at the next session start. The game " +
                "runtime already renders through ES 2; this covers the engine view as well.",
            initial = settings.forceEs2
        ) { checked -> applySettings { it.copy(forceEs2 = checked) } }
        backend.addView(
            Ui.caption(
                this,
                "The native host requests OpenGL ES 3 first and falls back to ES 2 automatically " +
                    "when the driver rejects it. A Vulkan backend is not implemented in this build."
            )
        )
        container.addView(backend)
    }

    // ------------------------------------------------------------ Performance

    private fun buildPerformance() {
        container.addView(Ui.section(this, "OVERLAY"))
        val overlay = Ui.card(this)
        Ui.addToggle(
            parent = overlay,
            context = this,
            label = "Performance overlay",
            description = "Off by default. Shows host rendering statistics (frame rate and " +
                "frame time). It is not a measurement of game frame rate.",
            initial = settings.showStatistics
        ) { checked -> applySettings { it.copy(showStatistics = checked) } }
        Ui.addToggle(
            parent = overlay,
            context = this,
            label = "Engine state banner",
            description = "Developer overlay showing the runtime state during a session.",
            initial = settings.diagnosticsOverlay
        ) { checked -> applySettings { it.copy(diagnosticsOverlay = checked) } }
        container.addView(overlay)

        container.addView(Ui.section(this, "FRAME PACING"))
        val pacing = Ui.card(this)
        val uncapped = Ui.secondaryButton(this, "UNCAPPED")
        uncapped.setOnClickListener { applyFrameLimit(0) }
        val sixty = Ui.secondaryButton(this, "60 FPS")
        sixty.setOnClickListener { applyFrameLimit(60) }
        Ui.addButtonRow(pacing, this, uncapped, sixty)
        val thirty = Ui.secondaryButton(this, "30 FPS")
        thirty.setOnClickListener { applyFrameLimit(30) }
        pacing.addView(thirty, params(topMarginDp = 10f))
        pacing.addView(
            Ui.mono(this, "CURRENT · ${frameLimitLabel(settings.frameLimitFps)}"),
            params(topMarginDp = 14f)
        )
        pacing.addView(
            Ui.caption(
                this,
                "The limit paces presentation and keeps the simulation on wall-clock time, so " +
                    "game logic is not sped up or slowed down. It applies to the native engine " +
                    "view; the game runtime owns its own frame loop."
            )
        )
        container.addView(pacing)

        container.addView(Ui.section(this, "PRESETS"))
        val presets = Ui.card(this)
        presets.addView(
            Ui.caption(
                this,
                "Presets set render scale, adaptive resolution and the frame limit together. " +
                    "They never change gameplay behavior mid-session."
            )
        )
        val battery = Ui.secondaryButton(this, "BATTERY SAVER")
        battery.setOnClickListener { applyPreset(GraphicsPreset.BATTERY) }
        val balanced = Ui.secondaryButton(this, "BALANCED")
        balanced.setOnClickListener { applyPreset(GraphicsPreset.BALANCED) }
        Ui.addButtonRow(presets, this, battery, balanced)
        val performance = Ui.secondaryButton(this, "PERFORMANCE")
        performance.setOnClickListener { applyPreset(GraphicsPreset.PERFORMANCE) }
        presets.addView(performance, params(topMarginDp = 10f))
        val active = GraphicsPreset.matching(settings)?.label?.uppercase(Locale.US) ?: "CUSTOM"
        presets.addView(
            Ui.mono(
                this,
                "CURRENT · $active · ${settings.resolutionScalePercent}% · " +
                    frameLimitLabel(settings.frameLimitFps)
            ),
            params(topMarginDp = 14f)
        )
        container.addView(presets)

        container.addView(
            Ui.caption(
                this,
                "Thermal pressure is handled automatically: as Android reports rising thermal " +
                    "status, the native host lowers its render scale ceiling without changing " +
                    "your saved settings."
            )
        )
    }

    // ------------------------------------------------------------------ Audio

    private fun buildAudio() {
        container.addView(Ui.section(this, "OUTPUT"))
        val audio = Ui.card(this)
        Ui.addToggle(
            parent = audio,
            context = this,
            label = "Game audio",
            description = "Turns audio on or off for the engine view and for the game runtime.",
            initial = settings.audioEnabled
        ) { checked -> applySettings { it.copy(audioEnabled = checked) } }
        Ui.addSlider(
            parent = audio,
            context = this,
            label = "MASTER VOLUME",
            min = 0,
            max = 100,
            initial = (settings.audioVolume * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(audioVolume = value / 100f) } }
        )
        audio.addView(
            Ui.caption(
                this,
                "The game runtime applies this volume when a session starts. The runtime's " +
                    "own settings menu can adjust it for the current session."
            )
        )
        container.addView(audio)

        container.addView(Ui.section(this, "DEVICE"))
        val device = Ui.card(this)
        device.addView(Ui.mono(this, audioSummary()))
        device.addView(
            Ui.caption(
                this,
                "Music and sound-effect levels are controlled by the game itself and are not " +
                    "exposed separately yet."
            )
        )
        container.addView(device)
    }

    // --------------------------------------------------------------- Controls

    private fun buildControls() {
        container.addView(Ui.section(this, "TOUCH PRESET"))
        val presets = Ui.card(this)
        presets.addView(
            Ui.caption(
                this,
                "Classic follows the original controller layout. Modern favors phone " +
                    "ergonomics. Compact reduces screen obstruction. Custom keeps your edited layout."
            )
        )
        val classic = Ui.secondaryButton(this, "CLASSIC")
        classic.setOnClickListener { applyTouchPreset(TouchPreset.CLASSIC) }
        val modern = Ui.secondaryButton(this, "MODERN")
        modern.setOnClickListener { applyTouchPreset(TouchPreset.MODERN) }
        Ui.addButtonRow(presets, this, classic, modern)
        val compact = Ui.secondaryButton(this, "COMPACT")
        compact.setOnClickListener { applyTouchPreset(TouchPreset.COMPACT) }
        val custom = Ui.secondaryButton(this, "CUSTOM")
        custom.setOnClickListener { applyTouchPreset(TouchPreset.CUSTOM) }
        Ui.addButtonRow(presets, this, compact, custom)
        presets.addView(
            Ui.mono(this, "ACTIVE · ${TouchPreset.fromId(settings.touchPreset).label.uppercase(Locale.US)}"),
            params(topMarginDp = 14f)
        )
        container.addView(presets)

        container.addView(Ui.section(this, "TOUCH APPEARANCE"))
        val appearance = Ui.card(this)
        Ui.addSlider(
            parent = appearance,
            context = this,
            label = "TOUCH SIZE",
            min = 60,
            max = 160,
            initial = (settings.touchScale * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(touchScale = value / 100f) } }
        )
        Ui.addSlider(
            parent = appearance,
            context = this,
            label = "TOUCH OPACITY",
            min = 15,
            max = 100,
            initial = (settings.touchAlpha * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(touchAlpha = value / 100f) } }
        )
        Ui.addToggle(
            parent = appearance,
            context = this,
            label = "Hide touch controls with a controller",
            description = "Hides the virtual controller while a physical gamepad is connected.",
            initial = settings.hideTouchWithGamepad
        ) { checked -> applySettings { it.copy(hideTouchWithGamepad = checked) } }
        container.addView(appearance)

        container.addView(Ui.section(this, "CAMERA"))
        val camera = Ui.card(this)
        Ui.addSlider(
            parent = camera,
            context = this,
            label = "CAMERA SENSITIVITY",
            min = 40,
            max = 200,
            initial = (settings.cameraSensitivity * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(cameraSensitivity = value / 100f) } }
        )
        Ui.addSlider(
            parent = camera,
            context = this,
            label = "TOUCH DEADZONE",
            min = 0,
            max = 30,
            initial = (settings.touchDeadzone * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(touchDeadzone = value / 100f) } }
        )
        Ui.addToggle(
            parent = camera,
            context = this,
            label = "Invert horizontal look",
            description = "Inverts the horizontal axis of the touch camera stick.",
            initial = settings.invertLookX
        ) { checked -> applySettings { it.copy(invertLookX = checked) } }
        Ui.addToggle(
            parent = camera,
            context = this,
            label = "Invert vertical look",
            description = "Inverts the vertical axis of the touch and physical right stick.",
            initial = settings.invertLookY
        ) { checked -> applySettings { it.copy(invertLookY = checked) } }
        container.addView(camera)

        val edit = Ui.primaryButton(this, "EDIT TOUCH LAYOUT")
        edit.setOnClickListener {
            startActivity(Intent(this, TouchLayoutEditorActivity::class.java))
        }
        container.addView(edit, params(topMarginDp = 20f))
        val physical = Ui.secondaryButton(this, "PHYSICAL CONTROLLER SETTINGS")
        physical.setOnClickListener {
            startActivity(Intent(this, ControllerSettingsActivity::class.java))
        }
        container.addView(physical, params(topMarginDp = 10f))
    }

    // ---------------------------------------------------------------- Storage

    private fun buildStorage() {
        val game = app.library.primaryGame()
        container.addView(Ui.section(this, "GAME DATA"))
        val info = Ui.card(this)
        if (game == null) {
            info.addView(Ui.body(this, "No game data imported."))
            info.addView(
                Ui.caption(
                    this,
                    "Import your own legally obtained Shadow of the Colossus (USA, SCUS-97472) " +
                        "disc image to validate and store it."
                )
            )
            container.addView(info)
            val openImport = Ui.primaryButton(this, "OPEN IMPORT")
            openImport.setOnClickListener {
                startActivity(Intent(this, ImportActivity::class.java))
            }
            container.addView(openImport, params(topMarginDp = 16f))
            return
        }

        val importedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .format(Date(game.importedAt))
        val tree = DiscTree.read(game.directory)
        val treeReady = game.isDiscTreeReady()
        info.addView(
            Ui.mono(
                this,
                buildString {
                    appendLine("SERIAL     ${game.serial}")
                    appendLine("TITLE      ${game.title}")
                    appendLine("BOOT       ${game.bootFile}")
                    appendLine("IMAGE      ${GameImporter.formatBytes(game.imageSize)}")
                    appendLine("MODULES    ${game.modules.joinToString(", ")}")
                    appendLine("IMPORTED   $importedAt")
                    appendLine("SHA-256    ${game.sha256}")
                    appendLine(
                        "DISC TREE  " + if (treeReady && tree != null) {
                            "ready · ${tree.fileCount} files · ${GameImporter.formatBytes(tree.byteCount)}"
                        } else {
                            "not prepared"
                        }
                    )
                    append("SAVES      ${GameImporter.formatBytes(directorySize(game.saveDirectory))}")
                }
            )
        )
        container.addView(info)

        container.addView(Ui.section(this, "RUNTIME DATA"))
        val runtimeData = Ui.card(this)
        runtimeData.addView(
            Ui.caption(
                this,
                "The native runtime reads game files from an extracted directory tree, not from " +
                    "the ISO directly. Preparing game data extracts the disc once into app " +
                    "storage; it must be done before the first launch and is repeated only when " +
                    "a different image is imported."
            )
        )
        runtimeData.addView(
            Ui.mono(
                this,
                buildString {
                    appendLine("TARGET     ${game.discDirectory.absolutePath}")
                    appendLine("BOOT ELF   ${game.bootElfFile.absolutePath}")
                    append("MEMORY CARD ${game.memoryCardDirectory.absolutePath}")
                }
            ),
            params(topMarginDp = 12f)
        )
        container.addView(runtimeData)

        val prepare = Ui.primaryButton(
            this,
            if (treeReady) "RE-EXTRACT DISC DATA" else "PREPARE GAME DATA"
        )
        prepare.setOnClickListener { prepareDiscTree(game) }
        container.addView(prepare, params(topMarginDp = 16f))
        container.addView(
            Ui.caption(
                this,
                "Extraction needs roughly the size of the disc image again in free storage " +
                    "(${GameImporter.formatBytes(game.imageSize)})."
            )
        )

        container.addView(Ui.section(this, "STORAGE USE"))
        val usage = Ui.card(this)
        usage.addView(
            Ui.mono(
                this,
                buildString {
                    appendLine("GAME DATA  ${GameImporter.formatBytes(directorySize(game.directory))}")
                    append("APP FILES  ${GameImporter.formatBytes(directorySize(app.paths.filesRoot))}")
                }
            )
        )
        container.addView(usage)

        val verify = Ui.secondaryButton(this, "VERIFY GAME FILES")
        verify.setOnClickListener { verifyGameFiles(game) }
        container.addView(verify, params(topMarginDp = 18f))

        val openImport = Ui.secondaryButton(this, "IMPORT ANOTHER IMAGE")
        openImport.setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }
        container.addView(openImport, params(topMarginDp = 10f))

        val remove = Ui.secondaryButton(this, "REMOVE GAME DATA")
        remove.setOnClickListener { confirmRemove(game) }
        container.addView(remove, params(topMarginDp = 10f))

        container.addView(
            Ui.caption(
                this,
                "Removing game data deletes the app-private copy, extracted modules, the " +
                    "extracted disc tree, and saves stored with it. The original file you " +
                    "selected with the system picker is never modified."
            )
        )
    }

    // --------------------------------------------------------------- Advanced

    private fun buildAdvanced() {
        container.addView(Ui.section(this, "RUNTIME"))
        val runtime = Ui.card(this)
        runtime.addView(
            Ui.mono(
                this,
                buildString {
                    appendLine("APP        ${CoreProbe.buildSummary(this@SettingsCategoryActivity)}")
                    appendLine("ABI        ${CoreProbe.expectedAbi()}")
                    appendLine("NATIVE     ${if (NativeCore.available) "LOADED" else "UNAVAILABLE"}")
                    appendLine("CORE       ${if (CoreProbe.isInstalled(this@SettingsCategoryActivity)) "PRESENT" else "NOT BUILT"}")
                    appendLine("GAME       ${app.library.primaryGame()?.serial ?: "NOT IMPORTED"}")
                    append("STORAGE    ${app.paths.filesRoot.absolutePath}")
                }
            )
        )
        if (!NativeCore.available) {
            runtime.addView(Ui.caption(this, "Load error: ${NativeCore.loadError ?: "unknown"}"))
        }
        container.addView(runtime)

        container.addView(Ui.section(this, "CAPABILITIES"))
        val capabilities = Ui.card(this)
        capabilities.addView(Ui.mono(this, "${glesSummary()}\n${audioSummary()}"))
        capabilities.addView(gpuReportView(), params(topMarginDp = 12f))
        container.addView(capabilities)

        container.addView(Ui.section(this, "DIAGNOSTICS"))
        val diagnostics = Ui.card(this)
        diagnostics.addView(
            Ui.caption(
                this,
                "The engine diagnostics screen exercises the native host: EGL initialization, " +
                    "AAudio output, and the input path. It never renders game content."
            )
        )
        val open = Ui.primaryButton(this, "OPEN ENGINE DIAGNOSTICS")
        open.setOnClickListener {
            startActivity(GameActivity.diagnosticsIntent(this))
        }
        diagnostics.addView(open, params(topMarginDp = 14f))
        val copy = Ui.secondaryButton(this, "COPY DIAGNOSTIC REPORT")
        copy.setOnClickListener { copyDiagnosticReport() }
        diagnostics.addView(copy, params(topMarginDp = 10f))
        container.addView(diagnostics)

        addGameLogSection()

        addResetButton()
    }

    // ---------------------------------------------------------------- Helpers

    /**
     * The runtime's own console output, one file per launch. It is the first
     * place to look when a session shows no picture, and it works without a
     * PC because the file lives in app-private storage.
     */
    private fun addGameLogSection() {
        container.addView(Ui.section(this, "GAME LOG"))
        val card = Ui.card(this)
        val latest = GameLogs.latest(app.paths.logsRoot)
        card.addView(
            Ui.mono(
                this,
                if (latest != null) {
                    "LAST SESSION\n${latest.absolutePath}\n${GameLogs.describe(latest)}"
                } else {
                    "No game session has been launched yet. Each launch writes its console " +
                        "output to files/logs/game-<stamp>.log."
                }
            )
        )
        card.addView(
            Ui.caption(
                this,
                "The game runtime reports its boot progress and failures here. Read it before " +
                    "reporting a problem with a session."
            )
        )
        val view = Ui.secondaryButton(this, "VIEW LATEST GAME LOG")
        view.isEnabled = latest != null
        view.alpha = if (latest != null) 1f else 0.55f
        view.setOnClickListener { latest?.let { showGameLog(it) } }
        card.addView(view, params(topMarginDp = 10f))
        val copy = Ui.secondaryButton(this, "COPY LATEST GAME LOG")
        copy.isEnabled = latest != null
        copy.alpha = if (latest != null) 1f else 0.55f
        copy.setOnClickListener { latest?.let { copyGameLog(it) } }
        card.addView(copy, params(topMarginDp = 10f))
        container.addView(card)
    }

    private fun showGameLog(file: File) {
        val text = GameLogs.tail(file)
        val body = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
            setText(text.ifEmpty { "This log file is empty." })
        }
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this)
            .setTitle(GameLogs.describe(file))
            .setView(scroll)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun copyGameLog(file: File) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Game log", GameLogs.tail(file)))
        Toast.makeText(this, "Game log copied", Toast.LENGTH_SHORT).show()
    }

    private fun addResetButton() {
        val reset = Ui.secondaryButton(this, "RESET ALL SETTINGS")
        reset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset all settings?")
                .setMessage(
                    "This restores every setting to its default and resets the touch layout to " +
                        "Classic. Imported game data is not affected."
                )
                .setPositiveButton("RESET") { _, _ ->
                    settings = Settings()
                    app.settingsStore.save(settings)
                    app.touchLayoutStore.reset()
                    NativeCore.configure(settings)
                    Toast.makeText(this, "Settings reset", Toast.LENGTH_SHORT).show()
                    buildUi()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
        container.addView(reset, params(topMarginDp = 24f))
    }

    private fun applyPreset(preset: GraphicsPreset) {
        applySettings { preset.apply(it) }
        buildUi()
    }

    private fun applyFrameLimit(framesPerSecond: Int) {
        applySettings { it.copy(frameLimitFps = framesPerSecond) }
        buildUi()
    }

    private fun frameLimitLabel(framesPerSecond: Int): String =
        if (framesPerSecond == 0) "uncapped" else "$framesPerSecond fps"

    /**
     * Fills in the driver readout from the native capability query. The query
     * creates an EGL context, so it runs on a worker thread and the view is
     * only touched if the activity is still alive.
     */
    private fun gpuReportView(): TextView {
        val view = Ui.mono(this, "GPU DRIVER · querying…")
        val forceEs2 = settings.forceEs2
        Thread {
            val info = NativeCore.gpuInfo(forceEs2)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    view.text = info.summary()
                }
            }
        }.start()
        return view
    }

    private fun applyTouchPreset(preset: TouchPreset) {
        if (preset != TouchPreset.CUSTOM) {
            app.touchLayoutStore.save(TouchLayout.preset(preset))
        }
        applySettings { it.copy(touchPreset = preset.id) }
        buildUi()
    }

    private fun applySettings(transform: (Settings) -> Settings) {
        settings = app.settingsStore.edit(transform)
        NativeCore.configure(settings)
    }

    private fun glesSummary(): String {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val version = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val major = version shr 16
        val minor = version and 0xffff
        return "REPORTED   OpenGL ES $major.$minor (Android capability declaration)"
    }

    private fun audioSummary(): String {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val rate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "unknown"
        val burst = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?: "unknown"
        return "AUDIO      ${rate} Hz · ${burst} frames/burst · stereo"
    }

    private fun directorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        return directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Extracts the disc tree with a cancellable progress dialog. Extraction
     * runs off the UI thread and never publishes a partial tree.
     */
    private fun prepareDiscTree(game: InstalledGame) {
        val cancel = AtomicBoolean(false)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Preparing game data")
            .setMessage("Starting extraction…")
            .setNegativeButton("CANCEL") { _, _ -> cancel.set(true) }
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            val result = DiscTreeExtractor().extract(game, cancel) { progress ->
                val fraction = progress.fraction
                val text = buildString {
                    appendLine(progress.message)
                    append(
                        "${GameImporter.formatBytes(progress.processedBytes)} of " +
                            "${GameImporter.formatBytes(progress.totalBytes)}"
                    )
                    if (fraction != null) {
                        append(" (${(fraction * 100f).toInt()}%)")
                    }
                    if (progress.fileCount > 0) {
                        appendLine()
                        append("${progress.filesDone} of ${progress.fileCount} files")
                    }
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && dialog.isShowing) {
                        dialog.setMessage(text)
                    }
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                dialog.dismiss()
                when (result) {
                    is DiscTreeExtractor.Result.Success ->
                        AlertDialog.Builder(this)
                            .setTitle("Game data prepared")
                            .setMessage(
                                "${result.tree.fileCount} files " +
                                    "(${GameImporter.formatBytes(result.tree.byteCount)}) were " +
                                    "extracted. The native runtime can now read the game from " +
                                    "app storage."
                            )
                            .setPositiveButton(android.R.string.ok, null)
                            .show()

                    is DiscTreeExtractor.Result.Cancelled ->
                        Toast.makeText(this, "Extraction cancelled", Toast.LENGTH_SHORT).show()

                    is DiscTreeExtractor.Result.Failure ->
                        AlertDialog.Builder(this)
                            .setTitle("Extraction failed")
                            .setMessage(result.message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                }
                buildUi()
            }
        }.start()
    }

    private fun verifyGameFiles(game: InstalledGame) {
        Toast.makeText(this, "Verifying game files…", Toast.LENGTH_SHORT).show()
        Thread {
            var actual = ""
            var error: String? = null
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                game.isoFile.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 20)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                actual = digest.digest().joinToString("") {
                    String.format(Locale.US, "%02x", it.toInt() and 0xff)
                }
            } catch (exception: Exception) {
                error = exception.message ?: "read error"
            }
            runOnUiThread {
                val complete = game.isComplete()
                val matches = actual.equals(game.sha256, ignoreCase = true)
                val message = when {
                    error != null -> "Verification failed: $error"
                    !complete -> "One or more required files are missing."
                    matches -> "All required files are present and the disc image matches the imported digest."
                    else -> "Files are present, but the disc digest does not match the imported record."
                }
                AlertDialog.Builder(this)
                    .setTitle(if (matches && complete) "Game data verified" else "Verification problem")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }.start()
    }

    private fun confirmRemove(game: InstalledGame) {
        AlertDialog.Builder(this)
            .setTitle("Remove game data?")
            .setMessage(
                "This deletes the app-private copy of ${game.serial}, its extracted modules, " +
                    "and saves stored with it. The original ISO you selected is not modified."
            )
            .setPositiveButton("REMOVE") { _, _ ->
                Thread {
                    val removed = game.directory.deleteRecursively()
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            if (removed) "Game data removed" else "Unable to remove game data",
                            Toast.LENGTH_SHORT
                        ).show()
                        buildUi()
                    }
                }.start()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun copyDiagnosticReport() {
        val report = buildString {
            appendLine("SOTC Mobile diagnostic report")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine()
            appendLine(CoreProbe.buildSummary(this@SettingsCategoryActivity))
            appendLine("ABI: ${CoreProbe.expectedAbi()}")
            appendLine("Native host: ${if (NativeCore.available) "loaded" else "unavailable"}")
            appendLine("Game core: ${if (CoreProbe.isInstalled(this@SettingsCategoryActivity)) "present" else "not built"}")
            appendLine("Game: ${app.library.primaryGame()?.serial ?: "not imported"}")
            appendLine(glesSummary())
            appendLine(audioSummary())
            appendLine(NativeCore.gpuInfo(settings.forceEs2).summary())
            appendLine()
            append(NativeCore.coreStatus())
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SOTC diagnostics", report))
        Toast.makeText(this, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
    }

    private fun params(topMarginDp: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = Ui.dp(this@SettingsCategoryActivity, topMarginDp)
        }
}
