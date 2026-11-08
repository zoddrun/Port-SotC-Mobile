package com.portsotc.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.portsotc.mobile.core.CoreProbe
import com.portsotc.mobile.core.Settings
import com.portsotc.mobile.nativebridge.NativeCore
import com.portsotc.mobile.ui.Ui

class SettingsActivity : Activity() {

    private lateinit var app: SotcApplication
    private lateinit var settings: Settings
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        app = application as SotcApplication
        settings = app.settingsStore.load()
        container = findViewById(R.id.settings_container)
        buildUi()
    }

    private fun buildUi() {
        container.removeAllViews()
        container.addView(Ui.title(this, getString(R.string.settings_title)))
        container.addView(
            Ui.caption(
                this,
                "Settings are applied to the native engine immediately and persist across sessions."
            )
        )

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
            onChange = { value ->
                applySettings { it.copy(resolutionScalePercent = value) }
            }
        )
        Ui.addToggle(
            parent = rendering,
            context = this,
            label = "Linear filtering",
            description = "Smooth the upscaled image. Disable for a sharper, pixel-like result.",
            initial = settings.linearFiltering
        ) { checked -> applySettings { it.copy(linearFiltering = checked) } }
        rendering.addView(
            Ui.caption(
                this,
                "The engine renders into an offscreen target at the selected scale, then blits to the display."
            )
        )
        container.addView(rendering)

        container.addView(Ui.section(this, "AUDIO"))
        val audio = Ui.card(this)
        Ui.addToggle(
            parent = audio,
            context = this,
            label = "Audio output",
            description = "Enables the AAudio output stream. Diagnostic mode plays a procedural ambience.",
            initial = settings.audioEnabled
        ) { checked -> applySettings { it.copy(audioEnabled = checked) } }
        Ui.addSlider(
            parent = audio,
            context = this,
            label = "VOLUME",
            min = 0,
            max = 100,
            initial = (settings.audioVolume * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(audioVolume = value / 100f) } }
        )
        container.addView(audio)

        container.addView(Ui.section(this, "INTERFACE"))
        val interfaceCard = Ui.card(this)
        Ui.addToggle(
            parent = interfaceCard,
            context = this,
            label = "Diagnostics overlay",
            description = "Shows the engine state banner while running.",
            initial = settings.diagnosticsOverlay
        ) { checked -> applySettings { it.copy(diagnosticsOverlay = checked) } }
        Ui.addToggle(
            parent = interfaceCard,
            context = this,
            label = "Performance statistics",
            description = "Shows frame rate, frame time, surface size and render scale.",
            initial = settings.showStatistics
        ) { checked -> applySettings { it.copy(showStatistics = checked) } }
        Ui.addToggle(
            parent = interfaceCard,
            context = this,
            label = "Touch controls",
            description = "Shows the on-screen PlayStation 2 controller.",
            initial = settings.touchControlsEnabled
        ) { checked -> applySettings { it.copy(touchControlsEnabled = checked) } }
        Ui.addSlider(
            parent = interfaceCard,
            context = this,
            label = "TOUCH OPACITY",
            min = 15,
            max = 100,
            initial = (settings.touchAlpha * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(touchAlpha = value / 100f) } }
        )
        Ui.addSlider(
            parent = interfaceCard,
            context = this,
            label = "TOUCH SIZE",
            min = 60,
            max = 160,
            initial = (settings.touchScale * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(touchScale = value / 100f) } }
        )
        val editTouch = Ui.primaryButton(this, "EDIT TOUCH LAYOUT")
        editTouch.setOnClickListener {
            startActivity(Intent(this, TouchLayoutEditorActivity::class.java))
        }
        interfaceCard.addView(editTouch, touchButtonParams())
        container.addView(interfaceCard)

        container.addView(Ui.section(this, "SYSTEM"))
        val system = Ui.card(this)
        val game = app.library.primaryGame()
        val coreInstalled = CoreProbe.isInstalled(this)
        system.addView(
            Ui.mono(
                this,
                buildString {
                    appendLine(CoreProbe.buildSummary(this@SettingsActivity))
                    appendLine("NATIVE   ${if (NativeCore.available) "LOADED" else "UNAVAILABLE"}")
                    appendLine("CORE     ${if (coreInstalled) "PRESENT" else "NOT BUILT"}")
                    appendLine("GAME     ${game?.serial ?: "NOT IMPORTED"}")
                    append("STORAGE  ${app.paths.gamesRoot.absolutePath}")
                }
            )
        )
        val diagnostics = Ui.secondaryButton(this, "OPEN ENGINE DIAGNOSTICS")
        diagnostics.setOnClickListener {
            startActivity(GameActivity.diagnosticsIntent(this))
        }
        system.addView(diagnostics, touchButtonParams())
        container.addView(system)

        val reset = Ui.secondaryButton(this, "RESET SETTINGS TO DEFAULTS")
        reset.setOnClickListener {
            settings = Settings()
            app.settingsStore.save(settings)
            NativeCore.configure(settings)
            buildUi()
        }
        container.addView(reset, touchButtonParams())
    }

    private fun touchButtonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = Ui.dp(this@SettingsActivity, 14f)
        }

    private fun applySettings(transform: (Settings) -> Settings) {
        settings = app.settingsStore.edit(transform)
        NativeCore.configure(settings)
    }
}
