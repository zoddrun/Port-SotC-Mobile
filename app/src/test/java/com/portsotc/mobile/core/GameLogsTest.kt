package com.portsotc.mobile.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameLogsTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sotc-game-logs").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun latestPicksTheNewestSessionAndIgnoresOtherFiles() {
        val older = log("game-20261118-100000.log", "old", modified = 1_000L)
        val newer = log("game-20261118-120000.log", "new", modified = 2_000L)
        log("notes.txt", "not a log", modified = 3_000L)
        log("game-20261118-130000.txt", "wrong suffix", modified = 4_000L)

        assertEquals(newer, GameLogs.latest(root))
        assertEquals(true, older.exists())
    }

    @Test
    fun latestIsNullWithoutAnySession() {
        assertNull(GameLogs.latest(root))
        assertNull(GameLogs.latest(File(root, "missing")))
    }

    @Test
    fun tailReturnsAWholeSmallFile() {
        val file = log("game-20261118-100000.log", "line one\nline two\n")

        assertEquals("line one\nline two\n", GameLogs.tail(file))
    }

    @Test
    fun tailKeepsTheEndAndStartsAtALineBoundary() {
        val file = log("game-20261118-100000.log", "first\nsecond\nthird\n")

        assertEquals("third\n", GameLogs.tail(file, maxBytes = 12))
    }

    @Test
    fun tailOfAnEmptyFileIsEmpty() {
        val file = log("game-20261118-100000.log", "")

        assertEquals("", GameLogs.tail(file))
    }

    @Test
    fun tailOfAMissingFileIsEmpty() {
        assertEquals("", GameLogs.tail(File(root, "game-missing.log")))
    }

    @Test
    fun sizesUseLocaleIndependentUnits() {
        assertEquals("512 B", GameLogs.size(512))
        assertEquals("1.5 KB", GameLogs.size(1536))
        assertEquals("2.0 MB", GameLogs.size(2L * 1024L * 1024L))
    }

    @Test
    fun describeShowsTheFileNameAndSize() {
        val file = log("game-20261118-100000.log", "abcd")

        assertEquals("game-20261118-100000.log - 4 B", GameLogs.describe(file))
    }

    private fun log(name: String, text: String, modified: Long = 0L): File =
        File(root, name).apply {
            writeText(text)
            if (modified > 0) setLastModified(modified)
        }
}
