package com.portsotc.mobile.core

import java.util.Locale
import kotlin.math.max

/**
 * The environment variables the recompiled runtime reads.
 *
 * Android starts a `NativeActivity` without argv and the runner library is
 * loaded by the framework, so this is the only channel into the runtime: the
 * app publishes these values with `NativeCore.setEnvironment` before
 * `super.onCreate` loads `libps2EntryRunner.so`. Every name here matches a
 * `std::getenv` in the runner (patches 0004, 0005, 0006 and 0009, plus knobs
 * already present upstream); a name that drifts is a silently ignored setting,
 * so keep the two sides in step.
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

    /** Sigil-style HUD requested by the touch overlay patch. */
    const val TOUCH_STYLE = "SOTC_TOUCH_STYLE"

    /** Shoulder, claw or bridge grip for the R1-like camera hold. */
    const val TOUCH_GRIP_MODE = "SOTC_TOUCH_GRIP_MODE"

    /** `1` keeps the left stick centered on the touch-down point. */
    const val TOUCH_FLOATING_STICK = "SOTC_TOUCH_FLOATING_STICK"

    /** Look region: `right-half` or `full-screen`. */
    const val TOUCH_LOOK_REGION = "SOTC_TOUCH_LOOK_REGION"

    /** Master volume for the runtime's audio, 0..1 (`0` also mutes it). */
    const val AUDIO_VOLUME = "SOTC_AUDIO_VOLUME"

    /**
     * Presentation cap for the raylib-owned loop: `0`, `30` or `60` fps.
     * Published for the proposed runner patch 0009; `0` means uncapped. This
     * never changes the PS2 guest time step.
     */
    const val FRAME_LIMIT = "SOTC_FRAME_LIMIT"

    /** Runner-owned render-scale adaptation: `1` on, `0` off (patch 0009). */
    const val ADAPTIVE_RESOLUTION = "SOTC_ADAPTIVE_RESOLUTION"

    /** Runner render target scale; celosia clamps it to 0.5..2. */
    const val RENDER_SCALE = "SOTC_RENDER_SCALE"

    /** Existing runner on-screen performance overlay: `1` on, `0` off. */
    const val PERF_OVERLAY = "SOTC_PERF_OVERLAY"

    /**
     * Existing runner diagnostic log gate. The name only has to be present;
     * it enables the five-second `[stats]` and `[pace]` records in the log
     * file that [GameNativeActivity] redirects the runtime output to.
     */
    const val STATS = "PS2X_STATS"

    /** Directory containing the verified, extracted HD shader pack. */
    const val HD_SHADER_PACK = "SOTC_HD_SHADER_PACK"

    /** Active presets id from the pack manifest: battery, balanced or ultra. */
    const val HD_SHADER_PRESET = "SOTC_HD_SHADER_PRESET"

    /** Version from the pack manifest; the runner reports it in `[hd-shader]`. */
    const val HD_SHADER_VERSION = "SOTC_HD_SHADER_VERSION"

    /** Lowest render scale the celosia runner accepts, from `native_tuning.h`. */
    const val MIN_RUNNER_RENDER_SCALE = 0.5f

    /**
     * Every variable for one session: the boot executable (when the disc tree
     * is ready) and the control and graphics settings. A null or blank
     * [bootPath] leaves the boot variable out, so the runtime reports the
     * missing path itself instead of being handed an unusable one.
     */
    fun variables(
        bootPath: String?,
        settings: Settings,
        extras: Map<String, String> = emptyMap()
    ): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        if (!bootPath.isNullOrBlank()) {
            values[BOOT_PATH] = bootPath
        }
        values.putAll(controls(settings))
        values.putAll(graphics(settings))
        values.putAll(extras)
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
        TOUCH_STYLE to "sigil",
        TOUCH_GRIP_MODE to TouchGripMode.fromId(settings.touchGripMode).id,
        TOUCH_FLOATING_STICK to "1",
        TOUCH_LOOK_REGION to "right-half",
        AUDIO_VOLUME to decimal(if (settings.audioEnabled) settings.audioVolume else 0f)
    )

    /**
     * Graphics and pacing knobs for the real game loop. The settings screen
     * permits 25% rendering, but celosia clamps the runner scale at 0.5, so
     * the published value is clamped here too: reporting 0.25 would be a
     * claim the runtime cannot honour.
     *
     * `frameLimitFps` is already restricted to 0, 30 or 60 by
     * [Settings.sanitized]. All values are read once at runner startup, so a
     * change takes effect on the next game session.
     */
    fun graphics(settings: Settings): Map<String, String> {
        val scale = max(MIN_RUNNER_RENDER_SCALE, settings.resolutionScalePercent / 100f)
        val values = linkedMapOf(
            FRAME_LIMIT to settings.frameLimitFps.toString(),
            ADAPTIVE_RESOLUTION to flag(settings.adaptiveResolution),
            RENDER_SCALE to decimal(scale),
            PERF_OVERLAY to flag(settings.showStatistics)
        )
        if (settings.showStatistics) {
            values[STATS] = "1"
        }
        return values
    }

    /**
     * Shader-pack values for the runner's `[hd-shader]` record. A missing
     * installed directory is omitted rather than reported as a ready pack.
     */
    fun hdShader(packDirectory: String?, preset: String, version: String): Map<String, String> {
        if (packDirectory.isNullOrBlank()) return emptyMap()
        return linkedMapOf(
            HD_SHADER_PACK to packDirectory,
            HD_SHADER_PRESET to HdPresetId.fromId(preset).id,
            HD_SHADER_VERSION to version
        )
    }

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
