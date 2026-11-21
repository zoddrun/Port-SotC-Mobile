package com.portsotc.mobile.disc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

class DiscInspectorTest {

    private lateinit var root: File
    private lateinit var iso: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sotc-disc-inspector").toFile()
        iso = SyntheticDisc.create(File(root, "SCUS_974.72.iso"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun computesBootIdentifierAndImageDigestFromContents() {
        val report = DiscInspector().inspect(iso)

        assertEquals("SCUS-97472", report.serial)
        assertEquals("SCUS_974.72", report.bootName)
        assertTrue(report.supported)
        assertTrue(report.bootElfValid)
        assertEquals(32, report.bootMd5?.length)
        assertEquals(expectedMd5("SCUS_974.72", SyntheticDisc.validElf()), report.bootMd5)
        assertEquals(sha256(iso.readBytes()), report.imageSha256)
        assertEquals("SOTC_TEST", report.volumeIdentifier)
        assertEquals(3, report.modules.size)
        assertFalse(report.issues.any { it.severity == IssueSeverity.ERROR })

        val formatted = report.format()
        assertTrue(formatted.contains("BOOT MD5    ${report.bootMd5}"))
        assertTrue(formatted.contains("SHA-256     ${report.imageSha256}"))
    }

    @Test
    fun bootIdentifierChangesWithExecutableBytes() {
        val original = DiscInspector().inspect(iso).bootMd5
        val changedElf = SyntheticDisc.validElf().copyOf()
        changedElf[changedElf.lastIndex] = (changedElf.last().toInt() + 1).toByte()

        val otherIso = SyntheticDisc.create(
            File(root, "changed.iso"),
            bootElf = changedElf
        )
        val changed = DiscInspector().inspect(otherIso).bootMd5

        assertNotNull(original)
        assertNotNull(changed)
        assertFalse(original.equals(changed, ignoreCase = true))
        assertEquals(expectedMd5("SCUS_974.72", changedElf), changed)
    }

    @Test
    fun bootIdentifierIncludesTheNameFromSystemCnf() {
        val bootName = "SCES_533.26"
        val otherIso = SyntheticDisc.create(
            File(root, "SCES_533.26.iso"),
            bootFile = bootName,
            cnf = SyntheticDisc.defaultCnf(bootName)
        )

        val report = DiscInspector().inspect(otherIso)

        assertEquals("SCES-53326", report.serial)
        assertFalse(report.supported)
        assertEquals(bootName, report.bootName)
        assertEquals(expectedMd5(bootName, SyntheticDisc.validElf()), report.bootMd5)
        assertTrue(report.issues.any { it.code == "KNOWN_UNVERIFIED_SERIAL" })
    }

    @Test
    fun primaryExecutableNamePreservesDiscDirectorySeparators() {
        assertEquals(
            "DIR\\SCUS_974.72",
            SystemCnf.primaryExecutableNameFromBootPath("cdrom0:\\DIR\\SCUS_974.72;1")
        )
        assertEquals(
            "SCUS_974.72",
            SystemCnf.primaryExecutableNameFromBootPath("  cdrom0:\\SCUS_974.72;1  ")
        )
    }

    private fun expectedMd5(name: String, executable: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(name.toByteArray(Charsets.US_ASCII))
        digest.update(executable)
        return hex(digest.digest())
    }

    private fun sha256(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }
}
