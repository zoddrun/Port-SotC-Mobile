#include "egl_renderer.h"
#include "log.h"

// EGL_OPENGL_ES3_BIT lives in the EGL 1.4 core header as EGL_OPENGL_ES3_BIT_KHR
// only when EGL/eglext.h is included. Define the documented value as a
// fallback so the renderer builds against every NDK sysroot revision.
#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

namespace {

// Window configurations differ between GPU drivers. Some devices expose no
// 8-bit alpha window config, so a single strict attribute list can yield zero
// configs on otherwise capable hardware. Try progressively simpler requests.
bool chooseEs3Config(EGLDisplay display, EGLConfig* config) {
    const EGLint rgba8888[] = {
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
    const EGLint rgb888[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 0,
        EGL_DEPTH_SIZE, 0,
        EGL_STENCIL_SIZE, 0,
        EGL_NONE
    };
    const EGLint minimal[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_NONE
    };

    const EGLint* candidates[] = {rgba8888, rgb888, minimal};
    const char* labels[] = {"RGBA8888", "RGB888", "minimal"};
    constexpr int kCandidateCount =
        static_cast<int>(sizeof(candidates) / sizeof(candidates[0]));
    for (int index = 0; index < kCandidateCount; ++index) {
        EGLConfig candidate = nullptr;
        EGLint count = 0;
        const EGLBoolean chosen =
            eglChooseConfig(display, candidates[index], &candidate, 1, &count);
        if (chosen != EGL_TRUE) {
            SOTC_LOGE("eglChooseConfig failed: 0x%x", eglGetError());
            return false;
        }
        if (count > 0 && candidate != nullptr) {
            *config = candidate;
            SOTC_LOGI("EGL config selected (%s)", labels[index]);
            return true;
        }
    }
    return false;
}

void logConfigAttributes(EGLDisplay display, EGLConfig config) {
    EGLint red = 0;
    EGLint green = 0;
    EGLint blue = 0;
    EGLint alpha = 0;
    EGLint depth = 0;
    EGLint stencil = 0;
    EGLint samples = 0;
    eglGetConfigAttrib(display, config, EGL_RED_SIZE, &red);
    eglGetConfigAttrib(display, config, EGL_GREEN_SIZE, &green);
    eglGetConfigAttrib(display, config, EGL_BLUE_SIZE, &blue);
    eglGetConfigAttrib(display, config, EGL_ALPHA_SIZE, &alpha);
    eglGetConfigAttrib(display, config, EGL_DEPTH_SIZE, &depth);
    eglGetConfigAttrib(display, config, EGL_STENCIL_SIZE, &stencil);
    eglGetConfigAttrib(display, config, EGL_SAMPLES, &samples);
    SOTC_LOGI(
        "EGL config: R%d G%d B%d A%d depth%d stencil%d samples%d",
        red,
        green,
        blue,
        alpha,
        depth,
        stencil,
        samples
    );
}

}  // namespace

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
    const char* clientApis = eglQueryString(display_, EGL_CLIENT_APIS);
    SOTC_LOGI("EGL client APIs: %s", clientApis != nullptr ? clientApis : "(unavailable)");

    if (eglBindAPI(EGL_OPENGL_ES_API) != EGL_TRUE) {
        SOTC_LOGE("eglBindAPI(EGL_OPENGL_ES_API) failed: 0x%x", eglGetError());
        shutdown();
        return false;
    }

    if (!chooseEs3Config(display_, &config_)) {
        SOTC_LOGE(
            "No EGL configuration exposes OpenGL ES 3; a GLES 3.0 capable driver is required"
        );
        shutdown();
        return false;
    }
    logConfigAttributes(display_, config_);

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
    if (eglGetConfigAttrib(display_, config_, EGL_NATIVE_VISUAL_ID, &nativeVisualId) == EGL_TRUE &&
        nativeVisualId != 0) {
        ANativeWindow_setBuffersGeometry(window_, 0, 0, nativeVisualId);
    }

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
