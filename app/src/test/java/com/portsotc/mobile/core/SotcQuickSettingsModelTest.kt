package com.portsotc.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SotcQuickSettingsModelTest {
    private fun state(
        settings: Settings = Settings(),
        gamepadConnected: Boolean = false,
        touchRestored: Boolean = false
    ) = SotcQuickSettingsModel.State(
        settings = settings,
        gamepadConnected = gamepadConnected,
        touchRestored = touchRestored,
        shaderPackReady = true,
        shaderPackDetail = "installed"
    )

    @Test
    fun touchControlsAreVisibleByDefault() {
        assertTrue(SotcQuickSettingsModel.touchControlsVisible(state()))
    }

    @Test
    fun connectedGamepadHidesTouchOnlyWhenAutoHideIsEnabled() {
        assertFalse(SotcQuickSettingsModel.touchControlsVisible(state(gamepadConnected = true)))
        assertTrue(
            SotcQuickSettingsModel.touchControlsVisible(
                state(
                    settings = Settings(hideTouchWithGamepad = false),
                    gamepadConnected = true
                )
            )
        )
    }

    @Test
    fun explicitTouchRestoreOverridesGamepadAutoHide() {
        assertTrue(
            SotcQuickSettingsModel.touchControlsVisible(
                state(gamepadConnected = true, touchRestored = true)
            )
        )
    }

    @Test
    fun restoreIsOfferedOnlyWhenTouchWasAutomaticallyHidden() {
        assertTrue(SotcQuickSettingsModel.restoreAvailable(state(gamepadConnected = true)))
        assertFalse(
            SotcQuickSettingsModel.restoreAvailable(
                state(
                    settings = Settings(hideTouchWithGamepad = false),
                    gamepadConnected = true
                )
            )
        )
        assertFalse(SotcQuickSettingsModel.restoreAvailable(state(touchRestored = true)))
    }

    @Test
    fun frameLimitLabelsAreTabularAndUncappedIsExplicit() {
        assertEquals("UNLIMITED", SotcQuickSettingsModel.frameLimitLabel(0))
        assertEquals("30 FPS", SotcQuickSettingsModel.frameLimitLabel(30))
        assertEquals("60 FPS", SotcQuickSettingsModel.frameLimitLabel(60))
    }

    @Test
    fun sessionNoticeNeverPromisesLiveRunnerReload() {
        val notice = SotcQuickSettingsModel.sessionNotice()

        assertTrue(notice.contains("next launch"))
        assertFalse(notice.contains("immediately reload"))
    }

    @Test
    fun hdCaptionMarksCustomOverrides() {
        val custom = state(
            settings = Settings(
                hdShaderPreset = HdPresetId.ULTRA.id,
                hdShaderOverrides = HdShaderOverrides(rcasSharpness = 0.5)
            )
        )

        val caption = SotcQuickSettingsModel.hdCaption(custom)

        assertTrue(caption.contains("Ultra HD"))
        assertTrue(caption.contains("CUSTOM"))
    }
}
