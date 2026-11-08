package com.portsotc.mobile.disc

import java.io.IOException
import java.io.InputStream

/**
 * Reads up to [maxBytes] bytes without requiring Java 9 APIs. Kotlin/Android
 * exposes InputStream.readNBytes only from API 33, while this application
 * supports API 28 and later.
 *
 * Returns fewer bytes than requested when the stream reaches its end.
 */
internal fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    val buffer = ByteArray(maxBytes)
    var total = 0
    while (total < maxBytes) {
        val read = read(buffer, total, maxBytes - total)
        if (read < 0) break
        if (read == 0) {
            // A zero-length read is only legal when the requested length is
            // zero. Guard against a misbehaving stream that would spin.
            val next = read()
            if (next < 0) break
            buffer[total++] = next.toByte()
        } else {
            total += read
        }
    }
    if (total == buffer.size) return buffer
    return buffer.copyOf(total)
}

/**
 * Reads exactly [size] bytes and fails when the stream ends early. Used for
 * header-only reads where a short result must be treated as corruption.
 */
internal fun InputStream.readExactly(size: Int): ByteArray {
    val bytes = readAtMost(size)
    if (bytes.size != size) {
        throw IOException("Expected $size bytes but only ${bytes.size} were available")
    }
    return bytes
}
