package com.portsotc.mobile.core

import android.content.Context
import android.content.SharedPreferences

/** User-visible configuration. Immutable so changes can be applied atomically. */
data class Settings(
    val resolutionScalePercent: Int = 100,
    val linearFiltering: Boolean = true,
    val audioEnabled: Boolean = true,
    val audioVolume: Float = 0.7f,
    val diagnosticsOverlay: Boolean = true,
    val showStatistics: Boolean = true,
    val touchControlsEnabled: Boolean = true,
    val touchScale: Float = 1.0f,
    val touchAlpha: Float = 0.55f,
    val padDeadzone: Float = 0.15f,
    val invertLookY: Boolean = false,
    val vibrationEnabled: Boolean = true
) {
    fun sanitized(): Settings = copy(
        resolutionScalePercent = resolutionScalePercent.coerceIn(25, 100),
        audioVolume = audioVolume.coerceIn(0f, 1f),
        touchScale = touchScale.coerceIn(0.6f, 1.6f),
        touchAlpha = touchAlpha.coerceIn(0.15f, 1f),
        padDeadzone = padDeadzone.coerceIn(0f, 0.6f)
    )
}

class SettingsStore(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("sotc_settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        resolutionScalePercent = preferences.getInt(KEY_RESOLUTION, 100),
        linearFiltering = preferences.getBoolean(KEY_FILTERING, true),
        audioEnabled = preferences.getBoolean(KEY_AUDIO_ENABLED, true),
        audioVolume = preferences.getFloat(KEY_AUDIO_VOLUME, 0.7f),
        diagnosticsOverlay = preferences.getBoolean(KEY_DIAGNOSTICS_OVERLAY, true),
        showStatistics = preferences.getBoolean(KEY_SHOW_STATISTICS, true),
        touchControlsEnabled = preferences.getBoolean(KEY_TOUCH_ENABLED, true),
        touchScale = preferences.getFloat(KEY_TOUCH_SCALE, 1.0f),
        touchAlpha = preferences.getFloat(KEY_TOUCH_ALPHA, 0.55f),
        padDeadzone = preferences.getFloat(KEY_PAD_DEADZONE, 0.15f),
        invertLookY = preferences.getBoolean(KEY_INVERT_LOOK_Y, false),
        vibrationEnabled = preferences.getBoolean(KEY_VIBRATION, true)
    ).sanitized()

    fun save(settings: Settings) {
        val sanitized = settings.sanitized()
        preferences.edit()
            .putInt(KEY_RESOLUTION, sanitized.resolutionScalePercent)
            .putBoolean(KEY_FILTERING, sanitized.linearFiltering)
            .putBoolean(KEY_AUDIO_ENABLED, sanitized.audioEnabled)
            .putFloat(KEY_AUDIO_VOLUME, sanitized.audioVolume)
            .putBoolean(KEY_DIAGNOSTICS_OVERLAY, sanitized.diagnosticsOverlay)
            .putBoolean(KEY_SHOW_STATISTICS, sanitized.showStatistics)
            .putBoolean(KEY_TOUCH_ENABLED, sanitized.touchControlsEnabled)
            .putFloat(KEY_TOUCH_SCALE, sanitized.touchScale)
            .putFloat(KEY_TOUCH_ALPHA, sanitized.touchAlpha)
            .putFloat(KEY_PAD_DEADZONE, sanitized.padDeadzone)
            .putBoolean(KEY_INVERT_LOOK_Y, sanitized.invertLookY)
            .putBoolean(KEY_VIBRATION, sanitized.vibrationEnabled)
            .apply()
    }

    fun edit(transform: (Settings) -> Settings): Settings {
        val updated = transform(load()).sanitized()
        save(updated)
        return updated
    }

    private companion object {
        const val KEY_RESOLUTION = "resolution_scale_percent"
        const val KEY_FILTERING = "linear_filtering"
        const val KEY_AUDIO_ENABLED = "audio_enabled"
        const val KEY_AUDIO_VOLUME = "audio_volume"
        const val KEY_DIAGNOSTICS_OVERLAY = "diagnostics_overlay"
        const val KEY_SHOW_STATISTICS = "show_statistics"
        const val KEY_TOUCH_ENABLED = "touch_controls_enabled"
        const val KEY_TOUCH_SCALE = "touch_scale"
        const val KEY_TOUCH_ALPHA = "touch_alpha"
        const val KEY_PAD_DEADZONE = "pad_deadzone"
        const val KEY_INVERT_LOOK_Y = "invert_look_y"
        const val KEY_VIBRATION = "vibration_enabled"
    }
}
