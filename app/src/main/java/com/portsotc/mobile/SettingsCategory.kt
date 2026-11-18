package com.portsotc.mobile

/** Settings destinations shown on the settings index. */
enum class SettingsCategory(val key: String, val title: String, val summary: String) {
    GENERAL("general", "General", "Application, orientation, and interface"),
    GRAPHICS("graphics", "Graphics", "Render scale and image filtering"),
    PERFORMANCE("performance", "Performance", "Performance overlay and presets"),
    AUDIO("audio", "Audio", "Output device and volume"),
    CONTROLS("controls", "Controls", "Touch presets, camera, and layout"),
    STORAGE("storage", "Storage", "Imported game data and saves"),
    ADVANCED("advanced", "Advanced", "Diagnostics, capabilities, and reports");

    companion object {
        fun fromKey(key: String?): SettingsCategory =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}
