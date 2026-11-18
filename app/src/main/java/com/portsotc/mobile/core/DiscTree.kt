package com.portsotc.mobile.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Record of a fully extracted disc tree.
 *
 * The recompiled runtime does not read the ISO image directly: it resolves
 * guest `cdrom0:` paths against a host directory tree (the runtime's `cdRoot`)
 * and reads the boot executable from a real file. The tree is therefore
 * extracted once and marked with this record, which also binds it to the
 * imported image digest so a re-imported image forces a fresh extraction.
 *
 * The marker is written last and atomically. A tree without a matching marker
 * is treated as incomplete and is extracted again.
 */
data class DiscTree(
    val sha256: String,
    val fileCount: Int,
    val byteCount: Long,
    val completedAt: Long
) {

    companion object {
        const val MARKER_NAME = "disc_tree.properties"
        private const val FORMAT = 1

        fun read(gameDirectory: File): DiscTree? {
            val marker = File(gameDirectory, MARKER_NAME)
            if (!marker.isFile) return null
            return try {
                val properties = Properties()
                FileInputStream(marker).use { properties.load(it) }
                if (properties.getProperty("format")?.toIntOrNull() != FORMAT) return null
                val sha256 = properties.getProperty("sha256") ?: return null
                DiscTree(
                    sha256 = sha256,
                    fileCount = properties.getProperty("files")?.toIntOrNull() ?: return null,
                    byteCount = properties.getProperty("bytes")?.toLongOrNull() ?: return null,
                    completedAt = properties.getProperty("completedAt")?.toLongOrNull() ?: 0L
                )
            } catch (_: Exception) {
                null
            }
        }

        fun write(gameDirectory: File, tree: DiscTree) {
            val properties = Properties()
            properties.setProperty("format", FORMAT.toString())
            properties.setProperty("sha256", tree.sha256)
            properties.setProperty("files", tree.fileCount.toString())
            properties.setProperty("bytes", tree.byteCount.toString())
            properties.setProperty("completedAt", tree.completedAt.toString())
            val target = File(gameDirectory, MARKER_NAME)
            val temporary = File(gameDirectory, "$MARKER_NAME.part")
            FileOutputStream(temporary).use { output ->
                properties.store(output, "Port SotC Mobile disc tree")
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) {
                    throw java.io.IOException("Unable to write the disc tree marker")
                }
            }
        }

        fun clear(gameDirectory: File) {
            File(gameDirectory, MARKER_NAME).delete()
        }
    }
}
