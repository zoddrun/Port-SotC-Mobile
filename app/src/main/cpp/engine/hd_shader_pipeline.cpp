#include "hd_shader_pipeline.h"

#include <EGL/egl.h>
#include <GLES2/gl2ext.h>

#include <android/log.h>

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <sys/stat.h>
#include <utility>
#include <vector>

#include "gl_util.h"
#include "hd_texture_cache.h"
#include "log.h"

namespace {

constexpr GLenum kTextureMaxAnisotropyExt = 0x84FE;
constexpr GLenum kMaxTextureMaxAnisotropyExt = 0x84FF;
constexpr GLenum kNumProgramBinaryFormats = 0x8B8D;
constexpr GLenum kProgramBinaryLength = 0x8741;
constexpr char kBinaryMagic[] = "SOTCBIN1";

using GetProgramBinaryFn = void(GL_APIENTRY*)(
    GLuint, GLsizei, GLsizei*, GLenum*, void*);
using ProgramBinaryFn = void(GL_APIENTRY*)(GLuint, GLenum, const void*, GLsizei);
using InvalidateFramebufferFn = void(GL_APIENTRY*)(GLenum, GLsizei, const GLenum*);
using DiscardFramebufferFn = void(GL_APIENTRY*)(GLenum, GLsizei, const GLenum*);

// Two attributes, matching both the ES 1.00 and ES 3.00 asset vertex shaders.
constexpr GLfloat kFullscreenTriangle[] = {
    -1.0f, -1.0f, 0.0f, 0.0f,
     3.0f, -1.0f, 2.0f, 0.0f,
    -1.0f,  3.0f, 0.0f, 2.0f
};

std::string hex(uint64_t value) {
    static const char* digits = "0123456789abcdef";
    std::string result(16, '0');
    for (int i = 15; i >= 0; --i) {
        result[static_cast<size_t>(i)] = digits[value & 0x0F];
        value >>= 4;
    }
    return result;
}

bool isPowerOfTwo(int value) {
    return value > 0 && (value & (value - 1)) == 0;
}

bool hasExtension(const char* name) {
    const char* extensions = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    if (extensions == nullptr || name == nullptr || *name == '\0') return false;
    const std::string needle(name);
    const std::string list(extensions);
    size_t start = 0;
    while (start < list.size()) {
        const size_t end = list.find(' ', start);
        const size_t length = end == std::string::npos ? std::string::npos : end - start;
        if (list.compare(start, length, needle) == 0) return true;
        if (end == std::string::npos) break;
        start = end + 1;
    }
    return false;
}

bool readTextFile(const std::string& path, std::string* output) {
    std::ifstream input(path, std::ios::binary);
    if (!input) return false;
    std::ostringstream contents;
    contents << input.rdbuf();
    *output = contents.str();
    return !output->empty();
}

std::string joinPath(const std::string& directory, const std::string& name) {
    if (name.empty()) return {};
    if (!name.empty() && name.front() == '/') return name;
    return directory.empty() ? name : directory + "/" + name;
}

void ensureCacheDirectory(const std::string& path) {
    if (path.empty()) return;
    ::mkdir(path.c_str(), 0700);
}

float clampFloat(float value, float low, float high) {
    return std::max(low, std::min(high, value));
}

}  // namespace

void sotcLogHdShader(const std::string& message) {
    SOTC_LOGI("[hd-shader] %s", message.c_str());
    std::fprintf(stdout, "[hd-shader] %s\n", message.c_str());
    std::fflush(stdout);
}

HdShaderPipeline& HdShaderPipeline::instance() {
    static HdShaderPipeline pipeline;
    return pipeline;
}

void HdShaderPipeline::configure(const Config& config) {
    std::lock_guard<std::mutex> lock(mutex_);
    const bool changed = config_.packDirectory != config.packDirectory ||
        config_.version != config.version ||
        config_.preset != config.preset ||
        config_.es3Vertex != config.es3Vertex ||
        config_.es3Fragment != config.es3Fragment ||
        config_.es2Vertex != config.es2Vertex ||
        config_.es2Fragment != config.es2Fragment;
    config_ = config;
    if (changed) {
        // The next initialize() must rebuild; GL objects belong to the render
        // thread, so shutdown itself is deferred to that thread.
        ready_.store(false);
        initialized_.store(false);
        reconfigurePending_.store(true);
    }
    sotcLogHdShader(
        "configured installed=1 version=" + config_.version +
        " preset=" + config_.preset +
        " rcas=" + std::to_string(config_.rcasSharpness) +
        " bloom=" + std::to_string(config_.bloomIntensity) +
        " aniso=" + std::to_string(config_.anisotropyLevel) + "x"
    );
}

std::string HdShaderPipeline::cachePath(const std::string& key) const {
    if (config_.packDirectory.empty()) return {};
    return joinPath(config_.packDirectory, "cache/" + key + ".bin");
}

std::string HdShaderPipeline::computeBinaryKey(
    const std::string& vertex,
    const std::string& fragment,
    bool es3
) const {
    uint64_t hash = sotcHdFnv1a(vertex.data(), vertex.size());
    hash ^= sotcHdFnv1a(fragment.data(), fragment.size()) + 0x9E3779B97F4A7C15ULL +
        (hash << 6) + (hash >> 2);
    const char* renderer = reinterpret_cast<const char*>(glGetString(GL_RENDERER));
    const std::string rendererText = renderer == nullptr ? "unknown" : renderer;
    hash ^= sotcHdFnv1a(rendererText.data(), rendererText.size()) +
        0x9E3779B97F4A7C15ULL + (hash << 6) + (hash >> 2);
    hash ^= es3 ? 0x4558330100000000ULL : 0x4553320100000000ULL;
    return hex(hash);
}

bool HdShaderPipeline::tryLoadBinary(GLuint program, const std::string& key) {
    auto load = reinterpret_cast<GetProgramBinaryFn>(eglGetProcAddress("glGetProgramBinary"));
    auto loadOes = reinterpret_cast<GetProgramBinaryFn>(
        eglGetProcAddress("glGetProgramBinaryOES"));
    if (load == nullptr && loadOes == nullptr) return false;
    auto apply = reinterpret_cast<ProgramBinaryFn>(eglGetProcAddress("glProgramBinary"));
    auto applyOes = reinterpret_cast<ProgramBinaryFn>(eglGetProcAddress("glProgramBinaryOES"));
    if (apply == nullptr && applyOes == nullptr) return false;

    std::ifstream input(cachePath(key), std::ios::binary);
    if (!input) return false;
    char magic[8] = {};
    uint32_t format = 0;
    uint32_t length = 0;
    input.read(magic, sizeof(magic));
    input.read(reinterpret_cast<char*>(&format), sizeof(format));
    input.read(reinterpret_cast<char*>(&length), sizeof(length));
    if (!input || std::memcmp(magic, kBinaryMagic, sizeof(magic)) != 0 ||
        length == 0 || length > 16u * 1024u * 1024u) {
        return false;
    }
    std::vector<unsigned char> data(length);
    input.read(reinterpret_cast<char*>(data.data()), static_cast<std::streamsize>(length));
    if (!input) return false;

    if (apply != nullptr) {
        apply(program, static_cast<GLenum>(format), data.data(), static_cast<GLsizei>(length));
    } else {
        applyOes(program, static_cast<GLenum>(format), data.data(), static_cast<GLsizei>(length));
    }
    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE) {
        sotcLogHdShader("cached program binary was rejected; recompiling from source");
        return false;
    }
    (void)load;  // Presence of the getter confirms binary support on this context.
    return true;
}

bool HdShaderPipeline::storeBinary(GLuint program, const std::string& key) {
    GLint formats = 0;
    glGetIntegerv(kNumProgramBinaryFormats, &formats);
    if (formats <= 0) return false;
    auto get = reinterpret_cast<GetProgramBinaryFn>(eglGetProcAddress("glGetProgramBinary"));
    auto getOes = reinterpret_cast<GetProgramBinaryFn>(
        eglGetProcAddress("glGetProgramBinaryOES"));
    if (get == nullptr && getOes == nullptr) return false;

    GLint length = 0;
    glGetProgramiv(program, kProgramBinaryLength, &length);
    if (length <= 0 || length > 16 * 1024 * 1024) return false;
    std::vector<unsigned char> data(static_cast<size_t>(length));
    GLsizei written = 0;
    GLenum format = 0;
    if (get != nullptr) {
        get(program, static_cast<GLsizei>(data.size()), &written, &format, data.data());
    } else {
        getOes(program, static_cast<GLsizei>(data.size()), &written, &format, data.data());
    }
    if (written <= 0) return false;
    const std::string path = cachePath(key);
    if (path.empty()) return false;
    ensureCacheDirectory(joinPath(config_.packDirectory, "cache"));
    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    if (!output) return false;
    output.write(kBinaryMagic, sizeof(kBinaryMagic));
    output.write(reinterpret_cast<const char*>(&format), sizeof(format));
    const uint32_t size = static_cast<uint32_t>(written);
    output.write(reinterpret_cast<const char*>(&size), sizeof(size));
    output.write(reinterpret_cast<const char*>(data.data()), written);
    return output.good();
}

GLuint HdShaderPipeline::createProgramFromSources(
    const std::string& vertex,
    const std::string& fragment
) {
    return sotcLinkProgram(vertex.c_str(), fragment.c_str());
}

bool HdShaderPipeline::tryCreateProgram(bool es3) {
    const std::string vertexPath = joinPath(
        config_.packDirectory, es3 ? config_.es3Vertex : config_.es2Vertex);
    const std::string fragmentPath = joinPath(
        config_.packDirectory, es3 ? config_.es3Fragment : config_.es2Fragment);
    if (vertexPath.empty() || fragmentPath.empty()) return false;

    std::string vertex;
    std::string fragment;
    if (!readTextFile(vertexPath, &vertex) || !readTextFile(fragmentPath, &fragment)) {
        lastError_ = "unable to read " + vertexPath + " or " + fragmentPath;
        return false;
    }
    // Guard against an accidental ES 3 source being loaded on an ES 2 context.
    const bool sourceIsEs3 = vertex.find("#version 300 es") != std::string::npos;
    if (sourceIsEs3 != es3) {
        lastError_ = "shader version does not match the requested backend";
        return false;
    }

    const std::string key = computeBinaryKey(vertex, fragment, es3);
    GLuint program = glCreateProgram();
    if (program == 0) {
        lastError_ = "glCreateProgram failed";
        return false;
    }
    bool linked = tryLoadBinary(program, key);
    if (!linked) {
        glDeleteProgram(program);
        program = createProgramFromSources(vertex, fragment);
        linked = program != 0;
        if (linked) storeBinary(program, key);
    }
    if (!linked) {
        lastError_ = "unable to link the " + std::string(es3 ? "ES 3" : "ES 2") + " pack";
        return false;
    }

    cleanupProgram();
    program_.program = program;
    program_.backend = es3 ? "es3" : "es2";
    program_.binaryKey = key;
    program_.positionLocation = glGetAttribLocation(program, es3 ? "aPos" : "aPos");
    program_.uvLocation = glGetAttribLocation(program, "aUv");
    program_.textureLocation = glGetUniformLocation(program, "uSceneTex");
    program_.texelSizeLocation = glGetUniformLocation(program, "uTexelSize");
    program_.sharpnessLocation = glGetUniformLocation(program, "uSharpness");
    program_.bloomLocation = glGetUniformLocation(program, "uBloomIntensity");
    program_.fxaaLocation = glGetUniformLocation(program, "uFxaaStrength");

    glGenBuffers(1, &program_.vertexBuffer);
    glBindBuffer(GL_ARRAY_BUFFER, program_.vertexBuffer);
    glBufferData(
        GL_ARRAY_BUFFER,
        sizeof(kFullscreenTriangle),
        kFullscreenTriangle,
        GL_STATIC_DRAW
    );
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    activeBackend_ = program_.backend;
    return true;
}

void HdShaderPipeline::cleanupProgram() {
    if (program_.vertexBuffer != 0) {
        glDeleteBuffers(1, &program_.vertexBuffer);
        program_.vertexBuffer = 0;
    }
    if (program_.program != 0) {
        glDeleteProgram(program_.program);
        program_.program = 0;
    }
    program_.positionLocation = -1;
    program_.uvLocation = -1;
    program_.textureLocation = -1;
    program_.texelSizeLocation = -1;
    program_.sharpnessLocation = -1;
    program_.bloomLocation = -1;
    program_.fxaaLocation = -1;
    program_.binaryKey.clear();
    program_.backend.clear();
}

bool HdShaderPipeline::initialize(bool es3Context) {
    std::lock_guard<std::mutex> lock(mutex_);
    reconfigurePending_.store(false);
    if (initialized_.load() && ready_.load()) return true;
    cleanupProgram();
    ready_.store(false);
    lastError_.clear();
    appliedAnisotropy_.store(1);

    bool started = false;
    if (es3Context) {
        started = tryCreateProgram(true);
        if (!started) {
            sotcLogHdShader("ES 3 shader failed, trying the ES 2 fallback: " + lastError_);
        }
    }
    if (!started) started = tryCreateProgram(false);
    if (!started) {
        sotcLogHdShader("pack not activated: " + lastError_);
        initialized_.store(true);
        return false;
    }

    const Config config = config_;
    sotcLogHdShader(
        "installed=1 version=" + config.version +
        " backend=" + activeBackend_ +
        " preset=" + config.preset +
        " rcas=" + std::to_string(config.rcasSharpness) +
        " bloom=" + std::to_string(config.bloomIntensity) +
        " aniso=" + std::to_string(config.anisotropyLevel) + "x" +
        " fxaa=" + std::string(config.fxaaEnabled ? "1" : "0") +
        " widescreen=" + std::string(config.widescreen16x9 ? "1" : "0") +
        " bandwidthSaver=" + std::string(config.bandwidthSaver ? "1" : "0")
    );
    ready_.store(true);
    initialized_.store(true);
    return true;
}

bool HdShaderPipeline::configureSampler(bool linear, int sourceWidth, int sourceHeight) {
    int requested = 1;
    std::string backend;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        requested = config_.anisotropyLevel;
        backend = activeBackend_;
    }
    float maximum = 1.0f;
    const bool anisotropyAvailable = hasExtension("GL_EXT_texture_filter_anisotropic");
    if (anisotropyAvailable) glGetFloatv(kMaxTextureMaxAnisotropyExt, &maximum);
    int applied = 1;
    if (anisotropyAvailable && requested > 1 && maximum > 1.0f) {
        applied = static_cast<int>(clampFloat(
            static_cast<float>(requested), 1.0f, std::min(maximum, 16.0f)));
        glTexParameterf(GL_TEXTURE_2D, kTextureMaxAnisotropyExt,
                        static_cast<float>(applied));
    }
    appliedAnisotropy_.store(applied);

    // ES 2 only guarantees mipmaps for power-of-two textures unless
    // GL_OES_texture_npot is advertised. The offscreen target follows the
    // surface size, so BALANCED/ULTRA can be NPOT. Sampling an incomplete
    // mipmap chain would turn the fullscreen blit black without a GL error,
    // so keep LINEAR and skip mipmap generation in that case.
    const bool pot = isPowerOfTwo(sourceWidth) && isPowerOfTwo(sourceHeight);
    const bool mipmapsAllowed = pot || backend == "es3" || hasExtension("GL_OES_texture_npot");
    const bool useMipmaps = mipmapsAllowed && applied > 1 && linear;
    const GLenum minFilter = useMipmaps ? GL_LINEAR_MIPMAP_LINEAR
        : (linear ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER,
                    linear ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return useMipmaps;
}

bool HdShaderPipeline::present(
    GLuint sourceTexture,
    int sourceWidth,
    int sourceHeight,
    int surfaceWidth,
    int surfaceHeight,
    bool linear
) {
    if (!ready_.load() || sourceTexture == 0 || sourceWidth <= 0 || sourceHeight <= 0 ||
        surfaceWidth <= 0 || surfaceHeight <= 0) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    if (program_.program == 0 || program_.vertexBuffer == 0) return false;

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, surfaceWidth, surfaceHeight);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);
    const GLenum errorBefore = glGetError();
    if (errorBefore != GL_NO_ERROR) {
        SOTC_LOGW("HD shader pass rejected (GL 0x%x); using the direct blit", errorBefore);
        return false;
    }

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sourceTexture);
    if (configureSampler(linear, sourceWidth, sourceHeight)) {
        glGenerateMipmap(GL_TEXTURE_2D);
    }

    glUseProgram(program_.program);
    if (program_.textureLocation >= 0) glUniform1i(program_.textureLocation, 0);
    if (program_.texelSizeLocation >= 0) {
        glUniform2f(program_.texelSizeLocation,
                    1.0f / static_cast<float>(sourceWidth),
                    1.0f / static_cast<float>(sourceHeight));
    }
    if (program_.sharpnessLocation >= 0) {
        glUniform1f(program_.sharpnessLocation, config_.rcasSharpness);
    }
    if (program_.bloomLocation >= 0) {
        glUniform1f(program_.bloomLocation, config_.bloomIntensity);
    }
    if (program_.fxaaLocation >= 0) {
        glUniform1f(program_.fxaaLocation, config_.fxaaEnabled ? 1.0f : 0.0f);
    }

    glBindBuffer(GL_ARRAY_BUFFER, program_.vertexBuffer);
    if (program_.positionLocation >= 0) {
        glEnableVertexAttribArray(static_cast<GLuint>(program_.positionLocation));
        glVertexAttribPointer(static_cast<GLuint>(program_.positionLocation),
                              2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat),
                              reinterpret_cast<const void*>(0));
    }
    if (program_.uvLocation >= 0) {
        glEnableVertexAttribArray(static_cast<GLuint>(program_.uvLocation));
        glVertexAttribPointer(static_cast<GLuint>(program_.uvLocation),
                              2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat),
                              reinterpret_cast<const void*>(2 * sizeof(GLfloat)));
    }
    glDrawArrays(GL_TRIANGLES, 0, 3);
    if (program_.uvLocation >= 0) {
        glDisableVertexAttribArray(static_cast<GLuint>(program_.uvLocation));
    }
    if (program_.positionLocation >= 0) {
        glDisableVertexAttribArray(static_cast<GLuint>(program_.positionLocation));
    }
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(0);
    const GLenum errorAfter = glGetError();
    if (errorAfter != GL_NO_ERROR) {
        SOTC_LOGW("HD shader pass failed while drawing (GL 0x%x); direct blit follows", errorAfter);
        return false;
    }
    return true;
}

void HdShaderPipeline::discardSource(GLuint framebuffer) {
    if (!config_.bandwidthSaver || framebuffer == 0) return;
    auto invalidate = reinterpret_cast<InvalidateFramebufferFn>(
        eglGetProcAddress("glInvalidateFramebuffer"));
    auto discard = reinterpret_cast<DiscardFramebufferFn>(
        eglGetProcAddress("glDiscardFramebufferEXT"));
    const bool invalidateAvailable = invalidate != nullptr &&
        hasExtension("GL_EXT_invalidate_framebuffer");
    const bool discardAvailable = discard != nullptr &&
        hasExtension("GL_EXT_discard_framebuffer");
    if (!invalidateAvailable && !discardAvailable) return;
    const GLenum attachments[] = {GL_COLOR_ATTACHMENT0};
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    if (invalidateAvailable) {
        invalidate(GL_FRAMEBUFFER, 1, attachments);
    } else {
        discard(GL_FRAMEBUFFER, 1, attachments);
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (!discardLogged_) {
        discardLogged_ = true;
        sotcLogHdShader("tile memory discard enabled for the offscreen colour target");
    }
}

void HdShaderPipeline::shutdown() {
    std::lock_guard<std::mutex> lock(mutex_);
    cleanupProgram();
    ready_.store(false);
    initialized_.store(false);
    activeBackend_.clear();
    discardLogged_ = false;
}

std::string HdShaderPipeline::status() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::string result = ready_.load() ? "active" : "installed";
    result += "|version=" + config_.version;
    result += "|preset=" + config_.preset;
    result += "|backend=" + (ready_.load() ? activeBackend_ : "none");
    result += "|aniso=" + std::to_string(appliedAnisotropy_.load());
    if (!lastError_.empty()) result += "|error=" + lastError_;
    return result;
}

std::string HdShaderPipeline::report() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::string result = "installed=1 version=" + config_.version;
    result += " backend=" + (ready_.load() ? activeBackend_ : "none");
    result += " preset=" + config_.preset;
    result += " rcas=" + std::to_string(config_.rcasSharpness);
    result += " bloom=" + std::to_string(config_.bloomIntensity);
    result += " aniso=" + std::to_string(config_.anisotropyLevel) + "x";
    result += " fxaa=" + std::string(config_.fxaaEnabled ? "1" : "0");
    result += " bandwidthSaver=" + std::string(config_.bandwidthSaver ? "1" : "0");
    return result;
}
