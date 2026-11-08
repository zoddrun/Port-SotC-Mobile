package com.portsotc.mobile.input

import kotlin.math.sqrt

/** Pure analog-stick math, kept separate from Android input classes for testing. */
object Analog {

    /**
     * Applies a radial deadzone and rescales the remaining magnitude so the
     * full output range is still reachable at full deflection.
     */
    fun applyDeadzone(x: Float, y: Float, deadzone: Float): Pair<Float, Float> {
        val clampedDeadzone = deadzone.coerceIn(0f, 0.95f)
        val magnitude = sqrt(x * x + y * y)
        if (magnitude <= clampedDeadzone || magnitude == 0f) return 0f to 0f
        val clamped = magnitude.coerceAtMost(1f)
        val scaled = ((clamped - clampedDeadzone) / (1f - clampedDeadzone)).coerceIn(0f, 1f)
        val factor = scaled / magnitude
        return (x * factor) to (y * factor)
    }
}
