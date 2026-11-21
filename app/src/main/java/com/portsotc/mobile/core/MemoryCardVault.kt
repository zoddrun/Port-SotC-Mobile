package com.portsotc.mobile.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * File-level backup vault for the app-private memory-card directory.
 *
 * This is not a PS2 memory-card parser and it does not claim save compatibility.
 * It copies the files the recompiled runtime has written under `disc/mc0` into a
 * versioned ZIP in app-private storage and can restore them. Backups are never
 * committed with the application.
 */
class MemoryCardVault(private val root: File) {

    data class Backup(
        val name: String,
        val serial: String,
        val createdAt: Long,
        val fileCount: Int,
        val byteCount: Long,
        val file: File
    ) {
        val displayName: String get() = name.removeSuffix(ZIP_SUFFIX)
    }

    data class RestoreResult(val previousDirectory: File?)

    init {
        root.mkdirs()
    }

    fun listBackups(): List<Backup> {
        val files = root.listFiles { file -> file.isFile && file.name.matches(BACKUP_NAME_REGEX) }
            ?: return emptyList()
        return files.mapNotNull { file -> readBackup(file) }
            .sortedByDescending { it.createdAt }
    }

    fun find(name: String): Backup? {
        val safeName = sanitizeName(name)
        val file = File(root, safeName)
        if (!file.isFile || file.canonicalFile.parentFile?.canonicalPath != root.canonicalPath) return null
        return readBackup(file)
    }

    /**
     * Creates a ZIP snapshot of every file currently present in the runtime's
     * memory-card directory. An empty directory is rejected so a backup never
     * looks like progress when no save data exists.
     */
    fun createBackup(game: InstalledGame, now: Long = System.currentTimeMillis()): Backup {
        val source = game.memoryCardDirectory
        if (!source.isDirectory) throw IOException("The memory-card directory does not exist")
        val files = source.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) throw IOException("No memory-card files are available to back up")

        val serial = sanitizeComponent(game.serial)
        val stamp = SimpleDateFormat(NAME_TIME_FORMAT, Locale.US).format(Date(now))
        val name = "mc-$serial-$stamp$ZIP_SUFFIX"
        val finalFile = File(root, name)
        val temporary = File(root, "$name.part")
        val meta = File(root, "$name$META_SUFFIX")
        val temporaryMeta = File(root, "$name.part$META_SUFFIX")

        try {
            var byteCount = 0L
            ZipOutputStream(FileOutputStream(temporary)).use { zip ->
                files.forEach { file ->
                    val relative = relativePath(source, file)
                        ?: throw IOException("Unsafe memory-card path: ${file.absolutePath}")
                    val entry = ZipEntry(relative)
                    entry.time = now
                    zip.putNextEntry(entry)
                    FileInputStream(file).use { input -> input.copyTo(zip, COPY_BUFFER_SIZE) }
                    zip.closeEntry()
                    byteCount += file.length()
                }
            }
            writeMeta(temporaryMeta, game.serial, now, files.size, byteCount)
            if (!temporary.renameTo(finalFile)) throw IOException("Unable to publish the backup archive")
            if (!temporaryMeta.renameTo(meta)) {
                finalFile.delete()
                throw IOException("Unable to publish the backup metadata")
            }
            return Backup(name, game.serial, now, files.size, byteCount, finalFile)
        } finally {
            temporary.delete()
            temporaryMeta.delete()
        }
    }

    /**
     * Restores a snapshot into a staged directory, then replaces the live
     * memory-card directory. The previous directory is retained under a
     * separate name so a restore can be undone from app storage.
     */
    fun restore(name: String, game: InstalledGame, now: Long = System.currentTimeMillis()): RestoreResult {
        if (!game.isDiscTreeReady()) throw IOException("Prepare the runtime files before restoring a backup")
        val backup = find(name) ?: throw IOException("Backup not found: $name")

        val staging = File(game.directory, "mc-restore-$now.part")
        if (staging.exists() && !staging.deleteRecursively()) {
            throw IOException("Unable to clear the restore staging directory")
        }
        var stagedFiles = 0
        try {
            staging.mkdirs()
            ZipFile(backup.file).use { zip ->
                val entries = zip.entries().toList().filter { !it.isDirectory }
                entries.forEach { entry ->
                    val relative = safeZipPath(entry.name)
                        ?: throw IOException("Unsafe path in backup archive: ${entry.name}")
                    val target = File(staging, relative)
                    if (!canonicalIsInside(staging, target)) {
                        throw IOException("Unsafe path in backup archive: ${entry.name}")
                    }
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        zip.getInputStream(entry).use { input -> input.copyTo(output, COPY_BUFFER_SIZE) }
                    }
                    stagedFiles++
                }
            }
            if (stagedFiles == 0) throw IOException("The backup archive contains no files")

            val live = game.memoryCardDirectory
            val previous = if (live.isDirectory) live else null
            val previousTarget = File(game.directory, "mc-before-restore-$now")
            if (previous != null) {
                if (previousTarget.exists() && !previousTarget.deleteRecursively()) {
                    throw IOException("Unable to clear the previous memory-card snapshot")
                }
                if (!previous.renameTo(previousTarget)) {
                    throw IOException("Unable to preserve the current memory-card files")
                }
            }
            if (!staging.renameTo(live)) {
                if (previous != null && !previousTarget.renameTo(live)) {
                    throw IOException("Unable to restore the previous memory-card files")
                }
                throw IOException("Unable to publish the restored memory-card files")
            }
            return RestoreResult(previousTarget.takeIf { it.isDirectory })
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    fun delete(name: String): Boolean {
        val safeName = sanitizeName(name)
        val file = File(root, safeName)
        if (!file.isFile || file.canonicalFile.parentFile?.canonicalPath != root.canonicalPath) return false
        val metadata = File(root, "$safeName$META_SUFFIX")
        val deleted = file.delete()
        metadata.delete()
        return deleted
    }

    private fun readBackup(file: File): Backup? {
        val metadata = File(root, "${file.name}$META_SUFFIX")
        if (!metadata.isFile) return null
        return try {
            val properties = Properties()
            FileInputStream(metadata).use { properties.load(it) }
            if (properties.getProperty("format") != FORMAT_VERSION.toString()) return null
            val serial = properties.getProperty("serial") ?: return null
            val createdAt = properties.getProperty("createdAt")?.toLongOrNull() ?: return null
            val fileCount = properties.getProperty("files")?.toIntOrNull() ?: return null
            val byteCount = properties.getProperty("bytes")?.toLongOrNull() ?: return null
            Backup(file.name, serial, createdAt, fileCount, byteCount, file)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeMeta(file: File, serial: String, createdAt: Long, files: Int, bytes: Long) {
        val properties = Properties()
        properties.setProperty("format", FORMAT_VERSION.toString())
        properties.setProperty("serial", serial)
        properties.setProperty("createdAt", createdAt.toString())
        properties.setProperty("files", files.toString())
        properties.setProperty("bytes", bytes.toString())
        FileOutputStream(file).use { properties.store(it, "Port SotC Mobile memory-card backup") }
    }

    private fun relativePath(root: File, file: File): String? {
        val relative = file.toRelativeString(root).replace(File.separatorChar, '/')
        return safeZipPath(relative)
    }

    private fun safeZipPath(path: String): String? {
        if (path.isEmpty() || path.startsWith("/") || path.contains('\\') || path.contains(':')) return null
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun canonicalIsInside(root: File, file: File): Boolean =
        file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())

    private fun sanitizeName(name: String): String {
        require(!name.contains('/') && !name.contains('\\') && name !in setOf(".", "..")) {
            "Invalid backup name"
        }
        return name
    }

    private fun sanitizeComponent(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(32).ifEmpty { "UNKNOWN" }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val ZIP_SUFFIX = ".zip"
        private const val META_SUFFIX = ".meta"
        private const val NAME_TIME_FORMAT = "yyyyMMdd-HHmmss-SSS"
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private val BACKUP_NAME_REGEX = Regex("^mc-.+\\.zip$")
    }
}
