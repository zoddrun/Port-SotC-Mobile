#pragma once

#include <string>

/**
 * What the device's GLES driver actually reports.
 *
 * The values come from a real offscreen EGL context created for the query, not
 * from the Android manifest or `ActivityManager`, so a driver that advertises
 * one version and rejects the matching context is reported honestly.
 */
struct GpuCapabilities {
    bool available = false;

    /** "OpenGL ES 3.0", "OpenGL ES 2.0", or empty when no context was created. */
    std::string backend;
    std::string vendor;
    std::string renderer;
    std::string version;
    std::string shadingLanguage;
    int maxTextureSize = 0;
    int maxVertexAttribs = 0;
    int extensionCount = 0;

    /** Human-readable reason when `available` is false. */
    std::string error;
};

/**
 * Creates a short-lived offscreen EGL context and reads its capabilities.
 *
 * Tries an OpenGL ES 3 context first and falls back to OpenGL ES 2 when the
 * driver rejects ES 3. Pass `forceEs2` to skip the ES 3 attempt entirely. Safe
 * to call from any thread, including while the render thread is running: the
 * display lifecycle is serialized through `sotcEglDisplayMutex()`.
 */
GpuCapabilities queryGpuCapabilities(bool forceEs2);

/**
 * Serializes [GpuCapabilities] as `key=value` lines.
 *
 * A line format (rather than JSON) keeps the Kotlin parser in plain Kotlin, so
 * it is covered by unit tests that do not need an Android runtime. Every value
 * is single-line; newlines inside driver strings become spaces.
 */
std::string gpuCapabilitiesReport(const GpuCapabilities& capabilities);
