package com.portsotc.mobile.core

import com.portsotc.mobile.disc.SyntheticDisc
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class DiscTreeExtractorTest {

    private lateinit var root: File
    private lateinit var game: InstalledGame
    private val extractor = DiscTreeExtractor()
    private val digest = "a".repeat(64)

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sotc-disc-tree").toFile()
        val directory = File(root, "SCUS-97472").apply { mkdirs() }
        val iso = SyntheticDisc.create(File(directory, GameLibrary.ISO_NAME))
        game = InstalledGame(
            serial = "SCUS-97472",
            title = "Shadow of the Colossus",
            bootFile = "SCUS_974.72",
            directory = directory,
            isoFile = iso,
            sha256 = digest,
            imageSize = iso.length(),
            importedAt = 0L,
            modules = listOf("KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF")
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun extractsTheRuntimeTreeAndWritesTheMarker() {
        val result = extractor.extract(game, AtomicBoolean(false)) {}

        assertTrue("extraction failed: $result", result is DiscTreeExtractor.Result.Success)
        val tree = (result as DiscTreeExtractor.Result.Success).tree
        assertEquals(digest, tree.sha256)
        assertTrue("no files were recorded", tree.fileCount > 0)
        assertTrue("no bytes were recorded", tree.byteCount > 0)

        assertTrue(game.bootElfFile.isFile)
        val header = game.bootElfFile.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
        assertEquals(listOf(0x7F, 0x45, 0x4C, 0x46), header.map { it.toInt() and 0xFF })

        val cnf = File(game.discDirectory, GameLibrary.BOOT_CONFIGURATION)
        assertTrue(cnf.isFile)
        assertTrue(cnf.readText().contains("SCUS_974.72"))

        assertTrue(File(game.discDirectory, "KERNEL.XFF").isFile)
        assertTrue(File(game.discDirectory, "GAMECORE.XFF").length() > 0L)

        assertTrue("memory-card root was not created", game.memoryCardDirectory.isDirectory)
        assertTrue(game.isDiscTreeReady())
        assertFalse(File(game.directory, "${DiscTreeExtractor.STAGING_NAME}.part").exists())
    }

    @Test
    fun aReadyTreeIsNotExtractedAgain() {
        assertTrue(extractor.extract(game, AtomicBoolean(false)) {} is DiscTreeExtractor.Result.Success)
        val marker = File(game.directory, DiscTree.MARKER_NAME)
        val originalMarker = marker.readBytes()

        val boot = game.bootElfFile
        boot.writeBytes(byteArrayOf(1, 2, 3))

        val second = extractor.extract(game, AtomicBoolean(false)) {}
        assertTrue(second is DiscTreeExtractor.Result.Success)
        assertEquals("the ready tree must not be rewritten", 3L, boot.length())
        assertTrue(originalMarker.contentEquals(marker.readBytes()))
    }

    @Test
    fun aDifferentImageDigestForcesReExtraction() {
        assertTrue(extractor.extract(game, AtomicBoolean(false)) {} is DiscTreeExtractor.Result.Success)

        val reimported = game.copy(sha256 = "b".repeat(64))
        assertFalse(reimported.isDiscTreeReady())

        val result = extractor.extract(reimported, AtomicBoolean(false)) {}
        assertTrue(result is DiscTreeExtractor.Result.Success)
        assertEquals("b".repeat(64), (result as DiscTreeExtractor.Result.Success).tree.sha256)
        assertTrue(reimported.isDiscTreeReady())
    }

    @Test
    fun cancellationLeavesNoTreeAndNoMarker() {
        val result = extractor.extract(game, AtomicBoolean(true)) {}

        assertTrue(result is DiscTreeExtractor.Result.Cancelled)
        assertFalse(game.discDirectory.exists())
        assertFalse(File(game.directory, DiscTree.MARKER_NAME).exists())
        assertFalse(File(game.directory, "${DiscTreeExtractor.STAGING_NAME}.part").exists())
    }

    @Test
    fun aMissingImageIsReportedNotThrown() {
        val broken = game.copy(isoFile = File(game.directory, "missing.iso"))

        val result = extractor.extract(broken, AtomicBoolean(false)) {}

        assertTrue(result is DiscTreeExtractor.Result.Failure)
        assertNotNull((result as DiscTreeExtractor.Result.Failure).message)
    }
}
