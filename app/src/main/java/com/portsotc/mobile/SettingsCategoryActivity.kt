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
import com.portsotc.mobile.core.HdPresetId
import com.portsotc.mobile.core.InstalledGame
import com.portsotc.mobile.core.LocalAchievementLedger
import com.portsotc.mobile.core.LocalAchievementState
import com.portsotc.mobile.core.LocalAchievements
import com.portsotc.mobile.core.MemoryCardVault
import com.portsotc.mobile.core.NativeDiagnosticsCache
import com.portsotc.mobile.core.NativeDiagnosticsSnapshot
import com.portsotc.mobile.core.Settings
import com.portsotc.mobile.core.TouchGripMode
import com.portsotc.mobile.core.TouchPreset
import com.portsotc.mobile.disc.DiscInspector
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
            SettingsCategory.DISC_INSPECTOR -> buildDiscInspector()
            SettingsCategory.ACHIEVEMENTS -> buildAchievements()
            SettingsCategory.VAULT -> buildVault()
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
            description = "Smooth the upscaled image in the diagnostics view. It does not " +
                "change the game runtime's texture filtering.",
            initial = settings.linearFiltering
        ) { checked -> applySettings { it.copy(linearFiltering = checked) } }
        Ui.addToggle(
            parent = rendering,
            context = this,
            label = "Adaptive resolution",
            description = "Lowers the internal resolution when frames miss their time budget and " +
                "restores it when there is headroom. The slider above is the ceiling. The runtime " +
                "measures its own frame time, so the performance overlay is not required. " +
                "Takes effect on the next game session.",
            initial = settings.adaptiveResolution
        ) { checked -> applySettings { it.copy(adaptiveResolution = checked) } }
        rendering.addView(
            Ui.caption(
                this,
                "Internal resolution and adaptive scaling are sent to the game runtime at " +
                    "startup and take effect on the next game session. Values below 50% are " +
                    "raised to 50% because the runtime does not support a lower render scale. " +
                    "The separate native engine view used for diagnostics keeps its own setting."
            )
        )
        container.addView(rendering)

        container.addView(Ui.section(this, "HD SHADER PACK"))
        val hdPack = Ui.card(this)
        hdPack.addView(
            Ui.mono(
                this,
                "STATUS · ${if (app.shaderPackReady) "verified" else "unavailable"} · " +
                    app.shaderPackDetail
            )
        )
        hdPack.addView(
            Ui.caption(
                this,
                "The bundled GLSL pack is installed into app-private storage and verified with " +
                    "SHA-256. This preset feeds the native diagnostics presentation pass and is " +
                    "published to the recompiled runner. Changes apply to the next engine session. " +
                    "Shader visuals and full-game rendering still require verification on physical " +
                    "ARM64 hardware."
            )
        )
        val batteryHd = Ui.secondaryButton(this, HdPresetId.BATTERY.label.uppercase(Locale.US))
        batteryHd.setOnClickListener { applyHdPreset(HdPresetId.BATTERY) }
        val balancedHd = Ui.secondaryButton(this, HdPresetId.BALANCED.label.uppercase(Locale.US))
        balancedHd.setOnClickListener { applyHdPreset(HdPresetId.BALANCED) }
        Ui.addButtonRow(hdPack, this, batteryHd, balancedHd)
        val ultraHd = Ui.secondaryButton(this, HdPresetId.ULTRA.label.uppercase(Locale.US))
        ultraHd.setOnClickListener { applyHdPreset(HdPresetId.ULTRA) }
        hdPack.addView(ultraHd, params(topMarginDp = 10f))
        hdPack.addView(
            Ui.mono(
                this,
                "ACTIVE · ${HdPresetId.fromId(settings.hdShaderPreset).label.uppercase(Locale.US)}"
            ),
            params(topMarginDp = 14f)
        )
        container.addView(hdPack)

        container.addView(Ui.section(this, "BACKEND"))
        val backend = Ui.card(this)
        backend.addView(Ui.mono(this, glesSummary()))
        backend.addView(gpuReportView(), params(topMarginDp = 12f))
        Ui.addToggle(
            parent = backend,
            context = this,
            label = "Force OpenGL ES 2.0",
            description = "Uses the ES 2 compatibility path at the next session start. This " +
                "affects the diagnostics engine view only; it does not change the game runtime " +
                "context or prove full-game ES 2 compatibility.",
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
            description = "Off by default. Shows runtime frame statistics during gameplay and " +
                "enables the five-second diagnostic record in the saved game log. It applies " +
                "to the next game session.",
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
                "The limit paces presentation only and leaves the guest simulation timing " +
                    "unchanged, so game logic is not sped up or slowed down. It is sent to the " +
                    "game runtime at startup and takes effect on the next game session; the " +
                    "runtime remains the single owner of its frame loop."
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
        appearance.addView(
            Ui.caption(
                this,
                "The game runtime applies its own limits: control size 60-140% and opacity " +
                    "20-100%. Values outside those ranges are reduced at the next game session."
            )
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
            description = "Inverts the vertical axis in the diagnostics view. The game runtime " +
                "does not receive this setting yet.",
            initial = settings.invertLookY
        ) { checked -> applySettings { it.copy(invertLookY = checked) } }
        container.addView(camera)

        container.addView(Ui.section(this, "GRIP MODE"))
        val grip = Ui.card(this)
        grip.addView(
            Ui.caption(
                this,
                "The right half of the screen owns the look camera. Shoulder uses a fixed R1 hold " +
                    "near the upper-right corner, Claw places it under the index finger, and Bridge " +
                    "uses a centered upper hold. These runner-side ergonomics are not yet verified " +
                    "on physical hardware."
            )
        )
        val shoulder = Ui.secondaryButton(this, TouchGripMode.SHOULDER.label.uppercase(Locale.US))
        shoulder.setOnClickListener { applyGripMode(TouchGripMode.SHOULDER) }
        val claw = Ui.secondaryButton(this, TouchGripMode.CLAW.label.uppercase(Locale.US))
        claw.setOnClickListener { applyGripMode(TouchGripMode.CLAW) }
        Ui.addButtonRow(grip, this, shoulder, claw)
        val bridge = Ui.secondaryButton(this, TouchGripMode.BRIDGE.label.uppercase(Locale.US))
        bridge.setOnClickListener { applyGripMode(TouchGripMode.BRIDGE) }
        grip.addView(bridge, params(topMarginDp = 10f))
        grip.addView(
            Ui.mono(
                this,
                "ACTIVE · ${TouchGripMode.fromId(settings.touchGripMode).label.uppercase(Locale.US)}"
            ),
            params(topMarginDp = 14f)
        )
        container.addView(grip)

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

    // ---------------------------------------------------------- Disc Inspector

    private fun buildDiscInspector() {
        val game = app.library.primaryGame()
        container.addView(Ui.section(this, "CURRENT IMAGE"))
        val info = Ui.card(this)
        if (game == null) {
            info.addView(Ui.body(this, "No game data imported."))
            info.addView(
                Ui.caption(
                    this,
                    "Import a supported disc image before running an inspection. The inspector " +
                        "reads only the app-private copy; the original file you selected is never modified."
                )
            )
            container.addView(info)
            return
        }
        info.addView(
            Ui.mono(
                this,
                buildString {
                    appendLine("IMAGE      ${game.isoFile.absolutePath}")
                    appendLine("SERIAL     ${game.serial}")
                    appendLine("BOOT       ${game.bootFile}")
                    appendLine("SHA-256    ${game.sha256}")
                    append("SIZE       ${GameImporter.formatBytes(game.imageSize)}")
                }
            )
        )
        container.addView(info)

        val last = app.inspectionStore.latest()
        val output = Ui.mono(
            this,
            if (last != null) {
                buildString {
                    appendLine("LAST INSPECTION")
                    appendLine("SERIAL      ${last.serial ?: "unknown"}")
                    appendLine("BOOT NAME   ${last.bootName ?: "unknown"}")
                    appendLine("BOOT MD5    ${last.bootMd5}")
                    append("CHECKED     ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(last.inspectedAt))}")
                }
            } else {
                "No inspection has been recorded for this installation."
            }
        )

        container.addView(Ui.section(this, "BOOT2 IDENTIFIER"))
        val inspection = Ui.card(this)
        inspection.addView(
            Ui.caption(
                this,
                "The boot identifier is MD5 over the executable name from SYSTEM.CNF followed by " +
                    "the executable bytes. The image digest is SHA-256 over every byte of the " +
                    "ISO. Neither value contacts an achievement service or proves gameplay."
            )
        )
        inspection.addView(output, params(topMarginDp = 12f))
        container.addView(inspection)

        val inspect = Ui.primaryButton(this, "RUN DISC INSPECTION")
        inspect.setOnClickListener {
            inspect.isEnabled = false
            output.text = "Inspecting the imported image..."
            Thread {
                val report = try {
                    DiscInspector().inspect(game.isoFile)
                } catch (error: Exception) {
                    null
                }
                val text = report?.format()
                    ?: "Inspection failed. Check the imported image and try again."
                val record = report?.let { app.inspectionStore.save(it) }
                if (record != null) syncAchievements()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        output.text = text
                        inspect.isEnabled = true
                    }
                }
            }.start()
        }
        container.addView(inspect, params(topMarginDp = 16f))
    }

    // ------------------------------------------------------------ Achievements

    private fun buildAchievements() {
        val ledger = syncAchievements()
        container.addView(Ui.section(this, "LOCAL MILESTONES"))
        val intro = Ui.card(this)
        intro.addView(
            Ui.body(
                this,
                "These are original, locally evaluated setup milestones. They are not game " +
                    "trophies, RetroAchievements, or evidence that gameplay has been verified."
            )
        )
        intro.addView(
            Ui.caption(
                this,
                "A milestone unlocks only when a real file, digest, or packaged runtime check " +
                    "succeeds on this device."
            )
        )
        container.addView(intro)

        container.addView(
            Ui.section(
                this,
                "UNLOCKED ${ledger.unlockedIds().size} OF ${LocalAchievements.ALL.size}"
            )
        )
        LocalAchievements.ALL.forEach { achievement ->
            val card = Ui.card(this)
            val unlockedAt = ledger.unlockedAt(achievement.id)
            card.addView(
                Ui.mono(
                    this,
                    if (unlockedAt != null) {
                        "UNLOCKED - ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(unlockedAt))}"
                    } else {
                        "LOCKED - ${achievement.category.label}"
                    }
                )
            )
            card.addView(Ui.body(this, achievement.title), params(topMarginDp = 6f))
            card.addView(Ui.caption(this, achievement.description), params(topMarginDp = 4f))
            container.addView(card)
        }

        val reset = Ui.secondaryButton(this, "RESET LOCAL MILESTONES")
        reset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset local milestones?")
                .setMessage("This clears the local unlock record. Game data, backups, and settings are not affected.")
                .setPositiveButton("RESET") { _, _ ->
                    app.localAchievementStore.clear()
                    Toast.makeText(this, "Local milestones reset", Toast.LENGTH_SHORT).show()
                    buildUi()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
        container.addView(reset, params(topMarginDp = 18f))
    }

    // ------------------------------------------------------------ Memory Vault

    private fun buildVault() {
        val game = app.library.primaryGame()
        container.addView(Ui.section(this, "RUNTIME MEMORY CARDS"))
        val info = Ui.card(this)
        if (game == null) {
            info.addView(Ui.body(this, "No game data imported."))
            info.addView(
                Ui.caption(
                    this,
                    "A memory-card backup can only be created after a supported game has been " +
                        "imported and its runtime files have been prepared."
                )
            )
            container.addView(info)
            return
        }
        val memoryCardDirectory = game.memoryCardDirectory
        val files = memoryCardDirectory.walkTopDown().filter { it.isFile }.toList()
        info.addView(
            Ui.mono(
                this,
                buildString {
                    appendLine("GAME       ${game.serial}")
                    appendLine("DIRECTORY  ${memoryCardDirectory.absolutePath}")
                    appendLine("FILES      ${files.size}")
                    append("SIZE       ${GameImporter.formatBytes(files.sumOf { it.length() })}")
                }
            )
        )
        info.addView(
            Ui.caption(
                this,
                "The vault copies the files present in this directory. It does not parse or " +
                    "validate PlayStation 2 memory-card structures and does not prove that a " +
                    "save will load in the game."
            )
        )
        container.addView(info)

        val backups = app.vault.listBackups()
        container.addView(Ui.section(this, "LOCAL BACKUPS"))
        val backupCard = Ui.card(this)
        if (backups.isEmpty()) {
            backupCard.addView(Ui.body(this, "No backups have been created."))
        } else {
            backups.forEachIndexed { index, backup ->
                if (index > 0) backupCard.addView(Ui.divider(this))
                backupCard.addView(
                    Ui.mono(
                        this,
                        buildString {
                            appendLine(backup.displayName)
                            appendLine("CREATED  ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(backup.createdAt))}")
                            appendLine("FILES    ${backup.fileCount}")
                            append("SIZE     ${GameImporter.formatBytes(backup.byteCount)}")
                        }
                    )
                )
            }
        }
        backupCard.addView(
            Ui.caption(
                this,
                "Backups stay in app-private storage and are removed with the application. " +
                    "They are never uploaded."
            )
        )
        container.addView(backupCard)

        val create = Ui.primaryButton(this, "CREATE BACKUP")
        create.setOnClickListener {
            create.isEnabled = false
            Thread {
                val result = try {
                    app.vault.createBackup(game)
                    syncAchievements()
                    "Memory-card files backed up."
                } catch (error: Exception) {
                    error.message ?: "Unable to create a backup"
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                        buildUi()
                    }
                }
            }.start()
        }
        container.addView(create, params(topMarginDp = 16f))

        val restore = Ui.secondaryButton(this, "RESTORE BACKUP")
        restore.isEnabled = backups.isNotEmpty()
        restore.alpha = if (backups.isNotEmpty()) 1f else 0.55f
        restore.setOnClickListener { showRestoreChooser(backups) }
        container.addView(restore, params(topMarginDp = 10f))

        val delete = Ui.secondaryButton(this, "DELETE BACKUP")
        delete.isEnabled = backups.isNotEmpty()
        delete.alpha = if (backups.isNotEmpty()) 1f else 0.55f
        delete.setOnClickListener { showDeleteChooser(backups) }
        container.addView(delete, params(topMarginDp = 10f))
    }

    private fun showRestoreChooser(backups: List<MemoryCardVault.Backup>) {
        AlertDialog.Builder(this)
            .setTitle("Restore a backup")
            .setMessage("The current memory-card directory is retained before the selected files replace it.")
            .setItems(backups.map { it.displayName }.toTypedArray()) { _, which ->
                val backup = backups[which]
                AlertDialog.Builder(this)
                    .setTitle("Restore ${backup.displayName}?")
                    .setMessage("This replaces the app-private memory-card files for this game.")
                    .setPositiveButton("RESTORE") { _, _ ->
                        val game = app.library.primaryGame() ?: return@setPositiveButton
                        Thread {
                            val result = try {
                                val restored = app.vault.restore(backup.name, game)
                                val kept = if (restored.previousDirectory != null) {
                                    " Previous files were kept."
                                } else {
                                    ""
                                }
                                "Backup restored.$kept"
                            } catch (error: Exception) {
                                error.message ?: "Unable to restore the backup"
                            }
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                                    buildUi()
                                }
                            }
                        }.start()
                    }
                    .setNegativeButton("CANCEL", null)
                    .show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showDeleteChooser(backups: List<MemoryCardVault.Backup>) {
        AlertDialog.Builder(this)
            .setTitle("Delete a backup")
            .setItems(backups.map { it.displayName }.toTypedArray()) { _, which ->
                val backup = backups[which]
                app.vault.delete(backup.name)
                Toast.makeText(this, "Backup deleted", Toast.LENGTH_SHORT).show()
                buildUi()
            }
            .setNegativeButton("CANCEL", null)
            .show()
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

        addNativeCacheSection()

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

    private fun achievementState(): LocalAchievementState {
        val game = app.library.primaryGame()
        val treeReady = game?.isDiscTreeReady() == true
        val inspection = app.inspectionStore.latest()
        return LocalAchievementState(
            supportedDiscImported = game?.serial?.equals("SCUS-97472", ignoreCase = true) == true,
            discImageVerified = game?.isComplete() == true && treeReady,
            boot2HashRecorded = inspection?.bootMd5?.length == 32,
            discTreeReady = treeReady,
            runtimeInstalled = CoreProbe.isRuntimeInstalled(this),
            vaultBackupCreated = app.vault.listBackups().isNotEmpty(),
            nativeReportCached = app.nativeDiagnosticsCache.load() != null
        )
    }

    /** Records newly satisfied local milestones and returns the current ledger. */
    private fun syncAchievements(): LocalAchievementLedger {
        val ledger = app.localAchievementStore.load()
        val unlocked = ledger.sync(achievementState(), System.currentTimeMillis())
        if (unlocked.isNotEmpty()) {
            app.localAchievementStore.save(ledger)
            Toast.makeText(
                this,
                "Local milestone unlocked: ${unlocked.first().title}",
                Toast.LENGTH_LONG
            ).show()
        }
        return ledger
    }

    private fun addNativeCacheSection() {
        container.addView(Ui.section(this, "NATIVE REPORT CACHE"))
        val card = Ui.card(this)
        val cached = app.nativeDiagnosticsCache.load()
        card.addView(
            Ui.mono(
                this,
                if (cached == null) {
                    "No native report has been cached."
                } else {
                    buildString {
                        appendLine("CACHED    ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(cached.createdAt))}")
                        appendLine("ABI       ${cached.abi}")
                        appendLine("BUILD     ${cached.build}")
                        appendLine("NATIVE    ${if (cached.nativeAvailable) "loaded" else "unavailable"}")
                        appendLine(cached.gpuSummary)
                        appendLine(cached.coreStatus)
                        appendLine(cached.hdShaderStatus)
                        append("ERROR     ${cached.lastError.ifEmpty { "none" }}")
                    }
                }
            )
        )
        card.addView(
            Ui.caption(
                this,
                "This cache stores diagnostics output only. GPU probing creates an offscreen " +
                    "EGL context, so refresh runs on a worker thread. It is not gameplay " +
                    "rendering evidence."
            )
        )
        container.addView(card)

        val refresh = Ui.secondaryButton(this, "REFRESH NATIVE REPORT")
        refresh.setOnClickListener {
            refresh.isEnabled = false
            Thread {
                val snapshot = NativeDiagnosticsSnapshot(
                    createdAt = System.currentTimeMillis(),
                    abi = CoreProbe.expectedAbi(),
                    build = CoreProbe.buildSummary(this@SettingsCategoryActivity),
                    gpuSummary = NativeCore.gpuInfo(settings.forceEs2).summary(),
                    coreStatus = NativeCore.coreStatus(),
                    hdShaderStatus = NativeCore.hdShaderStatus(),
                    nativeAvailable = NativeCore.available,
                    lastError = NativeCore.lastError ?: ""
                )
                app.nativeDiagnosticsCache.save(snapshot)
                syncAchievements()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) buildUi()
                }
            }.start()
        }
        container.addView(refresh, params(topMarginDp = 12f))

        val clear = Ui.secondaryButton(this, "CLEAR NATIVE REPORT")
        clear.isEnabled = cached != null
        clear.alpha = if (cached != null) 1f else 0.55f
        clear.setOnClickListener {
            app.nativeDiagnosticsCache.clear()
            Toast.makeText(this, "Native report cache cleared", Toast.LENGTH_SHORT).show()
            buildUi()
        }
        container.addView(clear, params(topMarginDp = 10f))
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

    private fun applyHdPreset(preset: HdPresetId) {
        applySettings { it.copy(hdShaderPreset = preset.id) }
        app.refreshHdShaderPack()
        buildUi()
    }

    private fun applyGripMode(mode: TouchGripMode) {
        applySettings { it.copy(touchGripMode = mode.id) }
        buildUi()
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
