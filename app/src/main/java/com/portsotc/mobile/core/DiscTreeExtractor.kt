package com.portsotc.mobile.core

import com.portsotc.mobile.disc.Iso9660Image
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Extracts the imported disc image into the host directory tree the recompiled
 * runtime reads.
 *
 * The runtime does not parse the ISO at run time: guest `cdrom0:` paths are
 * resolved against a host directory (its `cdRoot`), and the boot executable is
 * opened as a real file. This extractor produces that tree inside the game
 * directory and records completion in [DiscTree].
 *
 * The extraction is staged: files are written into `disc.part`, and only a
 * complete tree replaces `disc`. A cancelled or failed extraction leaves the
 * previously published tree untouched, and a tree without a matching marker is
 * never reported as ready.
 */
class DiscTreeExtractor {

    enum class Stage { PREPARING, EXTRACTING, FINALIZING }

    data class Progress(
        val stage: Stage,
        val processedBytes: Long,
        val totalBytes: Long,
        val filesDone: Int,
        val fileCount: Int,
        val message: String
    ) {
        val fraction: Float?
            get() = if (totalBytes > 0L) {
                (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    sealed class Result {
        data class Success(val tree: DiscTree) : Result()
        data class Failure(val message: String) : Result()
        data object Cancelled : Result()
    }

    fun extract(
        game: InstalledGame,
        cancel: AtomicBoolean,
        listener: (Progress) -> Unit
    ): Result {
        val existing = DiscTree.read(game.directory)
        if (game.isDiscTreeReady()) {
            return Result.Success(requireNotNull(existing))
        }

        if (!game.isoFile.isFile) {
            return Result.Failure("The imported disc image is missing")
        }

        val staging = File(game.directory, "$STAGING_NAME.part")
        try {
            staging.deleteRecursively()
            Iso9660Image(game.isoFile).use { image ->
                val files = image.entries.filter { !it.isDirectory }
                val directories = image.entries.filter { it.isDirectory && it.path != "/" }
                val totalBytes = files.sumOf { it.size }

                listener(
                    Progress(
                        Stage.PREPARING,
                        0,
                        totalBytes,
                        0,
                        files.size,
                        "Preparing ${files.size} files (${GameImporter.formatBytes(totalBytes)})"
                    )
                )

                val available = game.directory.usableSpace
                val required = totalBytes + FREE_SPACE_RESERVE
                if (available in 1 until required) {
                    return Result.Failure(
                        "Not enough free storage to extract the disc tree: " +
                            "need ${GameImporter.formatBytes(required)}, " +
                            "available ${GameImporter.formatBytes(available)}"
                    )
                }

                if (!staging.mkdirs()) {
                    return Result.Failure("Unable to create the disc tree staging directory")
                }

                for (directory in directories) {
                    val target = resolve(staging, directory.path)
                        ?: return Result.Failure("Unsafe directory path on disc: ${directory.path}")
                    if (!target.mkdirs() && !target.isDirectory) {
                        return Result.Failure("Unable to create directory ${directory.path}")
                    }
                }

                var processed = 0L
                var filesDone = 0
                var lastReported = -1L
                for (entry in files) {
                    if (cancel.get()) return Result.Cancelled
                    val target = resolve(staging, entry.path)
                        ?: return Result.Failure("Unsafe file path on disc: ${entry.path}")
                    target.parentFile?.let { parent ->
                        if (!parent.mkdirs() && !parent.isDirectory) {
                            return Result.Failure("Unable to create directory for ${entry.path}")
                        }
                    }

                    listener(
                        Progress(
                            Stage.EXTRACTING,
                            processed,
                            totalBytes,
                            filesDone,
                            files.size,
                            "Extracting ${entry.name}"
                        )
                    )

                    val temporary = File(target.parentFile, "${target.name}.part")
                    try {
                        var written = 0L
                        image.openFile(entry).use { input ->
                            FileOutputStream(temporary).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    if (cancel.get()) return Result.Cancelled
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (read == 0) continue
                                    output.write(buffer, 0, read)
                                    written += read
                                    processed += read
                                    if (processed - lastReported >= PROGRESS_INTERVAL) {
                                        lastReported = processed
                                        listener(
                                            Progress(
                                                Stage.EXTRACTING,
                                                processed,
                                                totalBytes,
                                                filesDone,
                                                files.size,
                                                "Extracting ${entry.name}"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        if (written != entry.size) {
                            throw IOException(
                                "Short read for ${entry.path}: expected ${entry.size} bytes, " +
                                    "wrote $written"
                            )
                        }
                        if (!temporary.renameTo(target)) {
                            throw IOException("Unable to finalize ${entry.path}")
                        }
                    } finally {
                        temporary.delete()
                    }
                    filesDone++
                }

                if (cancel.get()) return Result.Cancelled

                // The runtime derives its memory-card root from the boot
                // executable's directory, so the directory must exist even
                // before the game writes its first save. Re-extraction must not
                // discard files the runtime has already written there.
                val stagedMemoryCards = File(staging, GameLibrary.MEMORY_CARD_DIRECTORY)
                stagedMemoryCards.mkdirs()
                preserveMemoryCards(game.discDirectory, stagedMemoryCards)

                listener(
                    Progress(
                        Stage.FINALIZING,
                        processed,
                        totalBytes,
                        filesDone,
                        files.size,
                        "Publishing the disc tree"
                    )
                )
                publish(game, staging)

                val tree = DiscTree(
                    sha256 = game.sha256,
                    fileCount = files.size,
                    byteCount = totalBytes,
                    completedAt = System.currentTimeMillis()
                )
                DiscTree.write(game.directory, tree)
                return Result.Success(tree)
            }
        } catch (exception: IOException) {
            return Result.Failure(exception.message ?: "Unable to extract the disc tree")
        } catch (exception: Exception) {
            return Result.Failure(exception.message ?: "Unable to extract the disc tree")
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    /**
     * Copies existing runtime memory-card files into a staged tree. This is a
     * file-level preservation step; the extractor does not parse or validate
     * PlayStation 2 memory-card structures.
     */
    private fun preserveMemoryCards(previousRoot: File, stagedDirectory: File) {
        val source = File(previousRoot, GameLibrary.MEMORY_CARD_DIRECTORY)
        if (!source.isDirectory) return
        source.walkTopDown().forEach { entry ->
            val relative = entry.toRelativeString(source)
            if (relative.isEmpty() || relative == ".") return@forEach
            if (relative.split(File.separatorChar).any { it == ".." }) {
                throw IOException("Unsafe memory-card path in the existing tree: $relative")
            }
            val target = File(stagedDirectory, relative)
            if (entry.isDirectory) {
                if (!target.mkdirs() && !target.isDirectory) {
                    throw IOException("Unable to preserve memory-card directory $relative")
                }
            } else if (entry.isFile) {
                target.parentFile?.mkdirs()
                entry.copyTo(target, overwrite = true)
            }
        }
    }

    /**
     * Publishes a complete staged tree, keeping the previous one until the
     * replacement is in place. Only one directory rename is observable at a
     * time, so an interrupted publish leaves either the old or the new tree.
     */
    private fun publish(game: InstalledGame, staging: File) {
        val target = game.discDirectory
        val backup = File(game.directory, "$STAGING_NAME.backup")
        backup.deleteRecursively()
        val hadPrevious = target.exists()
        if (hadPrevious && !target.renameTo(backup)) {
            throw IOException("Unable to replace the previous disc tree")
        }
        if (!staging.renameTo(target)) {
            if (hadPrevious) backup.renameTo(target)
            throw IOException("Unable to publish the extracted disc tree")
        }
        backup.deleteRecursively()
    }

    /** Maps an absolute ISO9660 path onto the staging directory. */
    private fun resolve(root: File, isoPath: String): File? {
        val relative = isoPath.removePrefix("/")
        if (relative.isEmpty()) return null
        val components = relative.split('/')
        if (components.any { it.isEmpty() || it == "." || it == ".." }) return null
        if (components.any { it.contains('\\') || it.contains(':') || it.any { c -> c.code < 0x20 } }) {
            return null
        }
        var current = root
        for (component in components) {
            current = File(current, component)
        }
        return current
    }

    companion object {
        const val STAGING_NAME = "disc"
        private const val BUFFER_SIZE = 1 shl 20
        private const val PROGRESS_INTERVAL = 8L * 1024L * 1024L
        private const val FREE_SPACE_RESERVE = 64L * 1024L * 1024L
    }
}
