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
import com.portsotc.mobile.nativebridge.NativeCore

class MainActivity : Activity() {

    private lateinit var app: SotcApplication
    private lateinit var statusLabel: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusMeta: TextView
    private lateinit var buildInfo: TextView

    private var game: InstalledGame? = null
    private var coreInstalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        app = application as SotcApplication

        statusLabel = findViewById(R.id.status_label)
        statusDetail = findViewById(R.id.status_detail)
        statusMeta = findViewById(R.id.status_meta)
        buildInfo = findViewById(R.id.build_info)

        findViewById<Button>(R.id.button_play).setOnClickListener { play() }
        findViewById<Button>(R.id.button_import).setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }
        findViewById<Button>(R.id.button_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.button_controller).setOnClickListener {
            startActivity(Intent(this, ControllerSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.button_diagnostics).setOnClickListener {
            startActivity(GameActivity.diagnosticsIntent(this))
        }

        buildInfo.text = buildString {
            appendLine(CoreProbe.buildSummary(this@MainActivity))
            append("Native engine: ")
            append(
                if (NativeCore.available) {
                    "loaded"
                } else {
                    "unavailable (${NativeCore.loadError})"
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        game = app.library.primaryGame()
        coreInstalled = CoreProbe.isInstalled(this)
        val installed = game
        if (installed == null) {
            statusLabel.text = getString(R.string.status_not_imported)
            statusDetail.text =
                "Import your own legally obtained Shadow of the Colossus (USA) disc image. " +
                    "The application validates the disc structure before storing it."
            statusMeta.text = "REQUIRED · SCUS-97472 · 2048-BYTE ISO"
            return
        }

        if (coreInstalled) {
            statusLabel.text = getString(R.string.status_ready)
            statusDetail.text =
                "Disc image and engine modules are verified, and a native game core library " +
                    "is present. Compatibility is not guaranteed until gameplay is verified on this device."
            statusMeta.text = buildString {
                appendLine("SERIAL     ${installed.serial}")
                appendLine("TITLE      ${installed.title}")
                appendLine("IMAGE      ${GameImporter.formatBytes(installed.imageSize)}")
                appendLine("SHA-256    ${installed.sha256.take(24)}…")
                append("MODULES    ${installed.modules.joinToString(", ")}")
            }
        } else {
            statusLabel.text = getString(R.string.status_core_missing)
            statusDetail.text =
                "The disc image and engine modules are verified. The statically recompiled game " +
                    "core (libsotc_game.so) is not present in this build, so gameplay is not available. " +
                    "Engine diagnostics can still be opened."
            statusMeta.text = buildString {
                appendLine("SERIAL     ${installed.serial}")
                appendLine("TITLE      ${installed.title}")
                appendLine("IMAGE      ${GameImporter.formatBytes(installed.imageSize)}")
                append("SHA-256    ${installed.sha256.take(24)}…")
            }
        }
    }

    private fun play() {
        val installed = game
        when {
            installed == null -> startActivity(Intent(this, ImportActivity::class.java))
            !coreInstalled -> showCoreMissingDialog(installed)
            else -> startActivity(GameActivity.gameIntent(this))
        }
    }

    private fun showCoreMissingDialog(installed: InstalledGame) {
        AlertDialog.Builder(this)
            .setTitle("Native game core not built")
            .setMessage(
                "The imported ${installed.serial} data passed validation, but this build does not " +
                    "include a statically recompiled game core (libsotc_game.so).\n\n" +
                    "Gameplay cannot start without it. No emulator or fallback execution path is used. " +
                    "You can open the native engine diagnostics to verify rendering and audio on this device."
            )
            .setPositiveButton("OPEN DIAGNOSTICS") { _, _ ->
                startActivity(GameActivity.diagnosticsIntent(this))
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
