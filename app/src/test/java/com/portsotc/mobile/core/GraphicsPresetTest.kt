package com.portsotc.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphicsPresetTest {

    @Test
    fun eachPresetMapsToRealSettings() {
        val battery = GraphicsPreset.BATTERY.apply(Settings())
        assertEquals(50, battery.resolutionScalePercent)
        assertEquals(true, battery.adaptiveResolution)
        assertEquals(30, battery.frameLimitFps)

        val balanced = GraphicsPreset.BALANCED.apply(Settings())
        assertEquals(75, balanced.resolutionScalePercent)
        assertEquals(true, balanced.adaptiveResolution)
        assertEquals(60, balanced.frameLimitFps)

        val performance = GraphicsPreset.PERFORMANCE.apply(Settings())
        assertEquals(100, performance.resolutionScalePercent)
        assertEquals(false, performance.adaptiveResolution)
        assertEquals(0, performance.frameLimitFps)
    }

    @Test
    fun aPresetLeavesUnrelatedSettingsAlone() {
        val original = Settings(
            audioVolume = 0.35f,
            touchPreset = TouchPreset.MODERN.id,
            touchScale = 1.25f
        )

        val updated = GraphicsPreset.BATTERY.apply(original)

        assertEquals(0.35f, updated.audioVolume, 0.0001f)
        assertEquals(TouchPreset.MODERN.id, updated.touchPreset)
        assertEquals(1.25f, updated.touchScale, 0.0001f)
    }

    @Test
    fun matchingRecognisesAPresetAndRejectsAMix() {
        val preset = GraphicsPreset.BALANCED.apply(Settings())
        assertEquals(GraphicsPreset.BALANCED, GraphicsPreset.matching(preset))

        val mixed = preset.copy(linearFiltering = false)
        assertEquals(GraphicsPreset.BALANCED, GraphicsPreset.matching(mixed))

        val custom = preset.copy(resolutionScalePercent = 60)
        assertNull(GraphicsPreset.matching(custom))
    }

    @Test
    fun anUnknownPresetIdIsRejected() {
        assertNull(GraphicsPreset.fromId("not-a-preset"))
        assertEquals(GraphicsPreset.BATTERY, GraphicsPreset.fromId(GraphicsPreset.BATTERY.id))
    }
}
