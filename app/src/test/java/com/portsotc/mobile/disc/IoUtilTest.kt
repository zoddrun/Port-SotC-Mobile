package com.portsotc.mobile.disc

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IoUtilTest {

    @Test
    fun readsAtMostTheRequestedLength() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
        assertArrayEquals(byteArrayOf(1, 2, 3), stream.readAtMost(3))
        assertEquals(4, stream.read())
    }

    @Test
    fun returnsShortArrayAtEndOfStream() {
        val stream = ByteArrayInputStream(byteArrayOf(7, 8))
        assertArrayEquals(byteArrayOf(7, 8), stream.readAtMost(10))
    }

    @Test
    fun returnsEmptyArrayWhenNothingRemains() {
        val stream = ByteArrayInputStream(ByteArray(0))
        assertEquals(0, stream.readAtMost(4).size)
    }

    @Test
    fun readExactlyRejectsShortStreams() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2))
        assertThrows(IOException::class.java) { stream.readExactly(4) }
    }

    @Test
    fun readExactlyReturnsRequestedBytes() {
        val stream = ByteArrayInputStream(byteArrayOf(9, 8, 7))
        assertArrayEquals(byteArrayOf(9, 8), stream.readExactly(2))
    }
}
