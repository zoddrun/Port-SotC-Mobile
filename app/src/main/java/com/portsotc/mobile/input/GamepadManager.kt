package com.portsotc.mobile.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.portsotc.mobile.core.Settings

/**
 * Reads physical gamepad events and writes them into the shared [PadState].
 *
 * Only device-sourced events are consumed so that on-screen controls and
 * normal activity key handling keep working. Analog sticks use a radial
 * deadzone with rescaling, which avoids the "sticky axis" feel of per-axis
 * thresholds.
 */
class GamepadManager(
    context: Context,
    private val padState: PadState,
    private val mapping: ControllerMapping,
    private val settingsProvider: () -> Settings
) {

    private val inputManager =
        context.getSystemService(Context.INPUT_SERVICE) as? InputManager

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.device == null) return false
        val button = mapping.buttonFor(event.keyCode)
        if (button == 0) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                padState.setPadButton(button, true)
                return true
            }
            KeyEvent.ACTION_UP -> {
                padState.setPadButton(button, false)
                return true
            }
        }
        return false
    }

    fun handleGenericMotion(event: MotionEvent): Boolean {
        val device = event.device ?: return false
        if (device.sources and InputDevice.SOURCE_JOYSTICK == 0 &&
            device.sources and InputDevice.SOURCE_GAMEPAD == 0
        ) {
            return false
        }
        val settings = settingsProvider()

        val left = Analog.applyDeadzone(
            axis(event, MotionEvent.AXIS_X),
            axis(event, MotionEvent.AXIS_Y),
            settings.padDeadzone
        )
        padState.setPadStick(left = true, x = left.first, y = left.second)

        var rightX = axis(event, MotionEvent.AXIS_Z)
        var rightY = axis(event, MotionEvent.AXIS_RZ)
        if (rightX == 0f && rightY == 0f) {
            rightX = axis(event, MotionEvent.AXIS_RX)
            rightY = axis(event, MotionEvent.AXIS_RY)
        }
        val right = Analog.applyDeadzone(rightX, rightY, settings.padDeadzone)
        var lookX = (right.first * settings.cameraSensitivity).coerceIn(-1f, 1f)
        var lookY = (right.second * settings.cameraSensitivity).coerceIn(-1f, 1f)
        if (settings.invertLookX) lookX = -lookX
        if (settings.invertLookY) lookY = -lookY
        padState.setPadStick(left = false, x = lookX, y = lookY)

        val leftTrigger = maxOf(
            axis(event, MotionEvent.AXIS_LTRIGGER),
            axis(event, MotionEvent.AXIS_BRAKE)
        )
        val rightTrigger = maxOf(
            axis(event, MotionEvent.AXIS_RTRIGGER),
            axis(event, MotionEvent.AXIS_GAS)
        )
        padState.setPadButton(Ps2Buttons.L2, leftTrigger > 0.5f)
        padState.setPadButton(Ps2Buttons.R2, rightTrigger > 0.5f)

        val hatX = axis(event, MotionEvent.AXIS_HAT_X)
        val hatY = axis(event, MotionEvent.AXIS_HAT_Y)
        val up = hatY < -0.5f
        val down = hatY > 0.5f
        val leftDown = hatX < -0.5f
        val rightDown = hatX > 0.5f
        padState.setPadButton(Ps2Buttons.UP, up)
        padState.setPadButton(Ps2Buttons.DOWN, down)
        padState.setPadButton(Ps2Buttons.LEFT, leftDown)
        padState.setPadButton(Ps2Buttons.RIGHT, rightDown)
        return true
    }

    fun clear() {
        padState.clearPad()
    }

    fun connectedControllers(): List<String> {
        val manager = inputManager ?: return emptyList()
        val result = ArrayList<String>()
        for (id in manager.inputDeviceIds) {
            val device = manager.getInputDevice(id) ?: continue
            val isController = device.sources and InputDevice.SOURCE_GAMEPAD != 0 ||
                device.sources and InputDevice.SOURCE_JOYSTICK != 0
            if (isController) {
                result.add(
                    "${device.name} · " +
                        if (device.sources and InputDevice.SOURCE_GAMEPAD != 0) "GAMEPAD" else "JOYSTICK"
                )
            }
        }
        return result
    }

    companion object {
        fun deviceName(device: InputDevice?): String {
            if (device == null) return "No controller"
            return "${device.name} (vendor ${device.vendorId})"
        }

        private fun axis(event: MotionEvent, axis: Int): Float = try {
            event.getAxisValue(axis)
        } catch (_: IllegalArgumentException) {
            0f
        }
    }
}
