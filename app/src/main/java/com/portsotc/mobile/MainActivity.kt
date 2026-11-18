package com.portsotc.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.portsotc.mobile.core.CoreProbe
import com.portsotc.mobile.core.GameImporter
import com.portsotc.mobile.core.InstalledGame

/**
 * Launcher screen.
 *
 * The screen is deliberately small: branding, one primary action, and the
 * three secondary destinations a player actually needs. Technical details are
 * confined to the details dialog and to Settings -> Advanced -> Diagnostics.
 */
class MainActivity : Activity() {

    private lateinit var app: SotcApplication
    private lateinit var statusLabel: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusSerial: TextView
    private lateinit var playHint: TextView
    private lateinit var detailsLink: TextView
    private lateinit var playButton: Button
    private lateinit var buildInfo: TextView

    private var game: InstalledGame? = null
    private var coreInstalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        app = application as SotcApplication

        statusLabel = findViewById(R.id.status_label)
        statusDetail = findViewById(R.id.status_detail)
        statusSerial = findViewById(R.id.status_serial)
        playHint = findViewById(R.id.play_hint)
        detailsLink = findViewById(R.id.button_details)
        playButton = findViewById(R.id.button_play)
        buildInfo = findViewById(R.id.build_info)

        playButton.setOnClickListener { play() }
        detailsLink.setOnClickListener { showDetails() }
        findViewById<Button>(R.id.button_import).setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }
        findViewById<Button>(R.id.button_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.button_controller).setOnClickListener {
            startActivity(Intent(this, ControllerSettingsActivity::class.java))
        }

        buildInfo.text = CoreProbe.buildSummary(this)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        game = app.library.primaryGame()
        coreInstalled = CoreProbe.isInstalled(this)
        val installed = game

        when {
            installed == null -> {
                statusLabel.setText(R.string.status_not_imported)
                statusDetail.setText(R.string.status_detail_not_imported)
                statusSerial.text = "REQUIRED · SCUS-97472"
                playHint.setText(R.string.play_hint_not_imported)
                setPlayAvailable(false)
                detailsLink.visibility = TextView.GONE
            }

            coreInstalled -> {
                statusLabel.setText(R.string.status_ready)
                statusDetail.setText(R.string.status_detail_ready)
                statusSerial.text = "${installed.serial} · ${installed.title}"
                playHint.setText(R.string.play_hint_ready)
                setPlayAvailable(true)
                detailsLink.visibility = TextView.GONE
            }

            else -> {
                statusLabel.setText(R.string.status_core_missing)
                statusDetail.setText(R.string.status_detail_core_missing)
                statusSerial.text = "${installed.serial} · ${installed.title}"
                playHint.setText(R.string.play_hint_core_missing)
                setPlayAvailable(false)
                detailsLink.visibility = TextView.VISIBLE
            }
        }
    }

    private fun setPlayAvailable(available: Boolean) {
        playButton.isEnabled = available
        playButton.alpha = if (available) 1f else 0.55f
    }

    private fun play() {
        val installed = game
        when {
            installed == null -> startActivity(Intent(this, ImportActivity::class.java))
            !coreInstalled -> showDetails()
            else -> startActivity(GameActivity.gameIntent(this))
        }
    }

    private fun showDetails() {
        val installed = game ?: return
        val message = buildString {
            appendLine("SERIAL     ${installed.serial}")
            appendLine("TITLE      ${installed.title}")
            appendLine("BOOT       ${installed.bootFile}")
            appendLine("IMAGE      ${GameImporter.formatBytes(installed.imageSize)}")
            appendLine("SHA-256    ${installed.sha256}")
            appendLine("MODULES    ${installed.modules.joinToString(", ")}")
            appendLine("RUNTIME    ${if (coreInstalled) "installed" else "not installed"}")
            appendLine()
            append(
                if (coreInstalled) {
                    "The native runtime is present. Gameplay still requires a verified game core " +
                        "build and on-device testing."
                } else {
                    "Game data is verified, but this build does not include the statically " +
                        "recompiled game core. No emulator or fallback execution path is used. " +
                        "Diagnostics are available under Settings → Advanced."
                }
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.status_core_missing)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.settings) { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
    }
}
