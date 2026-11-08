#pragma once

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

/**
 * Owns the EGL display, context and window surface for the render thread.
 * All methods must be called from that thread after initialize().
 */
class EglRenderer {
public:
    EglRenderer() = default;
    ~EglRenderer();

    EglRenderer(const EglRenderer&) = delete;
    EglRenderer& operator=(const EglRenderer&) = delete;

    bool initialize(ANativeWindow* window);
    void shutdown();

    bool valid() const { return surface_ != EGL_NO_SURFACE && context_ != EGL_NO_CONTEXT; }
    bool makeCurrent();
    void swapBuffers();

    int surfaceWidth() const;
    int surfaceHeight() const;

private:
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLSurface surface_ = EGL_NO_SURFACE;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLConfig config_ = nullptr;
    ANativeWindow* window_ = nullptr;
};
