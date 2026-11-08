package com.portsotc.mobile.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.sqrt

class AnalogTest {

    @Test
    fun deadzoneSuppressesSmallInput() {
        val result = Analog.applyDeadzone(0.05f, 0.02f, 0.15f)
        assertEquals(0f, result.first, 0.0001f)
        assertEquals(0f, result.second, 0.0001f)
    }

    @Test
    fun inputOutsideDeadzoneIsRescaled() {
        val result = Analog.applyDeadzone(0.5f, 0f, 0.15f)
        val expected = (0.5f - 0.15f) / (1f - 0.15f)
        assertEquals(expected, result.first, 0.0001f)
        assertEquals(0f, result.second, 0.0001f)
    }

    @Test
    fun fullDeflectionReachesUnitMagnitude() {
        val result = Analog.applyDeadzone(1f, 0f, 0.2f)
        assertEquals(1f, result.first, 0.0001f)
    }

    @Test
    fun diagonalInputIsClampedToUnitCircle() {
        val result = Analog.applyDeadzone(1.4f, 1.4f, 0.1f)
        val magnitude = sqrt(result.first * result.first + result.second * result.second)
        assertEquals(1f, magnitude, 0.001f)
    }

    @Test
    fun zeroDeadzonePreservesInput() {
        val result = Analog.applyDeadzone(0.25f, -0.25f, 0f)
        assertEquals(0.25f, result.first, 0.0001f)
        assertEquals(-0.25f, result.second, 0.0001f)
    }

    @Test
    fun invalidDeadzoneDoesNotDivideByZero() {
        val result = Analog.applyDeadzone(0.5f, 0f, 5f)
        assertEquals(0f, result.first, 0.0001f)
        assertNotEquals(Float.NaN, result.first)
    }
}
