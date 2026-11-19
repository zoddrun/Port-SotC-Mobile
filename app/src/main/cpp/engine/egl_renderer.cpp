#include "egl_renderer.h"

#include <mutex>

#include "egl_display_lock.h"
#include "log.h"

// EGL_OPENGL_ES3_BIT lives in the EGL 1.4 core header as EGL_OPENGL_ES3_BIT_KHR
// only when EGL/eglext.h is included. Define the documented value as a
// fallback so the renderer builds against every NDK sysroot revision.
#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

namespace {

constexpr const char* kBackendEs3 = "OpenGL ES 3.0";
constexpr const char* kBackendEs2 = "OpenGL ES 2.0";

// Window configurations differ between GPU drivers. Some devices expose no
// 8-bit alpha window config, so a single strict attribute list can yield zero
// configs on otherwise capable hardware. Try progressively simpler requests.
bool chooseConfig(EGLDisplay display, EGLint renderableType, EGLConfig* config) {
    const EGLint rgba8888[] = {
        EGL_RENDERABLE_TYPE, renderableType,
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
        EGL_RENDERABLE_TYPE, renderableType,
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
        EGL_RENDERABLE_TYPE, renderableType,
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

struct Attempt {
    EGLint renderableType;
    EGLint clientVersion;
    const char* label;
};

}  // namespace

EglRenderer::~EglRenderer() {
    shutdown();
}

bool EglRenderer::initialize(ANativeWindow* window) {
    // The display is shared with the capability query; see egl_display_lock.h.
    std::lock_guard<std::mutex> lock(sotcEglDisplayMutex());
    shutdownLocked();
    window_ = window;
    backendKind_.store(kBackendKindNone);

    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) {
        SOTC_LOGE("eglGetDisplay failed");
        return false;
    }
    EGLint major = 0;
    EGLint minor = 0;
    if (eglInitialize(display_, &major, &minor) != EGL_TRUE) {
        SOTC_LOGE("eglInitialize failed: 0x%x", eglGetError());
        shutdownLocked();
        return false;
    }
    SOTC_LOGI("EGL %d.%d initialized", major, minor);
    const char* clientApis = eglQueryString(display_, EGL_CLIENT_APIS);
    SOTC_LOGI("EGL client APIs: %s", clientApis != nullptr ? clientApis : "(unavailable)");

    if (eglBindAPI(EGL_OPENGL_ES_API) != EGL_TRUE) {
        SOTC_LOGE("eglBindAPI(EGL_OPENGL_ES_API) failed: 0x%x", eglGetError());
        shutdownLocked();
        return false;
    }

    const Attempt attempts[] = {
        {EGL_OPENGL_ES3_BIT_KHR, 3, kBackendEs3},
        {EGL_OPENGL_ES2_BIT, 2, kBackendEs2}
    };
    constexpr int kAttemptCount = static_cast<int>(sizeof(attempts) / sizeof(attempts[0]));
    const int firstAttempt = preferredClientVersion_.load() == 2 ? 1 : 0;
    std::string failure = "no OpenGL ES context";

    for (int index = firstAttempt; index < kAttemptCount; ++index) {
        const Attempt& attempt = attempts[index];
        EGLConfig config = nullptr;
        if (!chooseConfig(display_, attempt.renderableType, &config)) {
            failure = std::string("no EGL config for ") + attempt.label;
            SOTC_LOGW("EGL: %s; trying the next API", failure.c_str());
            continue;
        }
        logConfigAttributes(display_, config);

        const EGLint contextAttributes[] = {
            EGL_CONTEXT_CLIENT_VERSION, attempt.clientVersion,
            EGL_NONE
        };
        EGLContext context =
            eglCreateContext(display_, config, EGL_NO_CONTEXT, contextAttributes);
        if (context == EGL_NO_CONTEXT) {
            failure = std::string("eglCreateContext(") + attempt.label + ") failed";
            SOTC_LOGW("EGL: %s: 0x%x; trying the next API", failure.c_str(), eglGetError());
            continue;
        }
        config_ = config;
        context_ = context;

        EGLint nativeVisualId = 0;
        if (eglGetConfigAttrib(display_, config_, EGL_NATIVE_VISUAL_ID, &nativeVisualId) == EGL_TRUE &&
            nativeVisualId != 0) {
            ANativeWindow_setBuffersGeometry(window_, 0, 0, nativeVisualId);
        }

        surface_ = eglCreateWindowSurface(display_, config_, window_, nullptr);
        if (surface_ == EGL_NO_SURFACE) {
            failure = std::string("eglCreateWindowSurface(") + attempt.label + ") failed";
            SOTC_LOGW("EGL: %s: 0x%x; trying the next API", failure.c_str(), eglGetError());
            eglDestroyContext(display_, context_);
            context_ = EGL_NO_CONTEXT;
            config_ = nullptr;
            continue;
        }
        if (eglMakeCurrent(display_, surface_, surface_, context_) != EGL_TRUE) {
            failure = std::string("eglMakeCurrent(") + attempt.label + ") failed";
            SOTC_LOGW("EGL: %s: 0x%x; trying the next API", failure.c_str(), eglGetError());
            eglDestroySurface(display_, surface_);
            surface_ = EGL_NO_SURFACE;
            eglDestroyContext(display_, context_);
            context_ = EGL_NO_CONTEXT;
            config_ = nullptr;
            continue;
        }

        // A hint only: some drivers ignore it and none are required to honour it.
        eglSwapInterval(display_, 1);
        backendKind_.store(
            attempt.clientVersion == 3 ? kBackendKindEs3 : kBackendKindEs2
        );
        SOTC_LOGI(
            "EGL surface ready: %dx%d, %s, driver: %s",
            surfaceWidth(),
            surfaceHeight(),
            backendName().c_str(),
            reinterpret_cast<const char*>(glGetString(GL_VERSION))
        );
        return true;
    }

    SOTC_LOGE("Unable to create an OpenGL ES context: %s", failure.c_str());
    shutdownLocked();
    return false;
}

void EglRenderer::shutdown() {
    std::lock_guard<std::mutex> lock(sotcEglDisplayMutex());
    shutdownLocked();
}

void EglRenderer::shutdownLocked() {
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
    backendKind_.store(kBackendKindNone);
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
