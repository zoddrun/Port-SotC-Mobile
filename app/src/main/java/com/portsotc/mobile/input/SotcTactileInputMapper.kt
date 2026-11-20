package com.portsotc.mobile.input

import com.portsotc.mobile.core.TouchGripMode

/** One button transition from the tactile view into the shared pad state. */
data class PadButtonDelta(val bit: Int, val pressed: Boolean)

/** How the grip hold behaves, independent of the Android view implementation. */
enum class TactileGripBehavior { HOLD, LATCH, HYBRID }

/**
 * Converts a full button mask into only the transitions that changed.
 *
 * The view naturally reports its complete state on every touch event, but the
 * shared [PadState] latches presses. Replaying unchanged bits would create fake
 * taps after every pointer move, so only changed bits are forwarded.
 */
class SotcTactileInputMapper {

    private var previousMask = 0

    fun deltas(mask: Int): List<PadButtonDelta> {
        val changed = (mask xor previousMask) and KNOWN_BITS
        previousMask = mask and KNOWN_BITS
        if (changed == 0) return emptyList()
        return KNOWN_BUTTONS.filter { (changed and it.first) != 0 }
            .map { PadButtonDelta(it.first, (mask and it.first) != 0) }
    }

    fun reset() {
        previousMask = 0
    }

    companion object {
        private val KNOWN_BUTTONS = listOf(
            Ps2Buttons.SELECT to "SELECT",
            Ps2Buttons.START to "START",
            Ps2Buttons.UP to "UP",
            Ps2Buttons.RIGHT to "RIGHT",
            Ps2Buttons.DOWN to "DOWN",
            Ps2Buttons.LEFT to "LEFT",
            Ps2Buttons.L1 to "L1",
            Ps2Buttons.L2 to "L2",
            Ps2Buttons.R1 to "R1",
            Ps2Buttons.R2 to "R2",
            Ps2Buttons.TRIANGLE to "TRIANGLE",
            Ps2Buttons.CIRCLE to "CIRCLE",
            Ps2Buttons.CROSS to "CROSS",
            Ps2Buttons.SQUARE to "SQUARE"
        )

        private val KNOWN_BITS = KNOWN_BUTTONS.fold(0) { mask, (bit, _) -> mask or bit }

        /**
         * Maps the named runner grip positions to the view's interaction model.
         * Shoulder is a hold under the index finger, Claw latches so the thumb
         * can keep aiming, and Bridge combines both behaviours.
         */
        fun gripBehaviorFor(gripModeId: String): TactileGripBehavior =
            when (TouchGripMode.fromId(gripModeId)) {
                TouchGripMode.CLAW -> TactileGripBehavior.LATCH
                TouchGripMode.BRIDGE -> TactileGripBehavior.HYBRID
                else -> TactileGripBehavior.HOLD
            }
    }
}
