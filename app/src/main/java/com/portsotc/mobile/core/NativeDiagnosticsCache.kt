package com.portsotc.mobile.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * App-private cache for diagnostics-only native reports.
 *
 * The cache never feeds gameplay configuration. It exists so the advanced
 * settings screen can show the last real GPU/AAudio engine probe without
 * repeatedly creating an EGL context.
 */
data class NativeDiagnosticsSnapshot(
    val createdAt: Long,
    val abi: String,
    val build: String,
    val gpuSummary: String,
    val coreStatus: String,
    val hdShaderStatus: String,
    val nativeAvailable: Boolean,
    val lastError: String
)

class NativeDiagnosticsCache(private val root: File) {

    private val file: File get() = File(root, FILE_NAME)

    fun load(): NativeDiagnosticsSnapshot? {
        val source = file
        if (!source.isFile) return null
        return try {
            val properties = Properties()
            FileInputStream(source).use { properties.load(it) }
            if (properties.getProperty("format") != FORMAT_VERSION.toString()) return null
            NativeDiagnosticsSnapshot(
                createdAt = properties.getProperty("createdAt")?.toLongOrNull() ?: return null,
                abi = properties.getProperty("abi", ""),
                build = properties.getProperty("build", ""),
                gpuSummary = properties.getProperty("gpuSummary", ""),
                coreStatus = properties.getProperty("coreStatus", ""),
                hdShaderStatus = properties.getProperty("hdShaderStatus", ""),
                nativeAvailable = properties.getProperty("nativeAvailable") == "1",
                lastError = properties.getProperty("lastError", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(snapshot: NativeDiagnosticsSnapshot) {
        root.mkdirs()
        val properties = Properties()
        properties.setProperty("format", FORMAT_VERSION.toString())
        properties.setProperty("createdAt", snapshot.createdAt.toString())
        properties.setProperty("abi", snapshot.abi)
        properties.setProperty("build", snapshot.build)
        properties.setProperty("gpuSummary", snapshot.gpuSummary)
        properties.setProperty("coreStatus", snapshot.coreStatus)
        properties.setProperty("hdShaderStatus", snapshot.hdShaderStatus)
        properties.setProperty("nativeAvailable", if (snapshot.nativeAvailable) "1" else "0")
        properties.setProperty("lastError", snapshot.lastError)
        val temporary = File(root, "$FILE_NAME.part")
        FileOutputStream(temporary).use { properties.store(it, "Port SotC Mobile native diagnostics cache") }
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) throw IllegalStateException("Unable to write native diagnostics cache")
        }
    }

    fun clear() {
        file.delete()
    }

    fun isEmpty(): Boolean = !file.isFile

    private companion object {
        const val FORMAT_VERSION = 1
        const val FILE_NAME = "native-diagnostics.properties"
    }
}
