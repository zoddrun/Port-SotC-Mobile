package com.portsotc.mobile.disc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Iso9660Test {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readsVolumeDescriptorAndRootFiles() {
        val imageFile = SyntheticDisc.create(temporaryFolder.newFile("disc.iso"))
        Iso9660Image(imageFile).use { image ->
            assertEquals("SOTC_TEST", image.volume.volumeIdentifier)
            assertEquals(2048, image.volume.logicalBlockSize)
            assertEquals(128L, image.sectorCount)
            assertEquals(SyntheticDisc.SECTOR.toLong(), image.volume.rootSize)

            val systemCnf = image.find("SYSTEM.CNF")
            assertNotNull(systemCnf)
            assertTrue(image.readFile(systemCnf!!).toString(Charsets.US_ASCII).contains("BOOT2"))

            assertNotNull(image.find("SCUS_974.72"))
            assertNotNull(image.find("KERNEL.XFF"))
            assertNotNull(image.find("MANAGER.XFF"))
            assertNotNull(image.find("GAMECORE.XFF"))
            assertEquals(SyntheticDisc.validElf().size.toLong(), image.find("SCUS_974.72")!!.size)
        }
    }

    @Test
    fun findsFilesUsingPs2PathNotation() {
        val imageFile = SyntheticDisc.create(temporaryFolder.newFile("paths.iso"))
        Iso9660Image(imageFile).use { image ->
            assertNotNull(image.find("cdrom0:\\SYSTEM.CNF;1"))
            assertNotNull(image.find("cdrom0:/system.cnf;1"))
            assertNotNull(image.find("/KERNEL.XFF"))
            assertNotNull(image.find("kernel.xff;1"))
        }
    }

    @Test(expected = IsoFormatException::class)
    fun rejectsNonIsoData() {
        val file = temporaryFolder.newFile("random.bin")
        file.writeBytes(ByteArray(64 * 1024) { (it * 7).toByte() })
        Iso9660Image(file).use { }
    }

    @Test
    fun readsFileAcrossMultipleLogicalReads() {
        val payload = ByteArray(5000) { (it % 251).toByte() }
        val imageFile = SyntheticDisc.create(
            temporaryFolder.newFile("multi.iso"),
            modules = mapOf("GAMECORE.XFF" to payload)
        )
        Iso9660Image(imageFile).use { image ->
            val entry = image.require("GAMECORE.XFF")
            assertEquals(payload.size.toLong(), entry.size)
            val read = image.readFile(entry)
            assertTrue(payload.contentEquals(read))
        }
    }
}
