package com.portsotc.mobile.core

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/**
 * The recompiled runtime's per-launch console log.
 *
 * `GameNativeActivity` redirects the process's stdout/stderr to
 * `<files>/logs/game-<yyyyMMdd-HHmmss>.log` before the runner library loads, so
 * this is where a session's boot messages and failures are recorded. The app
 * never writes to these files itself; it only lists and reads them.
 */
object GameLogs {

    const val FILE_PREFIX = "game-"
    const val FILE_SUFFIX = ".log"

    /** Enough context for a failed boot without loading a whole session. */
    const val DEFAULT_TAIL_BYTES = 64 * 1024

    /**
     * The newest `game-*.log` in [logsRoot], or null when no session has run.
     * Other files in the directory are ignored.
     */
    fun latest(logsRoot: File): File? =
        logsRoot.listFiles()
            ?.asSequence()
            ?.filter {
                it.isFile &&
                    it.name.startsWith(FILE_PREFIX) &&
                    it.name.endsWith(FILE_SUFFIX)
            }
            ?.maxByOrNull { it.lastModified() }

    /**
     * The last [maxBytes] of [file] as text, starting at a line boundary when
     * the file had to be truncated (a partial first line would be misleading).
     * Returns an empty string for a missing, empty or unreadable file.
     */
    fun tail(file: File, maxBytes: Int = DEFAULT_TAIL_BYTES): String {
        if (maxBytes <= 0 || !file.isFile) return ""
        val length = file.length()
        if (length <= 0) return ""
        return try {
            RandomAccessFile(file, "r").use { handle ->
                val start = maxOf(0L, length - maxBytes)
                handle.seek(start)
                val bytes = ByteArray((length - start).toInt())
                val read = handle.read(bytes)
                if (read <= 0) return ""
                var text = String(bytes, 0, read, Charsets.UTF_8)
                if (start > 0) {
                    val newline = text.indexOf('\n')
                    text = if (newline >= 0) text.substring(newline + 1) else ""
                }
                text
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** `game-20261118-114630.log - 12.4 KB`, for the Advanced screen. */
    fun describe(file: File): String = "${file.name} - ${size(file.length())}"

    /** Human-readable size with a dot decimal separator regardless of locale. */
    fun size(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
