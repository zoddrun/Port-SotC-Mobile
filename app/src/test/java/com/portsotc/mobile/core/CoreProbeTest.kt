package com.portsotc.mobile.core

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CoreProbeTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sotc-core-probe").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun findsTheRuntimeForASupportedAbi() {
        val apk = apkWith("lib/arm64-v8a/${CoreProbe.RUNTIME_LIBRARY_NAME}")

        assertTrue(
            CoreProbe.containsNativeLibrary(
                apk,
                CoreProbe.RUNTIME_LIBRARY_NAME,
                arrayOf("arm64-v8a")
            )
        )
    }

    @Test
    fun ignoresLibrariesForOtherAbis() {
        val apk = apkWith("lib/x86_64/${CoreProbe.RUNTIME_LIBRARY_NAME}")

        assertFalse(
            CoreProbe.containsNativeLibrary(
                apk,
                CoreProbe.RUNTIME_LIBRARY_NAME,
                arrayOf("arm64-v8a")
            )
        )
    }

    @Test
    fun doesNotMatchADifferentLibraryName() {
        val apk = apkWith("lib/arm64-v8a/${CoreProbe.CORE_LIBRARY_NAME}")

        assertFalse(
            CoreProbe.containsNativeLibrary(
                apk,
                CoreProbe.RUNTIME_LIBRARY_NAME,
                arrayOf("arm64-v8a")
            )
        )
    }

    @Test
    fun reportsMissingApkAsAbsent() {
        assertFalse(
            CoreProbe.containsNativeLibrary(
                File(root, "missing.apk"),
                CoreProbe.RUNTIME_LIBRARY_NAME,
                arrayOf("arm64-v8a")
            )
        )
    }

    @Test
    fun reportsCorruptArchiveAsAbsent() {
        val corrupt = File(root, "corrupt.apk").apply { writeText("not a zip") }

        assertFalse(
            CoreProbe.containsNativeLibrary(
                corrupt,
                CoreProbe.RUNTIME_LIBRARY_NAME,
                arrayOf("arm64-v8a")
            )
        )
    }

    private fun apkWith(entry: String): File {
        val apk = File(root, "app.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entry))
            zip.write(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))
            zip.closeEntry()
        }
        return apk
    }
}
