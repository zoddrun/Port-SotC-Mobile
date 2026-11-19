package com.portsotc.mobile.core

/**
 * What the device's GLES driver actually reports and accepts, queried through
 * a real offscreen EGL context by `libsotc_core.so`.
 *
 * The Android manifest and `ActivityManager` only advertise a version; this
 * record proves which context the driver accepted and which vendor, renderer
 * and driver strings it exposes. An unavailable record is a normal result on a
 * driver that rejects every context, never a crash.
 *
 * Capabilities named `...Advertised` come from an extension list. They are not
 * proof that the operation works; [selfTest] records the probes that were
 * actually attempted. No tier is inferred from a GPU vendor name.
 */
data class GpuInfo(
    val available: Boolean = false,
    val backend: String = "",
    val eglVendor: String = "",
    val eglVersion: String = "",
    val eglExtensions: String = "",
    val vendor: String = "",
    val renderer: String = "",
    val version: String = "",
    val shadingLanguage: String = "",
    val glExtensions: String = "",
    val maxTextureSize: Int = 0,
    val maxRenderbufferSize: Int = 0,
    val maxVertexAttribs: Int = 0,
    val maxTextureImageUnits: Int = 0,
    val maxVertexTextureImageUnits: Int = 0,
    val maxDrawBuffers: Int = 0,
    val maxSamples: Int = 0,
    val extensionCount: Int = 0,
    val framebufferObjectAdvertised: Boolean = false,
    val timerQueryAdvertised: Boolean = false,
    val fenceSyncAdvertised: Boolean = false,
    val selfTest: String = "",
    val error: String = ""
) {

    /** Conservative capability tier derived from probes, never from a vendor name. */
    enum class Tier {
        UNKNOWN,
        ES2_SAFE,
        ES3_BASE,
        ES3_ENHANCED
    }

    private val coreProbesPassed: Boolean
        get() = selfTest.contains("shader=1") &&
            selfTest.contains("rgba8=1") &&
            selfTest.contains("rtt=1")

    fun tier(): Tier = when {
        !available || !coreProbesPassed -> Tier.UNKNOWN
        backend.contains("3.0") && timerQueryAdvertised && fenceSyncAdvertised -> Tier.ES3_ENHANCED
        backend.contains("3.0") -> Tier.ES3_BASE
        else -> Tier.ES2_SAFE
    }

    /** Multi-line, monospace-friendly readout for the settings screens. */
    fun summary(): String = if (!available) {
        buildString {
            appendLine("GPU QUERY  unavailable")
            append("REASON     ${error.ifBlank { "no OpenGL ES context" }}")
        }
    } else {
        buildString {
            appendLine("BACKEND    $backend")
            appendLine("TIER       ${tier()}")
            appendLine("EGL        ${eglVendor.ifBlank { "unknown" }} · ${eglVersion.ifBlank { "unknown" }}")
            appendLine("VENDOR     ${vendor.ifBlank { "unknown" }}")
            appendLine("RENDERER   ${renderer.ifBlank { "unknown" }}")
            appendLine("DRIVER     ${version.ifBlank { "unknown" }}")
            appendLine("GLSL       ${shadingLanguage.ifBlank { "unknown" }}")
            appendLine("LIMITS     texture $maxTextureSize · renderbuffer $maxRenderbufferSize")
            appendLine("UNITS      fragment $maxTextureImageUnits · vertex $maxVertexTextureImageUnits")
            appendLine("TARGETS    draw buffers $maxDrawBuffers · samples $maxSamples · extensions $extensionCount")
            appendLine("SELF TEST  ${selfTest.ifBlank { "not run" }}")
            append(
                "OPTIONAL   timer=${if (timerQueryAdvertised) "advertised" else "no"} " +
                    "fence=${if (fenceSyncAdvertised) "advertised" else "no"}"
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
                eglVendor = values["eglVendor"].orEmpty(),
                eglVersion = values["eglVersion"].orEmpty(),
                eglExtensions = values["eglExtensions"].orEmpty(),
                vendor = values["vendor"].orEmpty(),
                renderer = values["renderer"].orEmpty(),
                version = values["version"].orEmpty(),
                shadingLanguage = values["glsl"].orEmpty(),
                glExtensions = values["glExtensions"].orEmpty(),
                maxTextureSize = values["maxTextureSize"]?.toIntOrNull() ?: 0,
                maxRenderbufferSize = values["maxRenderbufferSize"]?.toIntOrNull() ?: 0,
                maxVertexAttribs = values["maxVertexAttribs"]?.toIntOrNull() ?: 0,
                maxTextureImageUnits = values["maxTextureImageUnits"]?.toIntOrNull() ?: 0,
                maxVertexTextureImageUnits = values["maxVertexTextureImageUnits"]?.toIntOrNull() ?: 0,
                maxDrawBuffers = values["maxDrawBuffers"]?.toIntOrNull() ?: 0,
                maxSamples = values["maxSamples"]?.toIntOrNull() ?: 0,
                extensionCount = values["extensions"]?.toIntOrNull() ?: 0,
                framebufferObjectAdvertised = values["framebufferObjectAdvertised"] == "1",
                timerQueryAdvertised = values["timerQueryAdvertised"] == "1",
                fenceSyncAdvertised = values["fenceSyncAdvertised"] == "1",
                selfTest = values["selfTest"].orEmpty(),
                error = values["error"].orEmpty()
            )
        }
    }
}
