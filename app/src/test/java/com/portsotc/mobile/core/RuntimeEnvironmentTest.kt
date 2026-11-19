package com.portsotc.mobile.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class RuntimeEnvironmentTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun variablesCarryTheBootPathAndTheControls() {
        val settings = Settings()

        val values = RuntimeEnvironment.variables("/data/user/0/app/files/disc/SCUS_974.72", settings)

        assertEquals("/data/user/0/app/files/disc/SCUS_974.72", values[RuntimeEnvironment.BOOT_PATH])
        assertEquals("1", values[RuntimeEnvironment.TOUCH_OVERLAY])
        assertEquals(TouchPreset.CLASSIC.id, values[RuntimeEnvironment.TOUCH_PRESET])
        assertEquals("1", values[RuntimeEnvironment.TOUCH_HIDE_WITH_GAMEPAD])
        assertEquals("1.000", values[RuntimeEnvironment.TOUCH_SCALE])
        assertEquals("0.550", values[RuntimeEnvironment.TOUCH_ALPHA])
        assertEquals("0.080", values[RuntimeEnvironment.TOUCH_DEADZONE])
        assertEquals("1.000", values[RuntimeEnvironment.LOOK_SENSITIVITY])
        assertEquals("0", values[RuntimeEnvironment.INVERT_LOOK_X])
        assertEquals("0.700", values[RuntimeEnvironment.AUDIO_VOLUME])
    }

    @Test
    fun aMissingBootPathIsLeftOutInsteadOfPublishedEmpty() {
        assertFalse(RuntimeEnvironment.variables(null, Settings()).containsKey(RuntimeEnvironment.BOOT_PATH))
        assertFalse(RuntimeEnvironment.variables("   ", Settings()).containsKey(RuntimeEnvironment.BOOT_PATH))
        val expected = RuntimeEnvironment.controls(Settings()) + RuntimeEnvironment.graphics(Settings())
        assertEquals(
            expected,
            RuntimeEnvironment.variables(null, Settings())
        )
    }

    @Test
    fun disabledTouchControlsTurnTheOverlayOff() {
        val values = RuntimeEnvironment.controls(Settings(touchControlsEnabled = false))

        assertEquals("0", values[RuntimeEnvironment.TOUCH_OVERLAY])
    }

    @Test
    fun decimalsKeepADotUnderACommaLocale() {
        Locale.setDefault(Locale.GERMANY)

        val values = RuntimeEnvironment.controls(
            Settings(touchScale = 1.25f, touchAlpha = 0.4f, touchDeadzone = 0.125f, cameraSensitivity = 1.5f)
        )

        assertEquals("1.250", values[RuntimeEnvironment.TOUCH_SCALE])
        assertEquals("0.400", values[RuntimeEnvironment.TOUCH_ALPHA])
        assertEquals("0.125", values[RuntimeEnvironment.TOUCH_DEADZONE])
        assertEquals("1.500", values[RuntimeEnvironment.LOOK_SENSITIVITY])
    }

    @Test
    fun customAndInvertedLookArePassedThrough() {
        val values = RuntimeEnvironment.controls(
            Settings(touchPreset = TouchPreset.CUSTOM.id, invertLookX = true)
        )

        assertEquals(TouchPreset.CUSTOM.id, values[RuntimeEnvironment.TOUCH_PRESET])
        assertEquals("1", values[RuntimeEnvironment.INVERT_LOOK_X])
    }

    @Test
    fun anUnknownPresetIdIsPublishedAsClassic() {
        val values = RuntimeEnvironment.controls(Settings(touchPreset = "not-a-preset"))

        assertEquals(TouchPreset.CLASSIC.id, values[RuntimeEnvironment.TOUCH_PRESET])
    }

    @Test
    fun disabledAudioMutesTheRuntime() {
        val values = RuntimeEnvironment.controls(Settings(audioEnabled = false, audioVolume = 0.9f))

        assertEquals("0.000", values[RuntimeEnvironment.AUDIO_VOLUME])
    }

    @Test
    fun audioVolumeKeepsItsLocaleIndependentFormat() {
        Locale.setDefault(Locale.GERMANY)

        val values = RuntimeEnvironment.controls(Settings(audioVolume = 0.65f))

        assertEquals("0.650", values[RuntimeEnvironment.AUDIO_VOLUME])
    }

    @Test
    fun variablesCarryTheGraphicsAndPacingSettings() {
        val values = RuntimeEnvironment.variables("/disc/SCUS_974.72", Settings())

        assertEquals("60", values[RuntimeEnvironment.FRAME_LIMIT])
        assertEquals("1", values[RuntimeEnvironment.ADAPTIVE_RESOLUTION])
        assertEquals("1.000", values[RuntimeEnvironment.RENDER_SCALE])
        assertEquals("0", values[RuntimeEnvironment.PERF_OVERLAY])
        assertFalse(values.containsKey(RuntimeEnvironment.STATS))
    }

    @Test
    fun allThreeFrameLimitsArePublishedAsIntegers() {
        assertEquals("0", RuntimeEnvironment.graphics(Settings(frameLimitFps = 0))[RuntimeEnvironment.FRAME_LIMIT])
        assertEquals("30", RuntimeEnvironment.graphics(Settings(frameLimitFps = 30))[RuntimeEnvironment.FRAME_LIMIT])
        assertEquals("60", RuntimeEnvironment.graphics(Settings(frameLimitFps = 60))[RuntimeEnvironment.FRAME_LIMIT])
    }

    @Test
    fun theRendererScaleIsClampedToTheRunnerFloor() {
        val low = RuntimeEnvironment.graphics(Settings(resolutionScalePercent = 25))
        val mid = RuntimeEnvironment.graphics(Settings(resolutionScalePercent = 75))

        // The app allows 25%, but celosia clamps render scale to 0.5..2.
        assertEquals("0.500", low[RuntimeEnvironment.RENDER_SCALE])
        assertEquals("0.750", mid[RuntimeEnvironment.RENDER_SCALE])
    }

    @Test
    fun scaleKeepsItsDotUnderACommaLocale() {
        Locale.setDefault(Locale.GERMANY)

        assertEquals(
            "0.750",
            RuntimeEnvironment.graphics(Settings(resolutionScalePercent = 75))[RuntimeEnvironment.RENDER_SCALE]
        )
    }

    @Test
    fun adaptiveResolutionFlagFollowsTheSetting() {
        assertEquals("0", RuntimeEnvironment.graphics(Settings(adaptiveResolution = false))[RuntimeEnvironment.ADAPTIVE_RESOLUTION])
        assertEquals("1", RuntimeEnvironment.graphics(Settings(adaptiveResolution = true))[RuntimeEnvironment.ADAPTIVE_RESOLUTION])
    }

    @Test
    fun statisticsEnableBothTheOverlayAndTheDiagnosticLog() {
        val values = RuntimeEnvironment.graphics(Settings(showStatistics = true))

        assertEquals("1", values[RuntimeEnvironment.PERF_OVERLAY])
        assertEquals("1", values[RuntimeEnvironment.STATS])

        val off = RuntimeEnvironment.graphics(Settings(showStatistics = false))
        assertEquals("0", off[RuntimeEnvironment.PERF_OVERLAY])
        assertFalse(off.containsKey(RuntimeEnvironment.STATS))
    }

    @Test
    fun publishReportsTheNamesTheProcessRejected() {        val values = RuntimeEnvironment.variables("/disc/SCUS_974.72", Settings())
        val attempted = mutableListOf<String>()

        val failed = RuntimeEnvironment.publish(values) { name, _ ->
            attempted += name
            name != RuntimeEnvironment.TOUCH_ALPHA
        }

        assertEquals(values.keys.toList(), attempted)
        assertEquals(listOf(RuntimeEnvironment.TOUCH_ALPHA), failed)
        assertTrue(failed.none { it == RuntimeEnvironment.BOOT_PATH })
    }
}
