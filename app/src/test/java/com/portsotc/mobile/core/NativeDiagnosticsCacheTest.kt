package com.portsotc.mobile.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NativeDiagnosticsCacheTest {

    private lateinit var root: File
    private lateinit var cache: NativeDiagnosticsCache

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sotc-native-diagnostics").toFile()
        cache = NativeDiagnosticsCache(File(root, "diagnostics"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun savesAndLoadsADiagnosticsOnlySnapshot() {
        assertTrue(cache.isEmpty())
        assertNull(cache.load())

        val snapshot = NativeDiagnosticsSnapshot(
            createdAt = 1_700_000_000_000L,
            abi = "arm64-v8a",
            build = "R00M72 / release-keys",
            gpuSummary = "GPU: Adreno (TM) 740 / OpenGL ES 3.2",
            coreStatus = "diagnostic core not loaded",
            hdShaderStatus = "HD shader pack: BALANCE",
            nativeAvailable = true,
            lastError = ""
        )
        cache.save(snapshot)

        assertFalse(cache.isEmpty())
        val loaded = cache.load()
        assertNotNull(loaded)
        assertEquals(snapshot, loaded)
    }

    @Test
    fun aCorruptOrOutdatedSnapshotIsIgnored() {
        val cacheFile = File(File(root, "diagnostics"), "native-diagnostics.properties")
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("format=99\nabi=arm64-v8a\n")

        assertNull("an incompatible format version must be ignored", cache.load())
    }

    @Test
    fun clearRemovesTheCachedReport() {
        cache.save(
            NativeDiagnosticsSnapshot(
                createdAt = 1L,
                abi = "arm64-v8a",
                build = "test",
                gpuSummary = "test",
                coreStatus = "test",
                hdShaderStatus = "test",
                nativeAvailable = false,
                lastError = "test error"
            )
        )
        assertNotNull(cache.load())

        cache.clear()

        assertTrue(cache.isEmpty())
        assertNull(cache.load())
    }
}
