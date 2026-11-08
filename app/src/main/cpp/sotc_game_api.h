#pragma once

/**
 * Public ABI between the Android host (libsotc_core.so) and a statically
 * recompiled game core (libsotc_game.so).
 *
 * The game core is produced separately from a legally obtained disc image by
 * the PS2Recomp-based pipeline documented in docs/ARCHITECTURE.md. It is not
 * part of this repository. The host loads it at runtime; when it is absent the
 * application runs the native diagnostic renderer instead of pretending that
 * gameplay is available.
 *
 * ABI version 1 is append-only: new optional entry points may be added at the
 * end of SOTCGameApi, but existing field order and semantics must not change.
 */

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define SOTC_GAME_API_VERSION 1u

typedef struct SOTCGameInfo {
    uint32_t apiVersion;
    const char* name;
    const char* serial;
    const char* buildId;
} SOTCGameInfo;

typedef struct SOTCGameHost {
    uint32_t apiVersion;
    void* userData;

    /** level: 0 debug, 1 info, 2 warning, 3 error. */
    void (*log)(void* userData, int32_t level, const char* message);

    /** Returns the current SCE_PAD button bit mask. */
    uint32_t (*padButtons)(void* userData);

    /** Writes normalized stick values in [-1, 1]. */
    void (*padSticks)(void* userData, float* leftX, float* leftY, float* rightX, float* rightY);

    /**
     * Reads a file from the imported game directory into `buffer`.
     * Returns bytes read, or -1 when the file does not exist.
     */
    int64_t (*readGameFile)(void* userData, const char* name, void* buffer, uint32_t capacity);
} SOTCGameHost;

typedef struct SOTCGameApi {
    uint32_t apiVersion;

    const SOTCGameInfo* (*queryInfo)(void);
    int32_t (*initialize)(const SOTCGameHost* host, const char* gameDirectory);
    void (*shutdown)(void);
    void (*render)(int32_t width, int32_t height, double frameTimeSeconds);
    void (*audioRender)(float* interleavedStereo, int32_t frames);
    void (*setPaused)(int32_t paused);
} SOTCGameApi;

/** Mandatory exported symbol of libsotc_game.so. */
typedef const SOTCGameApi* (*sotc_game_query_api_fn)(void);

#ifdef __cplusplus
}
#endif
