package com.portsotc.mobile.input

import android.content.Context
import android.view.KeyEvent

/**
 * Maps Android key codes to PlayStation 2 pad bits.
 *
 * The defaults cover the standard Android gamepad mapping that both Xbox and
 * PlayStation controllers expose. Bindings can be overridden per key code and
 * are stored in SharedPreferences so a future remapping screen can reuse the
 * same table without changing the input pipeline.
 */
class ControllerMapping(context: Context) {

    private val preferences =
        context.getSharedPreferences("sotc_controller_mapping", Context.MODE_PRIVATE)

    fun buttonFor(keyCode: Int): Int {
        val override = preferences.getInt(overrideKey(keyCode), UNSET)
        if (override != UNSET) return override
        return DEFAULT_BINDINGS[keyCode] ?: 0
    }

    fun bind(keyCode: Int, button: Int) {
        preferences.edit().putInt(overrideKey(keyCode), button).apply()
    }

    fun unbind(keyCode: Int) {
        preferences.edit().putInt(overrideKey(keyCode), 0).apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    /** Returns the effective binding table for display, keyed by Android key code. */
    fun effectiveBindings(): Map<Int, Int> {
        val result = LinkedHashMap<Int, Int>()
        for ((keyCode, button) in DEFAULT_BINDINGS) {
            result[keyCode] = buttonFor(keyCode)
        }
        for ((key, _) in preferences.all) {
            if (!key.startsWith(OVERRIDE_PREFIX)) continue
            val keyCode = key.removePrefix(OVERRIDE_PREFIX).toIntOrNull() ?: continue
            val button = preferences.getInt(key, UNSET)
            if (button == 0) result.remove(keyCode) else result[keyCode] = button
        }
        return result
    }

    private fun overrideKey(keyCode: Int): String = "$OVERRIDE_PREFIX$keyCode"

    companion object {
        private const val OVERRIDE_PREFIX = "key_"
        private const val UNSET = -1

        val DEFAULT_BINDINGS: Map<Int, Int> = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to Ps2Buttons.CROSS,
            KeyEvent.KEYCODE_BUTTON_B to Ps2Buttons.CIRCLE,
            KeyEvent.KEYCODE_BUTTON_X to Ps2Buttons.SQUARE,
            KeyEvent.KEYCODE_BUTTON_Y to Ps2Buttons.TRIANGLE,
            KeyEvent.KEYCODE_BUTTON_L1 to Ps2Buttons.L1,
            KeyEvent.KEYCODE_BUTTON_R1 to Ps2Buttons.R1,
            KeyEvent.KEYCODE_BUTTON_L2 to Ps2Buttons.L2,
            KeyEvent.KEYCODE_BUTTON_R2 to Ps2Buttons.R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL to Ps2Buttons.L3,
            KeyEvent.KEYCODE_BUTTON_THUMBR to Ps2Buttons.R3,
            KeyEvent.KEYCODE_BUTTON_START to Ps2Buttons.START,
            KeyEvent.KEYCODE_BUTTON_SELECT to Ps2Buttons.SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE to Ps2Buttons.SELECT,
            KeyEvent.KEYCODE_DPAD_UP to Ps2Buttons.UP,
            KeyEvent.KEYCODE_DPAD_RIGHT to Ps2Buttons.RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN to Ps2Buttons.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to Ps2Buttons.LEFT,
            KeyEvent.KEYCODE_ENTER to Ps2Buttons.START,
            KeyEvent.KEYCODE_SPACE to Ps2Buttons.CROSS
        )
    }
}
