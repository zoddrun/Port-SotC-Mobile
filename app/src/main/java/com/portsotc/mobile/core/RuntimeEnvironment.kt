package com.portsotc.mobile.core

import java.util.Locale

/**
 * The environment variables the recompiled runtime reads.
 *
 * Android starts a `NativeActivity` without argv and the runner library is
 * loaded by the framework, so this is the only channel into the runtime: the
 * app publishes these values with `NativeCore.setEnvironment` before
 * `super.onCreate` loads `libps2EntryRunner.so`. Every name here matches a
 * `std::getenv` in the runner (patches 0004 and 0005); a name that drifts is a
 * silently ignored setting, so keep the two sides in step.
 */
object RuntimeEnvironment {

    /** Boot executable, read by patch 0004 in `main.cpp`. */
    const val BOOT_PATH = "SOTC_BOOT_ELF"

    /** 0 removes the on-screen controls and their input entirely. */
    const val TOUCH_OVERLAY = "SOTC_TOUCH_OVERLAY"

    /** `classic`, `modern` or `compact` (patch 0005 maps anything else to classic). */
    const val TOUCH_PRESET = "SOTC_TOUCH_PRESET"

    /** Hides the on-screen controls while a gamepad is connected. */
    const val TOUCH_HIDE_WITH_GAMEPAD = "SOTC_TOUCH_HIDE_WITH_GAMEPAD"

    /** Multiplier on the overlay's control sizes (the runner clamps to 0.6..1.4). */
    const val TOUCH_SCALE = "SOTC_TOUCH_SCALE"

    /** Opacity of the control bodies (the runner clamps to 0.2..1.0). */
    const val TOUCH_ALPHA = "SOTC_TOUCH_ALPHA"

    /** Radial dead zone of the touch sticks (the runner clamps to 0..0.6). */
    const val TOUCH_DEADZONE = "SOTC_TOUCH_DEADZONE"

    /** Multiplier on the right (look) stick (the runner clamps to 0.2..3). */
    const val LOOK_SENSITIVITY = "SOTC_LOOK_SENSITIVITY"

    /** Inverts the right stick's horizontal axis. */
    const val INVERT_LOOK_X = "SOTC_INVERT_LOOK_X"

    /** Master volume for the runtime's audio, 0..1 (`0` also mutes it). */
    const val AUDIO_VOLUME = "SOTC_AUDIO_VOLUME"

    /**
     * Every variable for one session: the boot executable (when the disc tree
     * is ready) and the control settings. A null or blank [bootPath] leaves
     * the boot variable out, so the runtime reports the missing path itself
     * instead of being handed an unusable one.
     */
    fun variables(bootPath: String?, settings: Settings): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        if (!bootPath.isNullOrBlank()) {
            values[BOOT_PATH] = bootPath
        }
        values.putAll(controls(settings))
        return values
    }

    /**
     * The touch and look knobs, as the runner's parser expects them: integers
     * for flags and decimals with a dot for numbers, regardless of the
     * device's locale (`std::atof` stops at a comma).
     */
    fun controls(settings: Settings): Map<String, String> = linkedMapOf(
        TOUCH_OVERLAY to flag(settings.touchControlsEnabled),
        TOUCH_PRESET to TouchPreset.fromId(settings.touchPreset).id,
        TOUCH_HIDE_WITH_GAMEPAD to flag(settings.hideTouchWithGamepad),
        TOUCH_SCALE to decimal(settings.touchScale),
        TOUCH_ALPHA to decimal(settings.touchAlpha),
        TOUCH_DEADZONE to decimal(settings.touchDeadzone),
        LOOK_SENSITIVITY to decimal(settings.cameraSensitivity),
        INVERT_LOOK_X to flag(settings.invertLookX),
        AUDIO_VOLUME to decimal(if (settings.audioEnabled) settings.audioVolume else 0f)
    )

    /**
     * Sends every entry through [publisher] (`NativeCore.setEnvironment` in
     * the app). Returns the names that could not be published, so the caller
     * can log exactly what the runtime will fall back on.
     */
    fun publish(
        values: Map<String, String>,
        publisher: (name: String, value: String) -> Boolean
    ): List<String> = values.filterNot { (name, value) -> publisher(name, value) }.keys.toList()

    private fun flag(enabled: Boolean): String = if (enabled) "1" else "0"

    private fun decimal(value: Float): String = String.format(Locale.US, "%.3f", value)
}
