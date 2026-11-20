#pragma once

#include <GLES2/gl2.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

/**
 * Optional high-definition presentation pass for the host diagnostics renderer.
 *
 * The pack is installed by Kotlin into app-private storage. This class compiles
 * its sources on the render thread, prefers the ES 3 variant only when the
 * current context really is ES 3, and always keeps an ES 2 GLSL fallback. It is
 * deliberately outside the raylib-owned gameplay renderer: no claim is made
 * that this pass changes the game itself.
 */
class HdShaderPipeline {
public:
    struct Config {
        std::string packDirectory;
        std::string version;
        std::string preset;
        std::string es3Vertex;
        std::string es3Fragment;
        std::string es2Vertex;
        std::string es2Fragment;
        float rcasSharpness = 0.35f;
        float bloomIntensity = 0.26f;
        int anisotropyLevel = 4;
        bool fxaaEnabled = true;
        bool widescreen16x9 = true;
        bool bandwidthSaver = true;
    };

    static HdShaderPipeline& instance();

    void configure(const Config& config);
    bool initialize(bool es3Context);
    bool ready() const { return ready_.load(); }
    bool reconfigurePending() const { return reconfigurePending_.load(); }
    bool bandwidthSaverEnabled() const { return config_.bandwidthSaver; }

    /** Draws the source texture to the default framebuffer. Returns false on fallback. */
    bool present(
        GLuint sourceTexture,
        int sourceWidth,
        int sourceHeight,
        int surfaceWidth,
        int surfaceHeight,
        bool linear
    );

    /**
     * Tells tile-based drivers that the offscreen colour attachment is no
     * longer needed after presentation, avoiding an unnecessary memory store.
     */
    void discardSource(GLuint framebuffer);

    void shutdown();
    std::string status() const;
    std::string report() const;

private:
    HdShaderPipeline() = default;
    ~HdShaderPipeline() = default;

    HdShaderPipeline(const HdShaderPipeline&) = delete;
    HdShaderPipeline& operator=(const HdShaderPipeline&) = delete;

    struct Program {
        GLuint program = 0;
        GLuint vertexBuffer = 0;
        GLint positionLocation = -1;
        GLint uvLocation = -1;
        GLint textureLocation = -1;
        GLint texelSizeLocation = -1;
        GLint sharpnessLocation = -1;
        GLint bloomLocation = -1;
        GLint fxaaLocation = -1;
        std::string backend;
        std::string binaryKey;
    };

    bool tryCreateProgram(bool es3);
    GLuint createProgramFromSources(const std::string& vertex, const std::string& fragment);
    bool tryLoadBinary(GLuint program, const std::string& key);
    bool storeBinary(GLuint program, const std::string& key);
    std::string computeBinaryKey(
        const std::string& vertex,
        const std::string& fragment,
        bool es3
    ) const;
    std::string cachePath(const std::string& key) const;
    void configureSampler(bool linear);
    void cleanupProgram();

    mutable std::mutex mutex_;
    Config config_;
    Program program_;
    std::atomic<bool> ready_{false};
    std::atomic<bool> initialized_{false};
    std::atomic<bool> reconfigurePending_{false};
    std::atomic<int> appliedAnisotropy_{1};
    std::string activeBackend_;
    std::string lastError_;
    bool discardLogged_ = false;
};

/** Emits one line to logcat and the redirected runtime log. */
void sotcLogHdShader(const std::string& message);
