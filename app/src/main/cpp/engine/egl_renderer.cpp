#include "egl_renderer.h"
#include "log.h"

// EGL_OPENGL_ES3_BIT lives in the EGL 1.4 core header as EGL_OPENGL_ES3_BIT_KHR
// only when EGL/eglext.h is included. Define the documented value as a
// fallback so the renderer builds against every NDK sysroot revision.
#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

EglRenderer::~EglRenderer() {
    shutdown();
}

bool EglRenderer::initialize(ANativeWindow* window) {
    shutdown();
    window_ = window;

    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) {
        SOTC_LOGE("eglGetDisplay failed");
        return false;
    }
    EGLint major = 0;
    EGLint minor = 0;
    if (eglInitialize(display_, &major, &minor) != EGL_TRUE) {
        SOTC_LOGE("eglInitialize failed: 0x%x", eglGetError());
        shutdown();
        return false;
    }
    SOTC_LOGI("EGL %d.%d initialized", major, minor);

    const EGLint configAttributes[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 0,
        EGL_STENCIL_SIZE, 0,
        EGL_NONE
    };
    EGLint configCount = 0;
    if (eglChooseConfig(display_, configAttributes, &config_, 1, &configCount) != EGL_TRUE ||
        configCount == 0) {
        SOTC_LOGE("No EGL config with OpenGL ES 3 support: 0x%x", eglGetError());
        shutdown();
        return false;
    }

    const EGLint contextAttributes[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };
    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, contextAttributes);
    if (context_ == EGL_NO_CONTEXT) {
        SOTC_LOGE("eglCreateContext failed: 0x%x", eglGetError());
        shutdown();
        return false;
    }

    EGLint nativeVisualId = 0;
    eglGetConfigAttrib(display_, config_, EGL_NATIVE_VISUAL_ID, &nativeVisualId);
    ANativeWindow_setBuffersGeometry(window_, 0, 0, nativeVisualId);

    surface_ = eglCreateWindowSurface(display_, config_, window_, nullptr);
    if (surface_ == EGL_NO_SURFACE) {
        SOTC_LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        shutdown();
        return false;
    }
    if (eglMakeCurrent(display_, surface_, surface_, context_) != EGL_TRUE) {
        SOTC_LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        shutdown();
        return false;
    }
    eglSwapInterval(display_, 1);

    SOTC_LOGI(
        "EGL surface ready: %dx%d, GL %s",
        surfaceWidth(),
        surfaceHeight(),
        reinterpret_cast<const char*>(glGetString(GL_VERSION))
    );
    return true;
}

void EglRenderer::shutdown() {
    if (display_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface_ != EGL_NO_SURFACE) {
            eglDestroySurface(display_, surface_);
        }
        if (context_ != EGL_NO_CONTEXT) {
            eglDestroyContext(display_, context_);
        }
        eglTerminate(display_);
    }
    display_ = EGL_NO_DISPLAY;
    surface_ = EGL_NO_SURFACE;
    context_ = EGL_NO_CONTEXT;
    config_ = nullptr;
    window_ = nullptr;
}

bool EglRenderer::makeCurrent() {
    if (!valid()) return false;
    return eglMakeCurrent(display_, surface_, surface_, context_) == EGL_TRUE;
}

void EglRenderer::swapBuffers() {
    if (valid()) {
        eglSwapBuffers(display_, surface_);
    }
}

int EglRenderer::surfaceWidth() const {
    EGLint width = 0;
    if (surface_ != EGL_NO_SURFACE) {
        eglQuerySurface(display_, surface_, EGL_WIDTH, &width);
    }
    return width;
}

int EglRenderer::surfaceHeight() const {
    EGLint height = 0;
    if (surface_ != EGL_NO_SURFACE) {
        eglQuerySurface(display_, surface_, EGL_HEIGHT, &height);
    }
    return height;
}
