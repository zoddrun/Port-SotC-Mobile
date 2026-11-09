package com.portsotc.mobile.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadStateTest {

    @Test
    fun initialSnapshotIsNeutral() {
        val state = PadState()
        val snapshot = state.snapshot()
        assertEquals(0, snapshot.buttons)
        assertEquals(0f, snapshot.leftX, 0f)
        assertEquals(0f, snapshot.leftY, 0f)
        assertEquals(0f, snapshot.rightX, 0f)
        assertEquals(0f, snapshot.rightY, 0f)
    }

    @Test
    fun mergesTouchAndPhysicalButtons() {
        val state = PadState()
        state.setTouchButton(Ps2Buttons.CROSS, true)
        state.setPadButton(Ps2Buttons.L1, true)
        assertEquals(
            Ps2Buttons.CROSS or Ps2Buttons.L1,
            state.snapshot().buttons
        )

        state.setTouchButton(Ps2Buttons.CROSS, false)
        assertEquals(Ps2Buttons.L1, state.snapshot().buttons)

        state.setTouchButton(Ps2Buttons.CROSS, true)
        state.clearPad()
        assertEquals(Ps2Buttons.CROSS, state.snapshot().buttons)
    }

    @Test
    fun clearsTouchStateWithoutClearingPadState() {
        val state = PadState()
        state.setTouchButton(Ps2Buttons.SQUARE, true)
        state.setPadButton(Ps2Buttons.TRIANGLE, true)
        state.setTouchStick(left = true, x = 0.5f, y = -0.5f)
        state.clearTouch()

        val snapshot = state.snapshot()
        assertEquals(Ps2Buttons.TRIANGLE, snapshot.buttons)
        assertEquals(0f, snapshot.leftX, 0f)
        assertEquals(0f, snapshot.leftY, 0f)
    }

    @Test
    fun largerStickMagnitudeWins() {
        val state = PadState()
        state.setTouchStick(left = true, x = 0.2f, y = 0f)
        state.setPadStick(left = true, x = -0.9f, y = 0.1f)
        assertEquals(-0.9f, state.snapshot().leftX, 0.0001f)

        state.setPadStick(left = true, x = -0.2f, y = 0.1f)
        state.setTouchStick(left = true, x = 0.7f, y = 0f)
        assertEquals(0.7f, state.snapshot().leftX, 0.0001f)
    }

    @Test
    fun dpadDirectionsReplaceOnlyDirectionBits() {
        val state = PadState()
        state.setTouchButton(Ps2Buttons.CROSS, true)
        state.setTouchDpad(up = true, right = false, down = false, left = false)
        assertEquals(
            Ps2Buttons.CROSS or Ps2Buttons.UP,
            state.snapshot().buttons
        )

        state.setTouchDpad(up = false, right = true, down = false, left = true)
        assertEquals(
            Ps2Buttons.CROSS or Ps2Buttons.RIGHT or Ps2Buttons.LEFT,
            state.snapshot().buttons
        )

        state.setTouchDpad(up = false, right = false, down = false, left = false)
        assertEquals(Ps2Buttons.CROSS, state.snapshot().buttons)
    }

    @Test
    fun shortTouchTapSurvivesUntilNextSnapshot() {
        val state = PadState()
        state.setTouchButton(Ps2Buttons.CROSS, true)
        state.setTouchButton(Ps2Buttons.CROSS, false)

        assertEquals(Ps2Buttons.CROSS, state.snapshot().buttons)
        assertEquals(0, state.snapshot().buttons)
    }

    @Test
    fun shortDpadTapSurvivesUntilNextSnapshot() {
        val state = PadState()
        state.setTouchDpad(up = false, right = true, down = false, left = false)
        state.setTouchDpad(up = false, right = false, down = false, left = false)

        assertEquals(Ps2Buttons.RIGHT, state.snapshot().buttons)
        assertEquals(0, state.snapshot().buttons)
    }

    @Test
    fun shortGamepadTapSurvivesUntilNextSnapshot() {
        val state = PadState()
        state.setPadButton(Ps2Buttons.START, true)
        state.setPadButton(Ps2Buttons.START, false)

        assertEquals(Ps2Buttons.START, state.snapshot().buttons)
        assertEquals(0, state.snapshot().buttons)
    }

    @Test
    fun clearDropsLatchedTaps() {
        val state = PadState()
        state.setTouchButton(Ps2Buttons.CIRCLE, true)
        state.setTouchButton(Ps2Buttons.CIRCLE, false)
        state.clearAll()

        assertEquals(0, state.snapshot().buttons)
    }

    @Test
    fun buttonDescriptionsAreEnglishAndComplete() {
        val description = Ps2Buttons.describe(
            Ps2Buttons.CROSS or Ps2Buttons.START or Ps2Buttons.UP
        )
        assertTrue(description.contains("CROSS"))
        assertTrue(description.contains("START"))
        assertTrue(description.contains("UP"))
        assertFalse(description.contains("null"))
        assertEquals("-", Ps2Buttons.describe(0))
    }
}
