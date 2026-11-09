package com.portsotc.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import com.portsotc.mobile.core.Settings
import com.portsotc.mobile.input.ControllerMapping
import com.portsotc.mobile.input.GamepadManager
import com.portsotc.mobile.input.PadState
import com.portsotc.mobile.input.Ps2Buttons
import com.portsotc.mobile.ui.Ui

class ControllerSettingsActivity : Activity() {

    private lateinit var app: SotcApplication
    private lateinit var settings: Settings
    private lateinit var container: LinearLayout
    private lateinit var mapping: ControllerMapping
    private lateinit var gamepadManager: GamepadManager
    private lateinit var mappingView: android.widget.TextView
    private lateinit var connectedView: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)
        app = application as SotcApplication
        settings = app.settingsStore.load()
        mapping = ControllerMapping(this)
        gamepadManager = GamepadManager(
            this,
            PadState(),
            mapping
        ) { app.settingsStore.load() }
        container = findViewById(R.id.controller_container)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        settings = app.settingsStore.load()
        refreshConnectedControllers()
        refreshMapping()
    }

    private fun buildUi() {
        container.removeAllViews()
        container.addView(Ui.title(this, getString(R.string.controller_title)))
        container.addView(
            Ui.caption(
                this,
                "Physical controllers are merged with the touch controller. " +
                    "Analog sticks use a radial deadzone."
            )
        )

        container.addView(Ui.section(this, "CONNECTED CONTROLLERS"))
        val connectedCard = Ui.card(this)
        connectedView = Ui.mono(this)
        connectedCard.addView(connectedView)
        container.addView(connectedCard)

        container.addView(Ui.section(this, "INPUT OPTIONS"))
        val options = Ui.card(this)
        Ui.addSlider(
            parent = options,
            context = this,
            label = "STICK DEADZONE",
            min = 0,
            max = 60,
            initial = (settings.padDeadzone * 100f).toInt(),
            formatter = { "$it%" },
            onChange = { value -> applySettings { it.copy(padDeadzone = value / 100f) } }
        )
        Ui.addToggle(
            parent = options,
            context = this,
            label = "Invert vertical look",
            description = "Inverts the vertical axis of the right analog stick.",
            initial = settings.invertLookY
        ) { checked -> applySettings { it.copy(invertLookY = checked) } }
        Ui.addToggle(
            parent = options,
            context = this,
            label = "Vibration",
            description = "Allows the game core to request controller vibration when supported.",
            initial = settings.vibrationEnabled
        ) { checked -> applySettings { it.copy(vibrationEnabled = checked) } }
        container.addView(options)

        container.addView(Ui.section(this, "BUTTON MAPPING"))
        val mappingCard = Ui.card(this)
        mappingView = Ui.mono(this)
        mappingCard.addView(mappingView)
        val reset = Ui.secondaryButton(this, "RESET MAPPING")
        reset.setOnClickListener {
            mapping.reset()
            refreshMapping()
        }
        mappingCard.addView(
            reset,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(this@ControllerSettingsActivity, 12f) }
        )
        container.addView(mappingCard)

        val touchEditor = Ui.primaryButton(this, "EDIT TOUCH LAYOUT")
        touchEditor.setOnClickListener {
            startActivity(Intent(this, TouchLayoutEditorActivity::class.java))
        }
        container.addView(
            touchEditor,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(this@ControllerSettingsActivity, 18f) }
        )
    }

    private fun refreshConnectedControllers() {
        val controllers = gamepadManager.connectedControllers()
        connectedView.text = if (controllers.isEmpty()) {
            "No physical controller detected.\nTouch controls remain available."
        } else {
            controllers.joinToString(separator = "\n")
        }
    }

    private fun refreshMapping() {
        val bindings = mapping.effectiveBindings().entries
            .sortedBy { Ps2Buttons.describe(it.value) }
        mappingView.text = bindings.joinToString(separator = "\n") { (keyCode, button) ->
            val buttonName = Ps2Buttons.describe(button)
            val keyName = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            "$buttonName <- $keyName"
        }
    }

    private fun applySettings(transform: (Settings) -> Settings) {
        settings = app.settingsStore.edit(transform)
    }
}
