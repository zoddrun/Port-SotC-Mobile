package com.portsotc.mobile.input

/**
 * PlayStation 2 pad button bits, matching the SCE_PAD layout used by the
 * original software. The recompiled game core consumes this exact bit mask.
 */
object Ps2Buttons {
    const val SELECT = 0x0001
    const val L3 = 0x0002
    const val R3 = 0x0004
    const val START = 0x0008
    const val UP = 0x0010
    const val RIGHT = 0x0020
    const val DOWN = 0x0040
    const val LEFT = 0x0080
    const val L2 = 0x0100
    const val R2 = 0x0200
    const val L1 = 0x0400
    const val R1 = 0x0800
    const val TRIANGLE = 0x1000
    const val CIRCLE = 0x2000
    const val CROSS = 0x4000
    const val SQUARE = 0x8000

    fun describe(mask: Int): String {
        if (mask == 0) return "-"
        val names = ArrayList<String>(8)
        if (mask and SELECT != 0) names.add("SELECT")
        if (mask and L3 != 0) names.add("L3")
        if (mask and R3 != 0) names.add("R3")
        if (mask and START != 0) names.add("START")
        if (mask and UP != 0) names.add("UP")
        if (mask and RIGHT != 0) names.add("RIGHT")
        if (mask and DOWN != 0) names.add("DOWN")
        if (mask and LEFT != 0) names.add("LEFT")
        if (mask and L2 != 0) names.add("L2")
        if (mask and R2 != 0) names.add("R2")
        if (mask and L1 != 0) names.add("L1")
        if (mask and R1 != 0) names.add("R1")
        if (mask and TRIANGLE != 0) names.add("TRIANGLE")
        if (mask and CIRCLE != 0) names.add("CIRCLE")
        if (mask and CROSS != 0) names.add("CROSS")
        if (mask and SQUARE != 0) names.add("SQUARE")
        return names.joinToString("+")
    }
}

/**
 * Thread-safe merge point for touch and physical controller input.
 *
 * The Android input callbacks run on the UI thread while the render loop reads
 * the snapshot from the native bridge. Both sources are kept separately and
 * merged on read so releasing one source cannot cancel a still-held button
 * from the other source.
 */
class PadState {

    data class Snapshot(
        val buttons: Int,
        val leftX: Float,
        val leftY: Float,
        val rightX: Float,
        val rightY: Float
    )

    private val lock = Any()

    private var touchButtons = 0
    private var padButtons = 0
    private var touchLeftX = 0f
    private var touchLeftY = 0f
    private var touchRightX = 0f
    private var touchRightY = 0f
    private var padLeftX = 0f
    private var padLeftY = 0f
    private var padRightX = 0f
    private var padRightY = 0f

    fun setTouchButton(bit: Int, pressed: Boolean) {
        synchronized(lock) {
            touchButtons = if (pressed) {
                touchButtons or bit
            } else {
                touchButtons and bit.inv()
            }
        }
    }

    fun setTouchDpad(up: Boolean, right: Boolean, down: Boolean, left: Boolean) {
        var mask = touchButtons and (Ps2Buttons.UP or Ps2Buttons.RIGHT or Ps2Buttons.DOWN or Ps2Buttons.LEFT).inv()
        if (up) mask = mask or Ps2Buttons.UP
        if (right) mask = mask or Ps2Buttons.RIGHT
        if (down) mask = mask or Ps2Buttons.DOWN
        if (left) mask = mask or Ps2Buttons.LEFT
        synchronized(lock) {
            touchButtons = mask
        }
    }

    fun setTouchStick(left: Boolean, x: Float, y: Float) {
        synchronized(lock) {
            if (left) {
                touchLeftX = x
                touchLeftY = y
            } else {
                touchRightX = x
                touchRightY = y
            }
        }
    }

    fun setPadButton(bit: Int, pressed: Boolean) {
        synchronized(lock) {
            padButtons = if (pressed) {
                padButtons or bit
            } else {
                padButtons and bit.inv()
            }
        }
    }

    fun setPadStick(left: Boolean, x: Float, y: Float) {
        synchronized(lock) {
            if (left) {
                padLeftX = x
                padLeftY = y
            } else {
                padRightX = x
                padRightY = y
            }
        }
    }

    fun clearTouch() {
        synchronized(lock) {
            touchButtons = 0
            touchLeftX = 0f
            touchLeftY = 0f
            touchRightX = 0f
            touchRightY = 0f
        }
    }

    fun clearPad() {
        synchronized(lock) {
            padButtons = 0
            padLeftX = 0f
            padLeftY = 0f
            padRightX = 0f
            padRightY = 0f
        }
    }

    fun clearAll() {
        synchronized(lock) {
            touchButtons = 0
            padButtons = 0
            touchLeftX = 0f
            touchLeftY = 0f
            touchRightX = 0f
            touchRightY = 0f
            padLeftX = 0f
            padLeftY = 0f
            padRightX = 0f
            padRightY = 0f
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            buttons = touchButtons or padButtons,
            leftX = if (magnitude(touchLeftX, touchLeftY) >= magnitude(padLeftX, padLeftY)) {
                touchLeftX
            } else {
                padLeftX
            },
            leftY = if (magnitude(touchLeftX, touchLeftY) >= magnitude(padLeftX, padLeftY)) {
                touchLeftY
            } else {
                padLeftY
            },
            rightX = if (magnitude(touchRightX, touchRightY) >= magnitude(padRightX, padRightY)) {
                touchRightX
            } else {
                padRightX
            },
            rightY = if (magnitude(touchRightX, touchRightY) >= magnitude(padRightX, padRightY)) {
                touchRightY
            } else {
                padRightY
            }
        )
    }

    private fun magnitude(x: Float, y: Float): Float = x * x + y * y
}
