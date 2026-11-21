#pragma once

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>

#include <algorithm>
#include <atomic>
#include <string>

/**
 * Owns the EGL display, context and window surface for the render thread.
 * All methods must be called from that thread after initialize().
 *
 * OpenGL ES 3 is requested first and OpenGL ES 2 is used when the driver
 * rejects it, so the same code path runs on every device that supports ES 2.
 * The shaders are GLSL ES 1.00 precisely so both context versions accept them.
 */
class EglRenderer {
public:
    EglRenderer() = default;
    ~EglRenderer();

    EglRenderer(const EglRenderer&) = delete;
    EglRenderer& operator=(const EglRenderer&) = delete;

    /**
     * 0 asks for the highest supported version (ES 3, then ES 2); 2 forces the
     * OpenGL ES 2 compatibility path. Read when initialize() runs, so a change
     * applies to the next session.
     */
    void setPreferredClientVersion(int version) {
        preferredClientVersion_.store(version == 2 ? 2 : 0);
    }

    bool initialize(ANativeWindow* window);
    void shutdown();

    /**
     * Presentation cadence: 0 drains the queue as fast as the driver accepts
     * frames, 1 follows the display refresh, 2 presents every other refresh.
     * The value is applied on the render thread before the next swap so it is
     * safe to change while a session is running.
     */
    void setSwapInterval(int interval) {
        swapInterval_.store(std::clamp(interval, 0, 2));
    }

    bool valid() const { return surface_ != EGL_NO_SURFACE && context_ != EGL_NO_CONTEXT; }
    bool makeCurrent();
    void swapBuffers();

    int surfaceWidth() const;
    int surfaceHeight() const;

    /**
     * "OpenGL ES 3.0", "OpenGL ES 2.0", or "unavailable". Safe to read from
     * another thread while the render thread initializes or shuts down.
     */
    std::string backendName() const {
        switch (backendKind_.load()) {
            case kBackendKindEs3: return "OpenGL ES 3.0";
            case kBackendKindEs2: return "OpenGL ES 2.0";
            default: return "unavailable";
        }
    }

private:
    void shutdownLocked();
    void applySwapInterval();

    enum BackendKind : int { kBackendKindNone = 0, kBackendKindEs3 = 1, kBackendKindEs2 = 2 };

    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLSurface surface_ = EGL_NO_SURFACE;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLConfig config_ = nullptr;
    ANativeWindow* window_ = nullptr;
    std::atomic<int> preferredClientVersion_{0};
    std::atomic<int> swapInterval_{1};
    std::atomic<int> appliedSwapInterval_{-1};
    std::atomic<int> backendKind_{kBackendKindNone};
};
