package com.portsotc.mobile.input

import com.portsotc.mobile.core.TouchGripMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SotcTactileInputMapperTest {
    @Test
    fun firstMaskReportsPressedBits() {
        val deltas = SotcTactileInputMapper().deltas(Ps2Buttons.CROSS or Ps2Buttons.START)

        assertEquals(2, deltas.size)
        assertTrue(deltas.any { it.bit == Ps2Buttons.CROSS && it.pressed })
        assertTrue(deltas.any { it.bit == Ps2Buttons.START && it.pressed })
    }

    @Test
    fun unchangedMaskProducesNoFakeRepeatTaps() {
        val mapper = SotcTactileInputMapper()
        mapper.deltas(Ps2Buttons.CROSS)

        assertTrue(mapper.deltas(Ps2Buttons.CROSS).isEmpty())
    }

    @Test
    fun releasedBitsAreReportedOnce() {
        val mapper = SotcTactileInputMapper()
        mapper.deltas(Ps2Buttons.CROSS or Ps2Buttons.SQUARE)

        val released = mapper.deltas(Ps2Buttons.CROSS)

        assertEquals(1, released.size)
        assertEquals(Ps2Buttons.SQUARE, released.single().bit)
        assertEquals(false, released.single().pressed)
    }

    @Test
    fun unknownBitsAreIgnored() {
        val deltas = SotcTactileInputMapper().deltas(0x40000000)

        assertTrue(deltas.isEmpty())
    }

    @Test
    fun resetRestoresFreshState() {
        val mapper = SotcTactileInputMapper()
        mapper.deltas(Ps2Buttons.CROSS)
        mapper.reset()

        val afterReset = mapper.deltas(Ps2Buttons.CROSS)

        assertEquals(1, afterReset.size)
        assertTrue(afterReset.single().pressed)
    }

    @Test
    fun namedGripModesMapToTactileBehaviors() {
        assertEquals(
            TactileGripBehavior.HOLD,
            SotcTactileInputMapper.gripBehaviorFor(TouchGripMode.SHOULDER.id)
        )
        assertEquals(
            TactileGripBehavior.LATCH,
            SotcTactileInputMapper.gripBehaviorFor(TouchGripMode.CLAW.id)
        )
        assertEquals(
            TactileGripBehavior.HYBRID,
            SotcTactileInputMapper.gripBehaviorFor(TouchGripMode.BRIDGE.id)
        )
    }
}
