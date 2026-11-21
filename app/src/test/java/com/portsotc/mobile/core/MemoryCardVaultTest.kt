package com.portsotc.mobile.core

import com.portsotc.mobile.disc.SyntheticDisc
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class MemoryCardVaultTest {

    private lateinit var root: File
    private lateinit var game: InstalledGame
    private lateinit var vault: MemoryCardVault

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sotc-memory-vault").toFile()
        val directory = File(root, "SCUS-97472").apply { mkdirs() }
        val iso = SyntheticDisc.create(File(directory, GameLibrary.ISO_NAME))
        game = InstalledGame(
            serial = "SCUS-97472",
            title = "Shadow of the Colossus",
            bootFile = "SCUS_974.72",
            directory = directory,
            isoFile = iso,
            sha256 = "a".repeat(64),
            imageSize = iso.length(),
            importedAt = 0L,
            modules = listOf("KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF")
        )
        assertTrue(
            DiscTreeExtractor().extract(game, AtomicBoolean(false)) {} is DiscTreeExtractor.Result.Success
        )
        vault = MemoryCardVault(File(root, "vault"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun createsListsAndRestoresAppPrivateBackups() {
        val original = "slot-magic".toByteArray(Charsets.US_ASCII)
        val memoryCard = File(game.memoryCardDirectory, "SLOT1")
        memoryCard.writeBytes(original)

        val backup = vault.createBackup(game, 1_700_000_000_000L)

        assertEquals(1, vault.listBackups().size)
        assertEquals(game.serial, backup.serial)
        assertEquals(1, backup.fileCount)
        assertEquals(original.size.toLong(), backup.byteCount)
        ZipFile(backup.file).use { zip ->
            assertEquals(original.toList(), zip.getInputStream(zip.getEntry("SLOT1")).readBytes().toList())
        }

        memoryCard.writeBytes("overwritten".toByteArray(Charsets.US_ASCII))
        val result = vault.restore(backup.name, game, 1_700_000_100_000L)

        assertNotNull("previous files were not retained", result.previousDirectory)
        assertEquals(original.toList(), memoryCard.readBytes().toList())
        assertEquals(
            "the pre-restore files must be preserved in the retained directory",
            "overwritten".toByteArray(Charsets.US_ASCII).toList(),
            File(requireNotNull(result.previousDirectory), "SLOT1").readBytes().toList()
        )
    }

    @Test
    fun rejectsAnEmptyMemoryCardDirectory() {
        try {
            vault.createBackup(game, 1_700_000_000_000L)
            fail("an empty backup must not be created")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("No memory-card files"))
        }
        assertTrue(vault.listBackups().isEmpty())
    }

    @Test
    fun rejectsArchivePathsOutsideTheMemoryCardDirectory() {
        File(game.memoryCardDirectory, "SLOT1").writeBytes("safe".toByteArray(Charsets.US_ASCII))
        val backup = vault.createBackup(game, 1_700_000_000_000L)
        val malicious = File(vaultRoot(), "mc-malicious.zip")
        ZipOutputStream(FileOutputStream(malicious)).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.dat"))
            zip.write("unsafe".toByteArray(Charsets.US_ASCII))
            zip.closeEntry()
        }
        val metadata = Properties().apply {
            setProperty("format", "1")
            setProperty("serial", game.serial)
            setProperty("createdAt", "1700000000000")
            setProperty("files", "1")
            setProperty("bytes", "6")
        }
        FileOutputStream(File(vaultRoot(), "mc-malicious.zip.meta")).use { output ->
            metadata.store(output, "test")
        }

        try {
            vault.restore("mc-malicious.zip", game, 1_700_000_100_000L)
            fail("a path-traversal archive must not restore")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("Unsafe path"))
        }
        assertEquals(backup.serial, vault.find(backup.name)?.serial)
        assertTrue(!File(game.directory, "evil.dat").exists())
    }

    @Test
    fun deletesABackupAndItsMetadata() {
        File(game.memoryCardDirectory, "SLOT1").writeBytes("data".toByteArray(Charsets.US_ASCII))
        val backup = vault.createBackup(game, 1_700_000_000_000L)

        assertTrue(vault.delete(backup.name))

        assertTrue(!backup.file.exists())
        assertTrue(!File(vaultRoot(), "${backup.name}.meta").exists())
        assertTrue(vault.listBackups().isEmpty())
    }

    private fun vaultRoot(): File = File(root, "vault")
}
