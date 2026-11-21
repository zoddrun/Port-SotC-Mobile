package com.portsotc.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test
    fun defaultsKeepDeveloperOverlaysOff() {
        val settings = Settings()

        assertFalse(settings.diagnosticsOverlay)
        assertFalse(settings.showStatistics)
        assertTrue(settings.touchControlsEnabled)
        assertTrue(settings.hideTouchWithGamepad)
        assertEquals(TouchPreset.CLASSIC.id, settings.touchPreset)
        assertEquals(1.0f, settings.cameraSensitivity, 0.0001f)
    }

    @Test
    fun sanitizedClampsNumericRanges() {
        val settings = Settings(
            resolutionScalePercent = 5,
            audioVolume = 4f,
            touchScale = 9f,
            touchAlpha = 0f,
            cameraSensitivity = 8f,
            touchDeadzone = 0.9f,
            padDeadzone = -1f
        ).sanitized()

        assertEquals(25, settings.resolutionScalePercent)
        assertEquals(1f, settings.audioVolume, 0.0001f)
        assertEquals(1.6f, settings.touchScale, 0.0001f)
        assertEquals(0.15f, settings.touchAlpha, 0.0001f)
        assertEquals(2f, settings.cameraSensitivity, 0.0001f)
        assertEquals(0.3f, settings.touchDeadzone, 0.0001f)
        assertEquals(0f, settings.padDeadzone, 0.0001f)
    }

    @Test
    fun unknownTouchPresetFallsBackToClassic() {
        val settings = Settings(touchPreset = "not-a-preset").sanitized()

        assertEquals(TouchPreset.CLASSIC.id, settings.touchPreset)
        assertEquals(TouchPreset.CLASSIC, TouchPreset.fromId("not-a-preset"))
    }

    @Test
    fun customTouchPresetIsPreserved() {
        val settings = Settings(touchPreset = TouchPreset.CUSTOM.id).sanitized()

        assertEquals(TouchPreset.CUSTOM.id, settings.touchPreset)
    }

    @Test
    fun performanceDefaultsAreAdaptiveAndUnlimited() {
        val settings = Settings()

        assertTrue(settings.adaptiveResolution)
        assertEquals(0, settings.frameLimitFps)
        assertFalse(settings.forceEs2)
    }

    @Test
    fun anUnknownFrameLimitFallsBackToUnlimited() {
        assertEquals(0, Settings(frameLimitFps = 144).sanitized().frameLimitFps)
        assertEquals(0, Settings(frameLimitFps = -1).sanitized().frameLimitFps)
        assertEquals(0, Settings(frameLimitFps = 0).sanitized().frameLimitFps)
        assertEquals(30, Settings(frameLimitFps = 30).sanitized().frameLimitFps)
    }
}
