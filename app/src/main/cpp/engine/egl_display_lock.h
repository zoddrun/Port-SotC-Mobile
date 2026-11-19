#pragma once

#include <mutex>

/**
 * Process-wide lock for EGL display-level calls.
 *
 * EGL initializes and terminates a display, not a context, so
 * `eglInitialize`/`eglTerminate` from one thread can invalidate every context
 * that another thread is using. Two places touch the display lifecycle: the
 * render thread (window surface and context) and the capability query (a short
 * lived offscreen pbuffer and context). Both take this lock, so a capability
 * query can never run while the render thread tears its context down.
 *
 * Context-local GL calls do not need the lock.
 */
inline std::mutex& sotcEglDisplayMutex() {
    static std::mutex mutex;
    return mutex;
}
