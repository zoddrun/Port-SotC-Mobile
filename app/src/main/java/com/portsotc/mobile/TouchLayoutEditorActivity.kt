package com.portsotc.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.portsotc.mobile.input.TouchControl
import com.portsotc.mobile.input.TouchControllerView
import com.portsotc.mobile.input.TouchLayout

class TouchLayoutEditorActivity : Activity() {

    private lateinit var app: SotcApplication
    private lateinit var layout: TouchLayout
    private lateinit var touchView: TouchControllerView
    private lateinit var selectionView: TextView
    private lateinit var sizeBar: SeekBar
    private lateinit var alphaBar: SeekBar

    private var selected: TouchControl? = null
    private var updatingControls = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touch_editor)
        app = application as SotcApplication
        layout = app.touchLayoutStore.load()

        touchView = findViewById(R.id.editor_touch_view)
        selectionView = findViewById(R.id.editor_selection)
        sizeBar = findViewById(R.id.editor_size)
        alphaBar = findViewById(R.id.editor_alpha)

        touchView.layout = layout
        touchView.editMode = true
        touchView.globalAlpha = 1f
        touchView.globalScale = app.settingsStore.load().touchScale
        touchView.listener = object : TouchControllerView.Listener {
            override fun onControlSelected(control: TouchControl) {
                selected = control
                updateSelectionPanel()
            }

            override fun onControlMoved(control: TouchControl) {
                if (control.id == selected?.id) {
                    updateSelectionPanel()
                }
            }
        }

        selectionView.text = "Tap a control to select it, then drag it into place."

        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (updatingControls || !fromUser) return
                val control = selected ?: return
                control.radius = 0.02f + (progress / 100f) * 0.23f
                touchView.invalidate()
                updateSelectionPanel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        alphaBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (updatingControls || !fromUser) return
                val control = selected ?: return
                control.alpha = 0.1f + (progress / 100f) * 0.9f
                touchView.invalidate()
                updateSelectionPanel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        findViewById<Button>(R.id.editor_hide).setOnClickListener { hideSelected() }
        findViewById<Button>(R.id.editor_reset).setOnClickListener { resetLayout() }
        findViewById<Button>(R.id.editor_save).setOnClickListener { saveAndFinish() }
    }

    private fun updateSelectionPanel() {
        val control = selected
        if (control == null) {
            selectionView.text = "Tap a control to select it, then drag it into place."
            return
        }
        selectionView.text = buildString {
            append("SELECTED: ")
            append(control.id.uppercase())
            append(" · X ")
            append(String.format(java.util.Locale.US, "%.2f", control.centerX))
            append(" · Y ")
            append(String.format(java.util.Locale.US, "%.2f", control.centerY))
            append(" · SIZE ")
            append(String.format(java.util.Locale.US, "%.2f", control.radius))
        }
        updatingControls = true
        sizeBar.progress = (((control.radius - 0.02f) / 0.23f) * 100f)
            .toInt()
            .coerceIn(0, 100)
        alphaBar.progress = (((control.alpha - 0.1f) / 0.9f) * 100f)
            .toInt()
            .coerceIn(0, 100)
        updatingControls = false
    }

    private fun hideSelected() {
        val control = selected
        if (control == null) {
            Toast.makeText(this, "Select a control first", Toast.LENGTH_SHORT).show()
            return
        }
        control.hidden = true
        selected = null
        selectionView.text = "${control.id.uppercase()} hidden. Save to keep this change."
        touchView.invalidate()
    }

    private fun resetLayout() {
        layout = TouchLayout.defaults()
        touchView.layout = layout
        selected = null
        updatingControls = true
        sizeBar.progress = 50
        alphaBar.progress = 50
        updatingControls = false
        selectionView.text = "Layout reset to defaults. Tap a control to edit it."
        touchView.invalidate()
    }

    private fun saveAndFinish() {
        app.touchLayoutStore.save(layout)
        Toast.makeText(this, "Touch layout saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onPause() {
        app.touchLayoutStore.save(layout)
        super.onPause()
    }
}
