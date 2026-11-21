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
    val vibrationEnabled: Boolean = true,
    val adaptiveResolution: Boolean = true,
    val frameLimitFps: Int = 0,
    val forceEs2: Boolean = false,
    val hdShaderPreset: String = HdPresetId.BALANCED.id,
    val hdShaderOverrides: HdShaderOverrides = HdShaderOverrides(),
    val touchGripMode: String = TouchGripMode.SHOULDER.id
) {
    fun sanitized(): Settings = copy(
        resolutionScalePercent = resolutionScalePercent.coerceIn(25, 100),
        audioVolume = audioVolume.coerceIn(0f, 1f),
        touchScale = touchScale.coerceIn(0.6f, 1.6f),
        touchAlpha = touchAlpha.coerceIn(0.15f, 1f),
        touchPreset = TouchPreset.fromId(touchPreset).id,
        cameraSensitivity = cameraSensitivity.coerceIn(0.4f, 2f),
        touchDeadzone = touchDeadzone.coerceIn(0f, 0.3f),
        padDeadzone = padDeadzone.coerceIn(0f, 0.6f),
        frameLimitFps = if (frameLimitFps == 0 || frameLimitFps == 30 || frameLimitFps == 60) {
            frameLimitFps
        } else {
            0
        },
        hdShaderPreset = HdPresetId.fromId(hdShaderPreset).id,
        hdShaderOverrides = hdShaderOverrides.sanitized(),
        touchGripMode = TouchGripMode.fromId(touchGripMode).id
    )
}

/** Preset identifiers shipped in the original HD shader pack. */
enum class HdPresetId(val id: String, val label: String) {
    BATTERY("battery", "Battery Saver"),
    BALANCED("balanced", "Balanced HD"),
    ULTRA("ultra", "Ultra HD");

    companion object {
        fun fromId(id: String?): HdPresetId = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/** Optional shoulder-button grips for the right-half look camera. */
enum class TouchGripMode(val id: String, val label: String) {
    SHOULDER("shoulder", "Shoulder"),
    CLAW("claw", "Claw"),
    BRIDGE("bridge", "Bridge");

    companion object {
        fun fromId(id: String?): TouchGripMode = entries.firstOrNull { it.id == id } ?: SHOULDER
    }
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
        vibrationEnabled = preferences.getBoolean(KEY_VIBRATION, true),
        adaptiveResolution = preferences.getBoolean(KEY_ADAPTIVE_RESOLUTION, true),
        frameLimitFps = preferences.getInt(KEY_FRAME_LIMIT_FPS, 0),
        forceEs2 = preferences.getBoolean(KEY_FORCE_ES2, false),
        hdShaderPreset = preferences.getString(KEY_HD_SHADER_PRESET, HdPresetId.BALANCED.id)
            ?: HdPresetId.BALANCED.id,
        hdShaderOverrides = HdShaderOverrides(
            resolutionScale = nullableFloat(KEY_HD_OVERRIDE_RESOLUTION)?.toDouble(),
            rcasSharpness = nullableFloat(KEY_HD_OVERRIDE_RCAS)?.toDouble(),
            anisotropyLevel = if (preferences.contains(KEY_HD_OVERRIDE_ANISO)) {
                preferences.getInt(KEY_HD_OVERRIDE_ANISO, 4)
            } else {
                null
            },
            bloomIntensity = nullableFloat(KEY_HD_OVERRIDE_BLOOM)?.toDouble(),
            fxaaEnabled = nullableBoolean(KEY_HD_OVERRIDE_FXAA),
            widescreen16x9 = nullableBoolean(KEY_HD_OVERRIDE_WIDESCREEN)
        ),
        touchGripMode = preferences.getString(KEY_TOUCH_GRIP_MODE, TouchGripMode.SHOULDER.id)
            ?: TouchGripMode.SHOULDER.id
    ).sanitized()

    fun save(settings: Settings) {
        val sanitized = settings.sanitized()
        val editor = preferences.edit()
        editor
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
            .putBoolean(KEY_ADAPTIVE_RESOLUTION, sanitized.adaptiveResolution)
            .putInt(KEY_FRAME_LIMIT_FPS, sanitized.frameLimitFps)
            .putBoolean(KEY_FORCE_ES2, sanitized.forceEs2)
            .putString(KEY_HD_SHADER_PRESET, sanitized.hdShaderPreset)
            .putString(KEY_TOUCH_GRIP_MODE, sanitized.touchGripMode)
        putNullableFloat(editor, KEY_HD_OVERRIDE_RESOLUTION,
            sanitized.hdShaderOverrides.resolutionScale?.toFloat())
        putNullableFloat(editor, KEY_HD_OVERRIDE_RCAS,
            sanitized.hdShaderOverrides.rcasSharpness?.toFloat())
        putNullableInt(editor, KEY_HD_OVERRIDE_ANISO,
            sanitized.hdShaderOverrides.anisotropyLevel)
        putNullableFloat(editor, KEY_HD_OVERRIDE_BLOOM,
            sanitized.hdShaderOverrides.bloomIntensity?.toFloat())
        putNullableBoolean(editor, KEY_HD_OVERRIDE_FXAA,
            sanitized.hdShaderOverrides.fxaaEnabled)
        putNullableBoolean(editor, KEY_HD_OVERRIDE_WIDESCREEN,
            sanitized.hdShaderOverrides.widescreen16x9)
        editor.apply()
    }

    private fun nullableFloat(key: String): Float? =
        if (preferences.contains(key)) preferences.getFloat(key, 0f) else null

    private fun nullableBoolean(key: String): Boolean? =
        if (preferences.contains(key)) preferences.getBoolean(key, false) else null

    private fun putNullableFloat(editor: SharedPreferences.Editor, key: String, value: Float?) {
        if (value == null) editor.remove(key) else editor.putFloat(key, value)
    }

    private fun putNullableInt(editor: SharedPreferences.Editor, key: String, value: Int?) {
        if (value == null) editor.remove(key) else editor.putInt(key, value)
    }

    private fun putNullableBoolean(editor: SharedPreferences.Editor, key: String, value: Boolean?) {
        if (value == null) editor.remove(key) else editor.putBoolean(key, value)
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
        const val KEY_ADAPTIVE_RESOLUTION = "adaptive_resolution"
        const val KEY_FRAME_LIMIT_FPS = "frame_limit_fps"
        const val KEY_FORCE_ES2 = "force_opengl_es2"
        const val KEY_HD_SHADER_PRESET = "hd_shader_preset"
        const val KEY_HD_OVERRIDE_RESOLUTION = "hd_override_resolution"
        const val KEY_HD_OVERRIDE_RCAS = "hd_override_rcas"
        const val KEY_HD_OVERRIDE_ANISO = "hd_override_anisotropy"
        const val KEY_HD_OVERRIDE_BLOOM = "hd_override_bloom"
        const val KEY_HD_OVERRIDE_FXAA = "hd_override_fxaa"
        const val KEY_HD_OVERRIDE_WIDESCREEN = "hd_override_widescreen"
        const val KEY_TOUCH_GRIP_MODE = "touch_grip_mode"
    }
}
