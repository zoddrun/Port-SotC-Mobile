package com.portsotc.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.portsotc.mobile.core.Settings
import com.portsotc.mobile.core.TouchGripMode
import com.portsotc.mobile.input.ControllerMapping
import com.portsotc.mobile.input.GamepadManager
import com.portsotc.mobile.input.PadState
import com.portsotc.mobile.input.Ps2Buttons
import com.portsotc.mobile.ui.Ui
import java.util.Locale

class ControllerSettingsActivity : Activity() {

    private lateinit var app: SotcApplication
    private lateinit var settings: Settings
    private lateinit var container: LinearLayout
    private lateinit var mapping: ControllerMapping
    private lateinit var gamepadManager: GamepadManager
    private lateinit var mappingList: LinearLayout
    private lateinit var gripStatusView: TextView
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
        refreshGripStatus()
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
        mappingList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mappingCard.addView(mappingList)
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

        container.addView(Ui.section(this, "R1 GRIP MODE"))
        val gripCard = Ui.card(this)
        gripCard.addView(
            Ui.caption(
                this,
                "Choose how the double-ring R1 grip behaves in the runner HUD: " +
                    "Latch uses the Claw position, Hold uses Shoulder, and Hybrid uses Bridge. " +
                    "Runner ergonomics are not yet verified on physical hardware."
            )
        )
        val latch = Ui.secondaryButton(this, "LATCH")
        latch.setOnClickListener { applyGripMode(TouchGripMode.CLAW) }
        val hold = Ui.secondaryButton(this, "HOLD")
        hold.setOnClickListener { applyGripMode(TouchGripMode.SHOULDER) }
        Ui.addButtonRow(gripCard, this, latch, hold)
        val hybrid = Ui.secondaryButton(this, "HYBRID")
        hybrid.setOnClickListener { applyGripMode(TouchGripMode.BRIDGE) }
        gripCard.addView(
            hybrid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(this@ControllerSettingsActivity, 10f) }
        )
        gripStatusView = Ui.mono(this)
        gripCard.addView(
            gripStatusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(this@ControllerSettingsActivity, 14f) }
        )
        container.addView(gripCard)

        val touchEditor = Ui.primaryButton(this, "CUSTOMIZE TOUCH LAYOUT")
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
            "No external controller connected · Touch HUD active"
        } else {
            controllers.joinToString(separator = "\n")
        }
    }

    private fun refreshMapping() {
        mappingList.removeAllViews()
        val bindings = mapping.effectiveBindings().entries
            .sortedBy { Ps2Buttons.describe(it.value) }
        for ((keyCode, button) in bindings) {
            val buttonName = Ps2Buttons.describe(button)
            val keyName = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            mappingList.addView(mappingRow(buttonName, keyName))
        }
    }

    /** A bordered PlayStation keycap beside a friendly keyboard key label. */
    private fun mappingRow(buttonName: String, keyName: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(this@ControllerSettingsActivity, 5f), 0,
                Ui.dp(this@ControllerSettingsActivity, 5f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val keycap = TextView(this).apply {
            text = buttonName
            gravity = Gravity.CENTER
            textSize = 11f
            isAllCaps = true
            setTextColor(getColor(R.color.void_black))
            setPadding(Ui.dp(this@ControllerSettingsActivity, 6f),
                Ui.dp(this@ControllerSettingsActivity, 4f),
                Ui.dp(this@ControllerSettingsActivity, 6f),
                Ui.dp(this@ControllerSettingsActivity, 4f))
            minWidth = Ui.dp(this@ControllerSettingsActivity, 62f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = Ui.dp(this@ControllerSettingsActivity, 4f).toFloat()
                setColor(getColor(R.color.bone_gold))
                setStroke(1, getColor(R.color.stone_edge))
            }
        }
        row.addView(
            keycap,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val keyLabel = Ui.body(this, keyName).apply {
            setPadding(Ui.dp(this@ControllerSettingsActivity, 14f), 0, 0, 0)
        }
        row.addView(
            keyLabel,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        return row
    }

    private fun applyGripMode(mode: TouchGripMode) {
        settings = app.settingsStore.edit { it.copy(touchGripMode = mode.id) }
        refreshGripStatus()
    }

    private fun refreshGripStatus() {
        if (!::gripStatusView.isInitialized) return
        val active = TouchGripMode.fromId(settings.touchGripMode)
        gripStatusView.text = "ACTIVE · ${active.label.uppercase(Locale.US)}"
    }

    private fun applySettings(transform: (Settings) -> Settings) {
        settings = app.settingsStore.edit(transform)
    }
}
