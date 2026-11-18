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
    }

    @Test
    fun aMissingBootPathIsLeftOutInsteadOfPublishedEmpty() {
        assertFalse(RuntimeEnvironment.variables(null, Settings()).containsKey(RuntimeEnvironment.BOOT_PATH))
        assertFalse(RuntimeEnvironment.variables("   ", Settings()).containsKey(RuntimeEnvironment.BOOT_PATH))
        assertEquals(
            RuntimeEnvironment.controls(Settings()),
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
    fun publishReportsTheNamesTheProcessRejected() {
        val values = RuntimeEnvironment.variables("/disc/SCUS_974.72", Settings())
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
