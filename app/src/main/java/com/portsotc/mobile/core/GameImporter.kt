package com.portsotc.mobile.core

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.portsotc.mobile.disc.DiscValidation
import com.portsotc.mobile.disc.DiscValidator
import com.portsotc.mobile.disc.Iso9660Image
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Imports a user-selected ISO image into app-private storage.
 *
 * Import is deliberately staged so that a partially copied or partially
 * validated image can never be mistaken for an installed game:
 *
 * 1. Copy the content URI into a unique `.part` file while hashing it.
 * 2. Validate the staged copy as a PS2 disc (never trust the display name).
 * 3. Assemble the game directory in a `.tmp` directory and extract modules.
 * 4. Atomically publish the directory, keeping the previous install as a
 *    backup until the new one is complete.
 *
 * Cancellation at any point removes staged data. The original ISO is never
 * modified or deleted; only the app-private copy is managed.
 */
class GameImporter(
    private val context: Context,
    private val library: GameLibrary,
    private val paths: AppPaths
) {

    enum class Stage { COPYING, VALIDATING, EXTRACTING, FINALIZING }

    data class Progress(
        val stage: Stage,
        val processedBytes: Long,
        val totalBytes: Long,
        val message: String
    ) {
        val fraction: Float?
            get() = if (totalBytes > 0L) {
                (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class SourceInfo(val displayName: String, val sizeBytes: Long?)

    sealed class Result {
        data class Success(val game: InstalledGame, val validation: DiscValidation) : Result()
        data class Failure(val message: String, val validation: DiscValidation? = null) : Result()
        data object Cancelled : Result()
    }

    private val validator = DiscValidator()

    fun querySource(uri: Uri): SourceInfo {
        var name = uri.lastPathSegment ?: "selected image"
        var size: Long? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // Some providers do not implement metadata queries. The copy still
            // works and validation decides whether the image is usable.
        }
        return SourceInfo(name, size)
    }

    fun import(
        uri: Uri,
        cancel: AtomicBoolean,
        listener: (Progress) -> Unit
    ): Result {
        paths.ensureDirectories()
        library.recoverInterruptedImports()

        val source = querySource(uri)
        val part = File(paths.importCache, "import-${UUID.randomUUID()}.iso.part")
        var tempDirectory: File? = null
        try {
            checkFreeSpace(source.sizeBytes ?: 0L, "copying the image")
            val digest = copyToStaging(uri, part, source.sizeBytes, cancel, listener)
                ?: return Result.Cancelled

            listener(Progress(Stage.VALIDATING, 0, 0, "Validating PlayStation 2 disc structure"))
            val validation = validator.validate(part)
            if (!validation.valid) {
                return Result.Failure(validation.errorSummary(), validation)
            }
            val serial = validation.serial
                ?: return Result.Failure("The disc serial could not be determined", validation)
            val release = com.portsotc.mobile.disc.SupportedDiscs.bySerial(serial)
                ?: return Result.Failure("Unsupported disc serial $serial", validation)

            val moduleBytes = validation.modules.sumOf { it.size }
            checkFreeSpace(validation.imageSize + moduleBytes, "storing the imported game")

            if (cancel.get()) return Result.Cancelled

            val staging = File(paths.gamesRoot, "$serial.tmp")
            tempDirectory = staging
            staging.deleteRecursively()
            if (!staging.mkdirs()) {
                throw IOException("Unable to create staging directory")
            }
            val modulesDirectory = File(staging, GameLibrary.MODULES_DIRECTORY)
            if (!modulesDirectory.mkdirs()) {
                throw IOException("Unable to create module directory")
            }

            listener(Progress(Stage.FINALIZING, 0, 0, "Publishing validated disc image"))
            val discFile = File(staging, GameLibrary.ISO_NAME)
            moveFile(part, discFile)

            listener(
                Progress(
                    Stage.EXTRACTING,
                    0,
                    moduleBytes,
                    "Extracting ${validation.modules.size} engine modules"
                )
            )
            val extracted = extractModules(
                discFile,
                modulesDirectory,
                validation,
                cancel,
                listener
            ) ?: return Result.Cancelled

            library.writeDigest(staging, digest)
            library.writeManifest(
                directory = staging,
                serial = serial,
                title = release.title,
                bootFile = validation.bootFile ?: release.bootFile,
                sha256 = digest,
                imageSize = validation.imageSize,
                modules = extracted
            )

            val finalDirectory = paths.gameDirectory(serial)
            preserveSaves(finalDirectory, staging)
            publish(staging, finalDirectory)
            tempDirectory = null

            val installed = library.readGame(finalDirectory)
                ?: return Result.Failure("Imported files failed the final consistency check", validation)
            listener(Progress(Stage.FINALIZING, installed.imageSize, installed.imageSize, "Import complete"))
            return Result.Success(installed, validation)
        } catch (exception: IOException) {
            return Result.Failure(exception.message ?: "Import failed while accessing storage")
        } catch (exception: SecurityException) {
            return Result.Failure("Access to the selected file was denied: ${exception.message}")
        } finally {
            if (part.exists()) part.delete()
            tempDirectory?.let { directory ->
                if (directory.exists() && !directory.name.endsWith(".backup")) {
                    directory.deleteRecursively()
                }
            }
        }
    }

    private fun copyToStaging(
        uri: Uri,
        target: File,
        expectedSize: Long?,
        cancel: AtomicBoolean,
        listener: (Progress) -> Unit
    ): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected file could not be opened")
        var processed = 0L
        var lastReported = -1L
        input.use { source ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    if (cancel.get()) return null
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    processed += read
                    if (processed - lastReported >= PROGRESS_INTERVAL) {
                        lastReported = processed
                        listener(
                            Progress(
                                Stage.COPYING,
                                processed,
                                expectedSize ?: -1L,
                                "Copying disc image into app storage"
                            )
                        )
                    }
                }
                output.fd.sync()
            }
        }
        listener(Progress(Stage.COPYING, processed, expectedSize ?: processed, "Copy complete"))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractModules(
        discFile: File,
        modulesDirectory: File,
        validation: DiscValidation,
        cancel: AtomicBoolean,
        listener: (Progress) -> Unit
    ): List<String>? {
        val extracted = ArrayList<String>(validation.modules.size)
        var processed = 0L
        val total = validation.modules.sumOf { it.size }
        Iso9660Image(discFile).use { image ->
            for (summary in validation.modules) {
                if (cancel.get()) return null
                val entry = image.find(summary.path)
                    ?: throw IOException("Module ${summary.path} disappeared from the disc image")
                val outputFile = File(modulesDirectory, entry.name)
                val temporary = File(modulesDirectory, "${entry.name}.part")
                image.openFile(entry).use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(1 shl 20)
                        while (true) {
                            if (cancel.get()) return null
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            processed += read
                            listener(
                                Progress(
                                    Stage.EXTRACTING,
                                    processed,
                                    total,
                                    "Extracting ${entry.name}"
                                )
                            )
                        }
                        output.fd.sync()
                    }
                }
                if (!temporary.renameTo(outputFile)) {
                    temporary.delete()
                    throw IOException("Unable to finalize extracted module ${entry.name}")
                }
                extracted.add(entry.name)
            }
        }
        return extracted
    }

    private fun publish(staging: File, finalDirectory: File) {
        val backup = File(paths.gamesRoot, "${finalDirectory.name}.backup")
        if (backup.exists()) {
            backup.deleteRecursively()
        }
        val hadPrevious = finalDirectory.exists()
        if (hadPrevious && !finalDirectory.renameTo(backup)) {
            throw IOException("Unable to replace the previous installation")
        }
        if (!staging.renameTo(finalDirectory)) {
            if (hadPrevious) backup.renameTo(finalDirectory)
            throw IOException("Unable to publish the imported game directory")
        }
        if (hadPrevious) backup.deleteRecursively()
        backup.deleteRecursively()
    }

    private fun preserveSaves(previousDirectory: File, staging: File) {
        val previousSaves = File(previousDirectory, GameLibrary.SAVES_DIRECTORY)
        if (!previousSaves.isDirectory) return
        val stagedSaves = File(staging, GameLibrary.SAVES_DIRECTORY)
        if (stagedSaves.exists()) return
        if (!previousSaves.renameTo(stagedSaves)) {
            // Saving the existing saves is best-effort; a failure here must not
            // invalidate the newly validated import.
        }
    }

    private fun moveFile(source: File, target: File) {
        if (source.renameTo(target)) return
        source.inputStream().use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        if (!source.delete()) {
            throw IOException("Unable to remove staged disc image")
        }
    }

    private fun checkFreeSpace(requiredBytes: Long, operation: String) {
        if (requiredBytes <= 0L) return
        val stat = StatFs(paths.filesRoot.absolutePath)
        val available = stat.availableBytes
        val reserve = 64L * 1024L * 1024L
        if (available < requiredBytes + reserve) {
            throw IOException(
                "Not enough free storage to finish $operation: " +
                    "need ${formatBytes(requiredBytes + reserve)}, " +
                    "available ${formatBytes(available)}"
            )
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL = 4L * 1024L * 1024L

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unit = -1
            while (value >= 1024.0 && unit < units.lastIndex) {
                value /= 1024.0
                unit++
            }
            return String.format(java.util.Locale.US, "%.2f %s", value, units[unit])
        }
    }
}
