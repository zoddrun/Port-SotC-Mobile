package com.portsotc.mobile.core

/**
 * What the device's GLES driver actually reports, queried through a real
 * offscreen EGL context by `libsotc_core.so`.
 *
 * The Android manifest and `ActivityManager` only advertise a version; this
 * record proves which context the driver accepted and which vendor, renderer
 * and driver strings it exposes. An unavailable record is a normal result on a
 * driver that rejects every context, never a crash.
 */
data class GpuInfo(
    val available: Boolean = false,
    val backend: String = "",
    val vendor: String = "",
    val renderer: String = "",
    val version: String = "",
    val shadingLanguage: String = "",
    val maxTextureSize: Int = 0,
    val maxVertexAttribs: Int = 0,
    val extensionCount: Int = 0,
    val error: String = ""
) {

    /** Multi-line, monospace-friendly readout for the settings screens. */
    fun summary(): String = if (!available) {
        buildString {
            appendLine("GPU QUERY  unavailable")
            append("REASON     ${error.ifBlank { "no OpenGL ES context" }}")
        }
    } else {
        buildString {
            appendLine("BACKEND    $backend")
            appendLine("VENDOR     ${vendor.ifBlank { "unknown" }}")
            appendLine("RENDERER   ${renderer.ifBlank { "unknown" }}")
            appendLine("DRIVER     ${version.ifBlank { "unknown" }}")
            appendLine("GLSL       ${shadingLanguage.ifBlank { "unknown" }}")
            append(
                "LIMITS     texture $maxTextureSize · " +
                    "attributes $maxVertexAttribs · extensions $extensionCount"
            )
        }
    }

    companion object {
        /**
         * Parses the native `key=value` report. Unknown keys and malformed
         * numbers fall back to defaults so a driver string can never make the
         * settings screen fail.
         */
        fun parse(report: String?): GpuInfo {
            if (report.isNullOrBlank()) {
                return GpuInfo(error = "No capability report")
            }
            val values = HashMap<String, String>()
            for (line in report.lineSequence()) {
                if (line.isBlank() || !line.contains('=')) continue
                values[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
            }
            return GpuInfo(
                available = values["available"] == "1",
                backend = values["backend"].orEmpty(),
                vendor = values["vendor"].orEmpty(),
                renderer = values["renderer"].orEmpty(),
                version = values["version"].orEmpty(),
                shadingLanguage = values["glsl"].orEmpty(),
                maxTextureSize = values["maxTextureSize"]?.toIntOrNull() ?: 0,
                maxVertexAttribs = values["maxVertexAttribs"]?.toIntOrNull() ?: 0,
                extensionCount = values["extensions"]?.toIntOrNull() ?: 0,
                error = values["error"].orEmpty()
            )
        }
    }
}
