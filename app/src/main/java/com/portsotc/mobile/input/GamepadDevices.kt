package com.portsotc.mobile.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Shared physical-controller detection.
 *
 * Android reports the built-in touchscreen, headset keys and several vendor
 * input helpers as joysticks. Those devices must not make the runtime hide its
 * touch controls, so the same filter is used by the controller settings screen
 * and by the in-game quick settings trigger.
 */
object GamepadDevices {

    private val noiseNames = setOf(
        "Virtual", "mtk-pmic-keys", "uinput-xiaomi",
        "Headset Jack", "NVTCapacitiveTouchScreen"
    )

    fun connectedControllers(context: Context): List<String> {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            ?: return emptyList()
        val result = ArrayList<String>()
        for (id in manager.inputDeviceIds) {
            val device = manager.getInputDevice(id) ?: continue
            if (device.isVirtual) continue
            val isGamepad = device.sources and InputDevice.SOURCE_GAMEPAD != 0
            val isJoystick = device.sources and InputDevice.SOURCE_JOYSTICK != 0
            if (!isGamepad && !isJoystick) continue
            if (noiseNames.any { noise -> device.name.contains(noise, ignoreCase = true) }) {
                continue
            }
            val hasAnalog = isJoystick || (isGamepad &&
                (device.getMotionRange(MotionEvent.AXIS_X) != null ||
                    device.getMotionRange(MotionEvent.AXIS_Z) != null))
            if (!hasAnalog && !isGamepad) continue
            result.add("${device.name} · " + if (isGamepad) "GAMEPAD" else "JOYSTICK")
        }
        return result
    }

    fun isConnected(context: Context): Boolean = connectedControllers(context).isNotEmpty()
}
