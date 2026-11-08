package com.portsotc.mobile.disc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps2ElfTest {

    @Test
    fun acceptsValidPlayStation2Executable() {
        val info = Ps2Elf.inspect(SyntheticDisc.validElf())
        assertEquals(0x00100000L, info.entryPoint)
        assertEquals(1, info.loadableSegments.size)
        assertTrue(info.loadableSegments.first().fileSize > 0)
        assertEquals(16L, info.totalFileBytes)
    }

    @Test(expected = Ps2ElfException::class)
    fun rejectsWrongMachine() {
        val bytes = SyntheticDisc.validElf()
        bytes[18] = 3 // EM_386
        bytes[19] = 0
        Ps2Elf.inspect(bytes)
    }

    @Test(expected = Ps2ElfException::class)
    fun rejectsMissingMagic() {
        val bytes = SyntheticDisc.validElf()
        bytes[1] = 'X'.code.toByte()
        Ps2Elf.inspect(bytes)
    }

    @Test(expected = Ps2ElfException::class)
    fun rejectsTruncatedHeader() {
        Ps2Elf.inspect(ByteArray(32))
    }

    @Test(expected = Ps2ElfException::class)
    fun rejectsMissingLoadSegment() {
        val bytes = SyntheticDisc.validElf()
        bytes[44] = 0 // phnum = 0
        bytes[45] = 0
        Ps2Elf.inspect(bytes)
    }
}
