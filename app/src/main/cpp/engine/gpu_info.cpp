#include "gpu_info.h"

#include <EGL/egl.h>
#include <GLES2/gl2.h>

#include <cstdio>
#include <mutex>

#include "egl_display_lock.h"
#include "log.h"

// Same documented value the renderer uses; defined here so the query compiles
// against NDK sysroots that only expose it through EGL/eglext.h.
#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

namespace {

constexpr const char* kBackendEs3 = "OpenGL ES 3.0";
constexpr const char* kBackendEs2 = "OpenGL ES 2.0";

std::string eglErrorText(const char* operation, EGLint error) {
    char buffer[96];
    std::snprintf(buffer, sizeof(buffer), "%s failed (EGL error 0x%04x)", operation, error);
    return buffer;
}

std::string glString(GLenum name) {
    const GLubyte* value = glGetString(name);
    return value != nullptr ? std::string(reinterpret_cast<const char*>(value)) : std::string();
}

// The report is line based, so a driver string with a line break must not be
// able to split a field into two.
std::string singleLine(std::string value) {
    for (char& character : value) {
        if (character == '\n' || character == '\r') {
            character = ' ';
        }
    }
    return value;
}

int extensionCount() {
    const GLubyte* extensions = glGetString(GL_EXTENSIONS);
    if (extensions == nullptr) return 0;
    int count = 0;
    bool inToken = false;
    for (const GLubyte* cursor = extensions; *cursor != '\0'; ++cursor) {
        const bool separator = *cursor == ' ';
        if (!separator && !inToken) {
            ++count;
        }
        inToken = !separator;
    }
    return count;
}

// Pbuffer configurations vary by driver in the same way window configurations
// do, so the query asks for progressively simpler attribute sets.
bool chooseConfig(EGLDisplay display, EGLint renderableType, EGLConfig* config) {
    const EGLint rgba8888[] = {
        EGL_RENDERABLE_TYPE, renderableType,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    const EGLint minimal[] = {
        EGL_RENDERABLE_TYPE, renderableType,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_NONE
    };
    const EGLint* candidates[] = {rgba8888, minimal};
    for (const EGLint* attributes : candidates) {
        EGLConfig candidate = nullptr;
        EGLint count = 0;
        if (eglChooseConfig(display, attributes, &candidate, 1, &count) != EGL_TRUE) {
            return false;
        }
        if (count > 0 && candidate != nullptr) {
            *config = candidate;
            return true;
        }
    }
    return false;
}

struct Attempt {
    EGLint renderableType;
    EGLint clientVersion;
    const char* label;
};

}  // namespace

GpuCapabilities queryGpuCapabilities(bool forceEs2) {
    GpuCapabilities capabilities;
    std::lock_guard<std::mutex> lock(sotcEglDisplayMutex());

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        capabilities.error = "eglGetDisplay failed";
        return capabilities;
    }
    EGLint major = 0;
    EGLint minor = 0;
    if (eglInitialize(display, &major, &minor) != EGL_TRUE) {
        capabilities.error = eglErrorText("eglInitialize", eglGetError());
        return capabilities;
    }
    if (eglBindAPI(EGL_OPENGL_ES_API) != EGL_TRUE) {
        capabilities.error = eglErrorText("eglBindAPI", eglGetError());
        return capabilities;
    }

    const Attempt attempts[] = {
        {EGL_OPENGL_ES3_BIT_KHR, 3, kBackendEs3},
        {EGL_OPENGL_ES2_BIT, 2, kBackendEs2}
    };
    constexpr int kAttemptCount = static_cast<int>(sizeof(attempts) / sizeof(attempts[0]));
    const int firstAttempt = forceEs2 ? 1 : 0;
    std::string lastError = "No OpenGL ES context could be created";

    for (int index = firstAttempt; index < kAttemptCount; ++index) {
        const Attempt& attempt = attempts[index];
        EGLConfig config = nullptr;
        if (!chooseConfig(display, attempt.renderableType, &config)) {
            lastError = std::string("No EGL pbuffer config for ") + attempt.label;
            continue;
        }

        const EGLint surfaceAttributes[] = {
            EGL_WIDTH, 16,
            EGL_HEIGHT, 16,
            EGL_NONE
        };
        EGLSurface surface = eglCreatePbufferSurface(display, config, surfaceAttributes);
        if (surface == EGL_NO_SURFACE) {
            lastError = eglErrorText("eglCreatePbufferSurface", eglGetError());
            continue;
        }

        const EGLint contextAttributes[] = {
            EGL_CONTEXT_CLIENT_VERSION, attempt.clientVersion,
            EGL_NONE
        };
        EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttributes);
        if (context == EGL_NO_CONTEXT) {
            lastError = eglErrorText("eglCreateContext", eglGetError());
            eglDestroySurface(display, surface);
            continue;
        }
        if (eglMakeCurrent(display, surface, surface, context) != EGL_TRUE) {
            lastError = eglErrorText("eglMakeCurrent", eglGetError());
            eglDestroyContext(display, context);
            eglDestroySurface(display, surface);
            continue;
        }

        capabilities.available = true;
        capabilities.backend = attempt.label;
        capabilities.vendor = singleLine(glString(GL_VENDOR));
        capabilities.renderer = singleLine(glString(GL_RENDERER));
        capabilities.version = singleLine(glString(GL_VERSION));
        capabilities.shadingLanguage = singleLine(glString(GL_SHADING_LANGUAGE_VERSION));

        GLint value = 0;
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, &value);
        capabilities.maxTextureSize = value;
        value = 0;
        glGetIntegerv(GL_MAX_VERTEX_ATTRIBS, &value);
        capabilities.maxVertexAttribs = value;
        capabilities.extensionCount = extensionCount();

        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(display, surface);
        eglDestroyContext(display, context);
        break;
    }

    if (!capabilities.available) {
        capabilities.error = lastError;
    }
    // The display is deliberately left initialized: eglTerminate would also
    // tear down the render thread's context, and the process holds the display
    // for its whole lifetime anyway.
    SOTC_LOGI(
        "GPU query: %s (%s / %s)",
        capabilities.available ? capabilities.backend.c_str() : "unavailable",
        capabilities.vendor.c_str(),
        capabilities.renderer.c_str()
    );
    return capabilities;
}

std::string gpuCapabilitiesReport(const GpuCapabilities& capabilities) {
    std::string report;
    const auto line = [&report](const char* key, const std::string& value) {
        report += key;
        report += '=';
        report += value;
        report += '\n';
    };
    line("available", capabilities.available ? "1" : "0");
    line("backend", singleLine(capabilities.backend));
    line("vendor", singleLine(capabilities.vendor));
    line("renderer", singleLine(capabilities.renderer));
    line("version", singleLine(capabilities.version));
    line("glsl", singleLine(capabilities.shadingLanguage));
    line("maxTextureSize", std::to_string(capabilities.maxTextureSize));
    line("maxVertexAttribs", std::to_string(capabilities.maxVertexAttribs));
    line("extensions", std::to_string(capabilities.extensionCount));
    line("error", singleLine(capabilities.error));
    return report;
}
