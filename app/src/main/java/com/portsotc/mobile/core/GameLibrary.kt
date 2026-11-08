package com.portsotc.mobile.core

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Installed game metadata read from an imported disc image.
 *
 * A game directory is considered installed only when the manifest, the ISO
 * image and every extracted module referenced by the manifest exist. This
 * prevents the UI from claiming a game is ready after a partial import.
 */
data class InstalledGame(
    val serial: String,
    val title: String,
    val bootFile: String,
    val directory: File,
    val isoFile: File,
    val sha256: String,
    val imageSize: Long,
    val importedAt: Long,
    val modules: List<String>
) {
    val moduleDirectory: File get() = File(directory, GameLibrary.MODULES_DIRECTORY)
    val saveDirectory: File get() = File(directory, GameLibrary.SAVES_DIRECTORY)
    val isoPath: String get() = isoFile.absolutePath

    fun isComplete(): Boolean {
        if (!isoFile.isFile || isoFile.length() != imageSize) return false
        if (sha256.length != 64) return false
        return modules.all { File(moduleDirectory, it).isFile }
    }
}

/** Reads and writes installed game records in app-private storage. */
class GameLibrary(private val paths: AppPaths) {

    companion object {
        private const val TAG = "GameLibrary"
        const val MANIFEST_NAME = "manifest.properties"
        const val ISO_NAME = "disc.iso"
        const val SHA256_NAME = "disc.sha256"
        const val MODULES_DIRECTORY = "modules"
        const val SAVES_DIRECTORY = "saves"
        private const val FORMAT_VERSION = 1
    }

    fun installedGames(): List<InstalledGame> {
        val directories = paths.gamesRoot.listFiles { file -> file.isDirectory } ?: return emptyList()
        return directories
            .filterNot { it.name.endsWith(".tmp") || it.name.endsWith(".backup") }
            .mapNotNull { readGame(it) }
            .sortedByDescending { it.importedAt }
    }

    fun primaryGame(): InstalledGame? = installedGames().firstOrNull()

    fun readGame(directory: File): InstalledGame? {
        val manifest = File(directory, MANIFEST_NAME)
        if (!manifest.isFile) return null
        return try {
            val properties = Properties()
            FileInputStream(manifest).use { properties.load(it) }
            if (properties.getProperty("format")?.toIntOrNull() != FORMAT_VERSION) {
                Log.w(TAG, "Ignoring ${directory.name}: unsupported manifest format")
                return null
            }
            val serial = properties.getProperty("serial") ?: return null
            val title = properties.getProperty("title") ?: return null
            val bootFile = properties.getProperty("bootFile") ?: return null
            val sha256 = properties.getProperty("sha256") ?: return null
            val imageSize = properties.getProperty("imageSize")?.toLongOrNull() ?: return null
            val importedAt = properties.getProperty("importedAt")?.toLongOrNull() ?: 0L
            val modules = properties.getProperty("modules")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val game = InstalledGame(
                serial = serial,
                title = title,
                bootFile = bootFile,
                directory = directory,
                isoFile = File(directory, ISO_NAME),
                sha256 = sha256,
                imageSize = imageSize,
                importedAt = importedAt,
                modules = modules
            )
            if (!game.isComplete()) {
                Log.w(TAG, "Ignoring ${directory.name}: installation is incomplete")
                null
            } else {
                game
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Ignoring ${directory.name}: ${exception.message}")
            null
        }
    }

    fun writeManifest(
        directory: File,
        serial: String,
        title: String,
        bootFile: String,
        sha256: String,
        imageSize: Long,
        modules: List<String>
    ) {
        val properties = Properties()
        properties.setProperty("format", FORMAT_VERSION.toString())
        properties.setProperty("serial", serial)
        properties.setProperty("title", title)
        properties.setProperty("bootFile", bootFile)
        properties.setProperty("sha256", sha256)
        properties.setProperty("imageSize", imageSize.toString())
        properties.setProperty("importedAt", System.currentTimeMillis().toString())
        properties.setProperty("modules", modules.joinToString(","))
        val target = File(directory, MANIFEST_NAME)
        val temporary = File(directory, "$MANIFEST_NAME.part")
        FileOutputStream(temporary).use { properties.store(it, "Port SotC Mobile game manifest") }
        if (!temporary.renameTo(target)) {
            target.delete()
            if (!temporary.renameTo(target)) {
                throw java.io.IOException("Unable to write game manifest")
            }
        }
    }

    fun writeDigest(directory: File, sha256: String) {
        File(directory, SHA256_NAME).writeText("$sha256\n", Charsets.US_ASCII)
    }

    /**
     * Removes abandoned import directories and restores backups left behind if
     * the process died during a previous import.
     */
    fun recoverInterruptedImports() {
        paths.importCache.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part") || file.name.endsWith(".tmp")) {
                file.deleteRecursively()
            }
        }
        val gameDirectories = paths.gamesRoot.listFiles() ?: return
        for (directory in gameDirectories) {
            when {
                directory.name.endsWith(".tmp") -> directory.deleteRecursively()
                directory.name.endsWith(".backup") -> {
                    val target = File(paths.gamesRoot, directory.name.removeSuffix(".backup"))
                    if (target.exists()) {
                        directory.deleteRecursively()
                    } else if (!directory.renameTo(target)) {
                        Log.w(TAG, "Unable to restore interrupted import backup ${directory.name}")
                    }
                }
            }
        }
    }
}
