package com.portsotc.mobile.core

/**
 * Real graphics presets.
 *
 * Each preset is a combination of settings that the native host actually
 * honours, so selecting one changes behaviour instead of only a label. The
 * game's own logic and timeline are never modified by a preset.
 */
enum class GraphicsPreset(
    val id: String,
    val label: String,
    val scalePercent: Int,
    val adaptiveResolution: Boolean,
    val frameLimitFps: Int
) {
    BATTERY("battery", "Battery saver", 50, true, 30),
    BALANCED("balanced", "Balanced", 75, true, 60),
    PERFORMANCE("performance", "Performance", 100, false, 0);

    fun apply(settings: Settings): Settings = settings.copy(
        resolutionScalePercent = scalePercent,
        adaptiveResolution = adaptiveResolution,
        frameLimitFps = frameLimitFps
    )

    companion object {
        fun fromId(id: String?): GraphicsPreset? = entries.firstOrNull { it.id == id }

        /** The preset that matches [settings] exactly, or null for a custom mix. */
        fun matching(settings: Settings): GraphicsPreset? = entries.firstOrNull {
            settings.resolutionScalePercent == it.scalePercent &&
                settings.adaptiveResolution == it.adaptiveResolution &&
                settings.frameLimitFps == it.frameLimitFps
        }
    }
}
