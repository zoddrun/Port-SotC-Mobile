package com.portsotc.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.portsotc.mobile.core.HdPresetId
import com.portsotc.mobile.core.HdShaderOverrides
import com.portsotc.mobile.ui.InGameQuickSettingsContent
import com.portsotc.mobile.ui.QuickSettingsUiState
import com.portsotc.mobile.ui.SotcTheme

/**
 * In-session quick settings drawer.
 *
 * It edits the same [com.portsotc.mobile.core.Settings] store used by the
 * launcher and reports the resulting intent back to the host so the host can
 * refresh its own view. No value here is presented as a live native hot-reload.
 */
class QuickSettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EXIT = "com.portsotc.mobile.extra.QUICK_EXIT"
        const val EXTRA_TOUCH_RESTORED = "com.portsotc.mobile.extra.TOUCH_RESTORED"

        private const val EXTRA_GAMEPAD = "com.portsotc.mobile.extra.GAMEPAD_CONNECTED"
        private const val EXTRA_TOUCH_RESTORED_IN = "com.portsotc.mobile.extra.TOUCH_RESTORED_INPUT"
        private const val EXTRA_PACK_READY = "com.portsotc.mobile.extra.PACK_READY"
        private const val EXTRA_PACK_DETAIL = "com.portsotc.mobile.extra.PACK_DETAIL"

        fun intent(
            context: android.content.Context,
            gamepadConnected: Boolean,
            touchRestored: Boolean,
            shaderPackReady: Boolean,
            shaderPackDetail: String
        ): Intent = Intent(context, QuickSettingsActivity::class.java)
            .putExtra(EXTRA_GAMEPAD, gamepadConnected)
            .putExtra(EXTRA_TOUCH_RESTORED_IN, touchRestored)
            .putExtra(EXTRA_PACK_READY, shaderPackReady)
            .putExtra(EXTRA_PACK_DETAIL, shaderPackDetail)
    }

    private lateinit var app: SotcApplication
    private var gamepadConnected = false
    private var touchRestored = false
    private var shaderPackReady = false
    private var shaderPackDetail = "not installed"
    private var uiState by mutableStateOf<QuickSettingsUiState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as SotcApplication
        gamepadConnected = intent.getBooleanExtra(EXTRA_GAMEPAD, false)
        touchRestored = intent.getBooleanExtra(EXTRA_TOUCH_RESTORED_IN, false)
        shaderPackReady = intent.getBooleanExtra(EXTRA_PACK_READY, app.shaderPackReady)
        shaderPackDetail = intent.getStringExtra(EXTRA_PACK_DETAIL) ?: app.shaderPackDetail

        setContent {
            SotcTheme {
                uiState?.let { state ->
                    InGameQuickSettingsContent(
                        state = state,
                        onPresetChange = { id -> changePreset(id) },
                        onOverride = { transform -> applyOverride(transform) },
                        onClearOverrides = { app.clearHdShaderOverrides() },
                        onFrameLimitChange = { limit ->
                            app.settingsStore.edit { it.copy(frameLimitFps = limit) }
                            refresh()
                        },
                        onTouchScaleChange = { scale ->
                            app.settingsStore.edit { it.copy(touchScale = scale) }
                            refresh()
                        },
                        onTouchAlphaChange = { alpha ->
                            app.settingsStore.edit { it.copy(touchAlpha = alpha) }
                            refresh()
                        },
                        onGripModeChange = { id ->
                            app.settingsStore.edit { it.copy(touchGripMode = id) }
                            refresh()
                        },
                        onHideTouchWithGamepadChange = { hide ->
                            app.settingsStore.edit { it.copy(hideTouchWithGamepad = hide) }
                            refresh()
                        },
                        onRestoreTouch = {
                            touchRestored = true
                            finishWith(restored = true)
                        },
                        onEditLayout = {
                            startActivity(Intent(this, TouchLayoutEditorActivity::class.java))
                        },
                        onExit = {
                            finishWith(exit = true)
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun changePreset(id: String) {
        app.selectHdShaderPreset(id)
        shaderPackReady = app.shaderPackReady
        shaderPackDetail = app.shaderPackDetail
        refresh()
    }

    private fun applyOverride(transform: (HdShaderOverrides) -> HdShaderOverrides) {
        app.updateHdShaderOverrides(transform)
        shaderPackReady = app.shaderPackReady
        shaderPackDetail = app.shaderPackDetail
        refresh()
    }

    private fun refresh() {
        val settings = app.settingsStore.load()
        val values = effectiveShaderValues(settings.hdShaderPreset, settings.hdShaderOverrides)
        uiState = QuickSettingsUiState(
            settings = settings,
            shaderPackReady = shaderPackReady,
            shaderPackDetail = shaderPackDetail,
            gamepadConnected = gamepadConnected,
            touchRestored = touchRestored,
            effectiveResolutionScale = values.resolution,
            effectiveRcas = values.rcas,
            effectiveBloom = values.bloom,
            effectiveAnisotropy = values.anisotropy
        )
    }

    private fun effectiveShaderValues(
        presetId: String,
        overrides: HdShaderOverrides
    ): EffectiveShaderValues = try {
        val manifest = app.shaderPackInstaller.readManifest()
        val preset = manifest.presets[HdPresetId.fromId(presetId).id]
            ?: manifest.presets.getValue(HdPresetId.BALANCED.id)
        val safe = overrides.sanitized()
        EffectiveShaderValues(
            resolution = (safe.resolutionScale ?: preset.resolutionScale).toFloat(),
            rcas = (safe.rcasSharpness ?: preset.rcasSharpness).toFloat(),
            bloom = (safe.bloomIntensity ?: preset.bloomIntensity).toFloat(),
            anisotropy = (safe.anisotropyLevel ?: preset.anisotropyLevel).toFloat()
        )
    } catch (_: Throwable) {
        val safe = overrides.sanitized()
        EffectiveShaderValues(
            resolution = (safe.resolutionScale ?: 1.0).toFloat(),
            rcas = (safe.rcasSharpness ?: 0.2).toFloat(),
            bloom = (safe.bloomIntensity ?: 0.26).toFloat(),
            anisotropy = (safe.anisotropyLevel ?: 4).toFloat()
        )
    }

    private fun finishWith(exit: Boolean = false, restored: Boolean = false) {
        val result = Intent().apply {
            putExtra(EXTRA_EXIT, exit)
            putExtra(EXTRA_TOUCH_RESTORED, restored)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private data class EffectiveShaderValues(
        val resolution: Float,
        val rcas: Float,
        val bloom: Float,
        val anisotropy: Float
    )
}
