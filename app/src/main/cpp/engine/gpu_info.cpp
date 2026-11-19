#include "gpu_info.h"

#include <EGL/egl.h>
#include <GLES2/gl2.h>

#include <cstdio>
#include <mutex>
#include <string>

#include "egl_display_lock.h"
#include "log.h"

// Same documented value the renderer uses; defined here so the query compiles
// against NDK sysroots that only expose it through EGL/eglext.h.
#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

// Limits and formats that are core in ES 3 but absent from the ES 2 headers
// this translation unit compiles against.
#ifndef GL_MAX_DRAW_BUFFERS
#define GL_MAX_DRAW_BUFFERS 0x8824
#endif
#ifndef GL_MAX_SAMPLES
#define GL_MAX_SAMPLES 0x8D57
#endif
#ifndef GL_DEPTH_ATTACHMENT
#define GL_DEPTH_ATTACHMENT 0x8D00
#endif
#ifndef GL_DEPTH_STENCIL_ATTACHMENT
#define GL_DEPTH_STENCIL_ATTACHMENT 0x821A
#endif
#ifndef GL_DEPTH24_STENCIL8
#define GL_DEPTH24_STENCIL8 0x88F0
#endif
#ifndef GL_DEPTH_COMPONENT16
#define GL_DEPTH_COMPONENT16 0x81A5
#endif
#ifndef GL_MAX_TEXTURE_IMAGE_UNITS
#define GL_MAX_TEXTURE_IMAGE_UNITS 0x8872
#endif
#ifndef GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS
#define GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS 0x8B4C
#endif
#ifndef GL_FRAMEBUFFER
#define GL_FRAMEBUFFER 0x8D40
#endif
#ifndef GL_RENDERBUFFER
#define GL_RENDERBUFFER 0x8D41
#endif
#ifndef GL_COLOR_ATTACHMENT0
#define GL_COLOR_ATTACHMENT0 0x8CE0
#endif
#ifndef GL_FRAMEBUFFER_COMPLETE
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#endif
#ifndef GL_APIENTRY
#define GL_APIENTRY
#endif

namespace {

constexpr const char* kBackendEs3 = "OpenGL ES 3.0";
constexpr const char* kBackendEs2 = "OpenGL ES 2.0";

std::string eglErrorText(const char* operation, EGLint error) {
    char buffer[96];
    std::snprintf(buffer, sizeof(buffer), "%s failed (EGL error 0x%04x)", operation, error);
    return buffer;
}

std::string eglString(EGLDisplay display, EGLint name) {
    const char* value = eglQueryString(display, name);
    return value != nullptr ? std::string(value) : std::string();
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

bool advertises(const std::string& list, const char* token) {
    return list.find(token) != std::string::npos;
}

int integerLimit(GLenum name) {
    GLint value = 0;
    glGetIntegerv(name, &value);
    return static_cast<int>(value);
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

// The framebuffer entry points are core in ES 3 and extensions in ES 2. They
// are resolved at run time so this file links against GLESv2 only and never
// calls an ES 3 function in an ES 2 context.
using GenFramebuffersFn = void(GL_APIENTRY*)(GLsizei, GLuint*);
using BindFramebufferFn = void(GL_APIENTRY*)(GLenum, GLuint);
using FramebufferTexture2DFn = void(GL_APIENTRY*)(GLenum, GLenum, GLenum, GLuint, GLint);
using FramebufferRenderbufferFn = void(GL_APIENTRY*)(GLenum, GLenum, GLenum, GLuint);
using CheckFramebufferStatusFn = GLenum(GL_APIENTRY*)(GLenum);
using DeleteFramebuffersFn = void(GL_APIENTRY*)(GLsizei, const GLuint*);
using GenRenderbuffersFn = void(GL_APIENTRY*)(GLsizei, GLuint*);
using BindRenderbufferFn = void(GL_APIENTRY*)(GLenum, GLuint);
using RenderbufferStorageFn = void(GL_APIENTRY*)(GLenum, GLenum, GLsizei, GLsizei);
using DeleteRenderbuffersFn = void(GL_APIENTRY*)(GLsizei, const GLuint*);

template <typename Function>
Function loadGlFunction(const char* coreName, const char* extensionName) {
    void* address = reinterpret_cast<void*>(eglGetProcAddress(coreName));
    if (address == nullptr && extensionName != nullptr) {
        address = reinterpret_cast<void*>(eglGetProcAddress(extensionName));
    }
    return reinterpret_cast<Function>(address);
}

struct FramebufferFunctions {
    GenFramebuffersFn genFramebuffers = nullptr;
    BindFramebufferFn bindFramebuffer = nullptr;
    FramebufferTexture2DFn framebufferTexture2D = nullptr;
    FramebufferRenderbufferFn framebufferRenderbuffer = nullptr;
    CheckFramebufferStatusFn checkFramebufferStatus = nullptr;
    DeleteFramebuffersFn deleteFramebuffers = nullptr;
    GenRenderbuffersFn genRenderbuffers = nullptr;
    BindRenderbufferFn bindRenderbuffer = nullptr;
    RenderbufferStorageFn renderbufferStorage = nullptr;
    DeleteRenderbuffersFn deleteRenderbuffers = nullptr;

    bool complete() const {
        return genFramebuffers != nullptr && bindFramebuffer != nullptr &&
               framebufferTexture2D != nullptr && framebufferRenderbuffer != nullptr &&
               checkFramebufferStatus != nullptr && deleteFramebuffers != nullptr &&
               genRenderbuffers != nullptr && bindRenderbuffer != nullptr &&
               renderbufferStorage != nullptr && deleteRenderbuffers != nullptr;
    }

    static FramebufferFunctions load() {
        FramebufferFunctions functions;
        functions.genFramebuffers = loadGlFunction<GenFramebuffersFn>("glGenFramebuffers", "glGenFramebuffersOES");
        functions.bindFramebuffer = loadGlFunction<BindFramebufferFn>("glBindFramebuffer", "glBindFramebufferOES");
        functions.framebufferTexture2D =
            loadGlFunction<FramebufferTexture2DFn>("glFramebufferTexture2D", "glFramebufferTexture2DOES");
        functions.framebufferRenderbuffer =
            loadGlFunction<FramebufferRenderbufferFn>("glFramebufferRenderbuffer", "glFramebufferRenderbufferOES");
        functions.checkFramebufferStatus =
            loadGlFunction<CheckFramebufferStatusFn>("glCheckFramebufferStatus", "glCheckFramebufferStatusOES");
        functions.deleteFramebuffers =
            loadGlFunction<DeleteFramebuffersFn>("glDeleteFramebuffers", "glDeleteFramebuffersOES");
        functions.genRenderbuffers =
            loadGlFunction<GenRenderbuffersFn>("glGenRenderbuffers", "glGenRenderbuffersOES");
        functions.bindRenderbuffer =
            loadGlFunction<BindRenderbufferFn>("glBindRenderbuffer", "glBindRenderbufferOES");
        functions.renderbufferStorage =
            loadGlFunction<RenderbufferStorageFn>("glRenderbufferStorage", "glRenderbufferStorageOES");
        functions.deleteRenderbuffers =
            loadGlFunction<DeleteRenderbuffersFn>("glDeleteRenderbuffers", "glDeleteRenderbuffersOES");
        return functions;
    }
};

bool testShaderCompile() {
    const char* vertexSource = "attribute vec2 p; void main(){ gl_Position = vec4(p, 0.0, 1.0); }";
    const char* fragmentSource = "precision mediump float; void main(){ gl_FragColor = vec4(1.0); }";
    GLuint vertex = glCreateShader(GL_VERTEX_SHADER);
    GLuint fragment = glCreateShader(GL_FRAGMENT_SHADER);
    if (vertex == 0 || fragment == 0) return false;
    glShaderSource(vertex, 1, &vertexSource, nullptr);
    glCompileShader(vertex);
    glShaderSource(fragment, 1, &fragmentSource, nullptr);
    glCompileShader(fragment);
    GLint vertexOk = 0;
    GLint fragmentOk = 0;
    glGetShaderiv(vertex, GL_COMPILE_STATUS, &vertexOk);
    glGetShaderiv(fragment, GL_COMPILE_STATUS, &fragmentOk);
    GLint programOk = 0;
    if (vertexOk && fragmentOk) {
        GLuint program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glGetProgramiv(program, GL_LINK_STATUS, &programOk);
        glDeleteProgram(program);
    }
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    while (glGetError() != GL_NO_ERROR) {
    }
    return vertexOk != 0 && fragmentOk != 0 && programOk != 0;
}

// Creates a 4x4 RGBA render target, clears it to a known colour and reads a
// pixel back. A format passes only when the framebuffer is complete AND the
// readback returns the colour, so a driver that merely accepts texImage2D
// cannot be reported as supporting render-to-texture.
bool testColorTarget(GLenum internalFormat, GLenum format, const FramebufferFunctions& functions) {
    GLuint texture = 0;
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, 4, 4, 0, format, GL_UNSIGNED_BYTE, nullptr);
    bool passed = glGetError() == GL_NO_ERROR;

    GLuint framebuffer = 0;
    if (passed) {
        functions.genFramebuffers(1, &framebuffer);
        functions.bindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        functions.framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        passed = functions.checkFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    }
    if (passed) {
        glClearColor(0.25f, 0.5f, 0.75f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        unsigned char pixel[4] = {};
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        passed = glGetError() == GL_NO_ERROR && pixel[0] >= 32 && pixel[1] >= 96 && pixel[3] >= 224;
    }
    if (framebuffer != 0) {
        functions.bindFramebuffer(GL_FRAMEBUFFER, 0);
        functions.deleteFramebuffers(1, &framebuffer);
    }
    glDeleteTextures(1, &texture);
    while (glGetError() != GL_NO_ERROR) {
    }
    return passed;
}

bool testDepthTarget(GLenum attachment, const FramebufferFunctions& functions) {
    GLuint framebuffer = 0;
    GLuint renderbuffer = 0;
    functions.genFramebuffers(1, &framebuffer);
    functions.bindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    functions.genRenderbuffers(1, &renderbuffer);
    functions.bindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
    functions.renderbufferStorage(GL_RENDERBUFFER, attachment == GL_DEPTH_ATTACHMENT
                                                       ? GL_DEPTH_COMPONENT16
                                                       : GL_DEPTH24_STENCIL8,
                                   4, 4);
    functions.framebufferRenderbuffer(GL_FRAMEBUFFER, attachment, GL_RENDERBUFFER, renderbuffer);
    const bool passed = functions.checkFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    functions.bindRenderbuffer(GL_RENDERBUFFER, 0);
    functions.bindFramebuffer(GL_FRAMEBUFFER, 0);
    functions.deleteRenderbuffers(1, &renderbuffer);
    functions.deleteFramebuffers(1, &framebuffer);
    while (glGetError() != GL_NO_ERROR) {
    }
    return passed;
}

void runSelfTests(GpuCapabilities& capabilities, bool es3) {
    capabilities.selfTest = "not-run";
    capabilities.shaderCompilePassed = testShaderCompile();

    const FramebufferFunctions functions = FramebufferFunctions::load();
    if (!functions.complete()) {
        capabilities.selfTest = "framebuffer-api-unavailable";
        return;
    }

    capabilities.renderTargetRgba8Passed = testColorTarget(
        es3 ? 0x8058 /* GL_RGBA8 */ : GL_RGBA, GL_RGBA, functions);
    capabilities.renderToTexturePassed = capabilities.renderTargetRgba8Passed;
    if (!es3) {
        capabilities.renderTargetRgb565Passed = testColorTarget(GL_RGB, GL_RGB, functions);
    } else {
        capabilities.renderTargetRgb565Passed = testColorTarget(0x8D62 /* GL_RGB565 */, GL_RGB, functions);
    }
    capabilities.renderTargetDepth16Passed = testDepthTarget(GL_DEPTH_ATTACHMENT, functions);
    capabilities.renderTargetDepth24Stencil8Passed = testDepthTarget(GL_DEPTH_STENCIL_ATTACHMENT, functions);

    const auto flag = [](bool value) { return value ? '1' : '0'; };
    char summary[96];
    std::snprintf(summary, sizeof(summary), "shader=%c rgba8=%c rgb565=%c depth16=%c depth24s8=%c rtt=%c",
                  flag(capabilities.shaderCompilePassed), flag(capabilities.renderTargetRgba8Passed),
                  flag(capabilities.renderTargetRgb565Passed), flag(capabilities.renderTargetDepth16Passed),
                  flag(capabilities.renderTargetDepth24Stencil8Passed), flag(capabilities.renderToTexturePassed));
    capabilities.selfTest = summary;
}

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

    capabilities.eglVendor = singleLine(eglString(display, EGL_VENDOR));
    capabilities.eglVersion = singleLine(eglString(display, EGL_VERSION));
    capabilities.eglExtensions = singleLine(eglString(display, EGL_EXTENSIONS));

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
        capabilities.glExtensions = singleLine(glString(GL_EXTENSIONS));
        capabilities.maxTextureSize = integerLimit(GL_MAX_TEXTURE_SIZE);
        capabilities.maxRenderbufferSize = integerLimit(GL_MAX_RENDERBUFFER_SIZE);
        capabilities.maxVertexAttribs = integerLimit(GL_MAX_VERTEX_ATTRIBS);
        capabilities.maxTextureImageUnits = integerLimit(GL_MAX_TEXTURE_IMAGE_UNITS);
        capabilities.maxVertexTextureImageUnits = integerLimit(GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS);
        capabilities.maxDrawBuffers = integerLimit(GL_MAX_DRAW_BUFFERS);
        if (capabilities.maxDrawBuffers <= 0) capabilities.maxDrawBuffers = 1;
        capabilities.maxSamples = integerLimit(GL_MAX_SAMPLES);
        capabilities.extensionCount = extensionCount();
        capabilities.framebufferObjectAdvertised = attempt.clientVersion >= 3 ||
            advertises(capabilities.glExtensions, "framebuffer_object");
        capabilities.timerQueryAdvertised = advertises(capabilities.glExtensions, "disjoint_timer_query") ||
            advertises(capabilities.glExtensions, "timer_query");
        capabilities.fenceSyncAdvertised = attempt.clientVersion >= 3 ||
            advertises(capabilities.glExtensions, "EGL_ANDROID_native_fence_sync") ||
            advertises(capabilities.eglExtensions, "native_fence_sync");
        runSelfTests(capabilities, attempt.clientVersion >= 3);

        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(display, surface);
        eglDestroyContext(display, context);
        break;
    }

    if (!capabilities.available) {
        capabilities.error = lastError;
    }
    // The display is deliberately left initialized: eglTerminate would also
    // tear down the render thread's context. EglRenderer owns teardown of its
    // own context and surface; see the GPU display lifetime note in
    // docs/agent/GPU_COMPATIBILITY_MATRIX.md.
    SOTC_LOGI(
        "GPU query: %s (%s / %s), self-test %s",
        capabilities.available ? capabilities.backend.c_str() : "unavailable",
        capabilities.vendor.c_str(),
        capabilities.renderer.c_str(),
        capabilities.selfTest.c_str()
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
    line("eglVendor", singleLine(capabilities.eglVendor));
    line("eglVersion", singleLine(capabilities.eglVersion));
    line("eglExtensions", singleLine(capabilities.eglExtensions));
    line("vendor", singleLine(capabilities.vendor));
    line("renderer", singleLine(capabilities.renderer));
    line("version", singleLine(capabilities.version));
    line("glsl", singleLine(capabilities.shadingLanguage));
    line("glExtensions", singleLine(capabilities.glExtensions));
    line("maxTextureSize", std::to_string(capabilities.maxTextureSize));
    line("maxRenderbufferSize", std::to_string(capabilities.maxRenderbufferSize));
    line("maxVertexAttribs", std::to_string(capabilities.maxVertexAttribs));
    line("maxTextureImageUnits", std::to_string(capabilities.maxTextureImageUnits));
    line("maxVertexTextureImageUnits", std::to_string(capabilities.maxVertexTextureImageUnits));
    line("maxDrawBuffers", std::to_string(capabilities.maxDrawBuffers));
    line("maxSamples", std::to_string(capabilities.maxSamples));
    line("extensions", std::to_string(capabilities.extensionCount));
    line("framebufferObjectAdvertised", capabilities.framebufferObjectAdvertised ? "1" : "0");
    line("timerQueryAdvertised", capabilities.timerQueryAdvertised ? "1" : "0");
    line("fenceSyncAdvertised", capabilities.fenceSyncAdvertised ? "1" : "0");
    line("selfTest", singleLine(capabilities.selfTest));
    line("error", singleLine(capabilities.error));
    return report;
}
