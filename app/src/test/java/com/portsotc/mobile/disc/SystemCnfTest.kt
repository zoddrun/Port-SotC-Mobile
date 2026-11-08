package com.portsotc.mobile.disc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemCnfTest {

    @Test
    fun parsesBootConfiguration() {
        val entries = SystemCnf.parse(
            """
            BOOT2 = cdrom0:\SCUS_974.72;1
            VER = 1.00
            VMODE = NTSC
            """.trimIndent()
        )
        assertEquals("cdrom0:\\SCUS_974.72;1", entries["BOOT2"])
        assertEquals("1.00", entries["VER"])
        assertEquals("NTSC", entries["VMODE"])
        assertEquals("SCUS_974.72", SystemCnf.bootFile(entries))
    }

    @Test
    fun normalizesBootPaths() {
        assertEquals("SCUS_974.72", SystemCnf.normalizeBootPath("cdrom0:\\SCUS_974.72;1"))
        assertEquals("SCUS_974.72", SystemCnf.normalizeBootPath("cdrom0:/SCUS_974.72;1"))
        assertEquals("SCUS_974.72", SystemCnf.normalizeBootPath("SCUS_974.72"))
    }

    @Test
    fun derivesSerialFromBootFileName() {
        assertEquals("SCUS-97472", SystemCnf.serialFromBootFile("SCUS_974.72"))
        assertEquals("SCUS-97472", SystemCnf.serialFromBootFile("scus_974.72"))
        assertNull(SystemCnf.serialFromBootFile("UNKNOWN.BIN"))
        assertNull(SystemCnf.serialFromBootFile("SCUS97472"))
    }

    @Test
    fun ignoresCommentsAndMalformedLines() {
        val entries = SystemCnf.parse(
            "# comment\n" +
                "; another comment\n" +
                "NOT A KEY VALUE PAIR\n" +
                "BOOT = cdrom0:\\SLUS_200.62;1\n"
        )
        assertEquals("SLUS_200.62", SystemCnf.bootFile(entries))
    }
}
