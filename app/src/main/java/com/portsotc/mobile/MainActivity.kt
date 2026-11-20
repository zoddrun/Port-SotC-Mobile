package com.portsotc.mobile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.portsotc.mobile.core.CoreProbe
import com.portsotc.mobile.core.DiscTree
import com.portsotc.mobile.core.GameImporter
import com.portsotc.mobile.core.HdPresetId
import com.portsotc.mobile.core.InstalledGame
import com.portsotc.mobile.core.SotcLauncherState
import com.portsotc.mobile.ui.SotcRefinedLauncherScreen
import com.portsotc.mobile.ui.SotcTheme
import com.portsotc.mobile.core.SotcLauncherModel
import androidx.activity.compose.setContent

/**
 * Launcher screen.
 *
 * The composition keeps the same small information architecture as before:
 * branding, one primary action, and the three secondary destinations a player
 * actually needs. Technical details stay in a dialog under Settings -> Advanced.
 */
class MainActivity : ComponentActivity() {

    private lateinit var app: SotcApplication
    private var launcherState by mutableStateOf<SotcLauncherState?>(null)
    private var game: InstalledGame? = null
    private var runtimeInstalled = false
    private var discTreeReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as SotcApplication

        setContent {
            SotcTheme {
                launcherState?.let { state ->
                    SotcRefinedLauncherScreen(
                        state = state,
                        onPlay = { play() },
                        onImport = {
                            startActivity(Intent(this, ImportActivity::class.java))
                        },
                        onSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onControls = {
                            startActivity(Intent(this, ControllerSettingsActivity::class.java))
                        },
                        onHdSettings = {
                            startActivity(
                                SettingsCategoryActivity.intent(this, SettingsCategory.GRAPHICS)
                            )
                        },
                        onDetails = { showDetails() },
                        onPresetChange = { presetId ->
                            app.selectHdShaderPreset(presetId)
                            refreshStatus()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        game = app.library.primaryGame()
        runtimeInstalled = CoreProbe.isRuntimeInstalled(this)
        discTreeReady = game?.isDiscTreeReady() == true
        val installed = game
        val preset = try {
            HdPresetId.fromId(app.settingsStore.load().hdShaderPreset).id
        } catch (_: Throwable) {
            HdPresetId.BALANCED.id
        }
        launcherState = SotcLauncherState(
            gameInstalled = installed != null,
            runtimeInstalled = runtimeInstalled,
            discTreeReady = discTreeReady,
            shaderPackReady = app.shaderPackReady,
            shaderPackDetail = app.shaderPackDetail,
            hdPresetId = preset,
            buildSummary = CoreProbe.buildSummary(this),
            serial = installed?.serial,
            title = installed?.title
        )
    }

    private fun play() {
        val installed = game
        when {
            installed == null -> startActivity(Intent(this, ImportActivity::class.java))
            !runtimeInstalled -> showDetails()
            !installed.isDiscTreeReady() ->
                startActivity(SettingsCategoryActivity.intent(this, SettingsCategory.STORAGE))
            else -> startActivity(Intent(this, GameNativeActivity::class.java))
        }
    }

    private fun showDetails() {
        val installed = game ?: return
        val tree = DiscTree.read(installed.directory)
        val message = buildString {
            appendLine("SERIAL     ${installed.serial}")
            appendLine("TITLE      ${installed.title}")
            appendLine("BOOT       ${installed.bootFile}")
            appendLine("IMAGE      ${GameImporter.formatBytes(installed.imageSize)}")
            appendLine("SHA-256    ${installed.sha256}")
            appendLine("MODULES    ${installed.modules.joinToString(", ")}")
            appendLine("RUNTIME    ${if (runtimeInstalled) "installed" else "not installed"}")
            appendLine(
                "DISC TREE  " + if (discTreeReady && tree != null) {
                    "${tree.fileCount} files · ${GameImporter.formatBytes(tree.byteCount)}"
                } else {
                    "not prepared"
                }
            )
            appendLine()
            append(
                if (runtimeInstalled) {
                    "The native runtime is packaged with this build. The extracted disc tree " +
                        "is required before the first launch."
                } else {
                    "Game data is verified, but this build does not include the statically " +
                        "recompiled game runtime. No emulator or fallback execution path is used. " +
                        "Diagnostics are available under Settings -> Advanced."
                }
            )
        }
        AlertDialog.Builder(this)
            .setTitle("TECHNICAL DETAILS")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("SETTINGS") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
    }
}
