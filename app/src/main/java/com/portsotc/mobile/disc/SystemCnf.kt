package com.portsotc.mobile.disc

import java.util.Locale

/**
 * Parser for the `SYSTEM.CNF` boot configuration stored in the root directory
 * of every PlayStation 2 disc.
 *
 * Example contents:
 * ```
 * BOOT2 = cdrom0:\SCUS_974.72;1
 * VER = 1.00
 * VMODE = NTSC
 * ```
 */
object SystemCnf {

    data class Entry(val key: String, val value: String)

    fun parse(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().trimStart('\uFEFF')
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                return@forEach
            }
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().uppercase(Locale.US)
            var value = line.substring(separator + 1).trim()
            if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length - 1)
            }
            if (key.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    /** Extracts the normalized boot file name from a parsed config. */
    fun bootFile(entries: Map<String, String>): String? {
        val raw = entries["BOOT2"] ?: entries["BOOT"] ?: return null
        return normalizeBootPath(raw)
    }

    fun normalizeBootPath(raw: String): String {
        var value = raw.trim()
        val colon = value.indexOf(':')
        if (colon >= 0) {
            value = value.substring(colon + 1)
        }
        value = value.replace('\\', '/')
        value = value.substringBefore(';')
        while (value.startsWith("/")) value = value.substring(1)
        return value.trim()
    }

    /**
     * Derives the disc serial (for example `SCUS-97472`) from a boot file name
     * such as `SCUS_974.72`. This mirrors the filename convention used by Sony
     * on PlayStation 2 masters; it is not used as the only validation signal.
     */
    fun serialFromBootFile(bootFile: String): String? {
        val base = bootFile.substringAfterLast('/').uppercase(Locale.US)
        val match = Regex("^([A-Z]{4})_([0-9]{3})\\.([0-9]{2})$").find(base)
        return if (match != null) {
            "${match.groupValues[1]}-${match.groupValues[2]}${match.groupValues[3]}"
        } else {
            null
        }
    }
}
