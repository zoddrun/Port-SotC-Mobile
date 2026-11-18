package com.portsotc.mobile.core

import android.content.Context
import android.content.SharedPreferences

/** User-visible configuration. Immutable so changes can be applied atomically. */
data class Settings(
    val resolutionScalePercent: Int = 100,
    val linearFiltering: Boolean = true,
    val audioEnabled: Boolean = true,
    val audioVolume: Float = 0.7f,
    val diagnosticsOverlay: Boolean = false,
    val showStatistics: Boolean = false,
    val touchControlsEnabled: Boolean = true,
    val touchScale: Float = 1.0f,
    val touchAlpha: Float = 0.55f,
    val touchPreset: String = TouchPreset.CLASSIC.id,
    val hideTouchWithGamepad: Boolean = true,
    val cameraSensitivity: Float = 1.0f,
    val invertLookX: Boolean = false,
    val touchDeadzone: Float = 0.08f,
    val padDeadzone: Float = 0.15f,
    val invertLookY: Boolean = false,
    val vibrationEnabled: Boolean = true
) {
    fun sanitized(): Settings = copy(
        resolutionScalePercent = resolutionScalePercent.coerceIn(25, 100),
        audioVolume = audioVolume.coerceIn(0f, 1f),
        touchScale = touchScale.coerceIn(0.6f, 1.6f),
        touchAlpha = touchAlpha.coerceIn(0.15f, 1f),
        touchPreset = TouchPreset.fromId(touchPreset).id,
        cameraSensitivity = cameraSensitivity.coerceIn(0.4f, 2f),
        touchDeadzone = touchDeadzone.coerceIn(0f, 0.3f),
        padDeadzone = padDeadzone.coerceIn(0f, 0.6f)
    )
}

/** Named on-screen control presets. Custom keeps whatever the player edited. */
enum class TouchPreset(val id: String, val label: String) {
    CLASSIC("classic", "Classic"),
    MODERN("modern", "Modern"),
    COMPACT("compact", "Compact"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromId(id: String?): TouchPreset =
            entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

class SettingsStore(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("sotc_settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        resolutionScalePercent = preferences.getInt(KEY_RESOLUTION, 100),
        linearFiltering = preferences.getBoolean(KEY_FILTERING, true),
        audioEnabled = preferences.getBoolean(KEY_AUDIO_ENABLED, true),
        audioVolume = preferences.getFloat(KEY_AUDIO_VOLUME, 0.7f),
        diagnosticsOverlay = preferences.getBoolean(KEY_DIAGNOSTICS_OVERLAY, false),
        showStatistics = preferences.getBoolean(KEY_SHOW_STATISTICS, false),
        touchControlsEnabled = preferences.getBoolean(KEY_TOUCH_ENABLED, true),
        touchScale = preferences.getFloat(KEY_TOUCH_SCALE, 1.0f),
        touchAlpha = preferences.getFloat(KEY_TOUCH_ALPHA, 0.55f),
        touchPreset = preferences.getString(KEY_TOUCH_PRESET, TouchPreset.CLASSIC.id)
            ?: TouchPreset.CLASSIC.id,
        hideTouchWithGamepad = preferences.getBoolean(KEY_HIDE_TOUCH_GAMEPAD, true),
        cameraSensitivity = preferences.getFloat(KEY_CAMERA_SENSITIVITY, 1.0f),
        invertLookX = preferences.getBoolean(KEY_INVERT_LOOK_X, false),
        touchDeadzone = preferences.getFloat(KEY_TOUCH_DEADZONE, 0.08f),
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
            .putString(KEY_TOUCH_PRESET, sanitized.touchPreset)
            .putBoolean(KEY_HIDE_TOUCH_GAMEPAD, sanitized.hideTouchWithGamepad)
            .putFloat(KEY_CAMERA_SENSITIVITY, sanitized.cameraSensitivity)
            .putBoolean(KEY_INVERT_LOOK_X, sanitized.invertLookX)
            .putFloat(KEY_TOUCH_DEADZONE, sanitized.touchDeadzone)
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
        const val KEY_TOUCH_PRESET = "touch_preset"
        const val KEY_HIDE_TOUCH_GAMEPAD = "hide_touch_with_gamepad"
        const val KEY_CAMERA_SENSITIVITY = "camera_sensitivity"
        const val KEY_INVERT_LOOK_X = "invert_look_x"
        const val KEY_TOUCH_DEADZONE = "touch_deadzone"
        const val KEY_PAD_DEADZONE = "pad_deadzone"
        const val KEY_INVERT_LOOK_Y = "invert_look_y"
        const val KEY_VIBRATION = "vibration_enabled"
    }
}
