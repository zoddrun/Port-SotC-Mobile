#pragma once

#include <android/native_window.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>

#include "audio_engine.h"
#include "diagnostics_scene.h"
#include "egl_renderer.h"
#include "game_core.h"
#include "hd_shader_pipeline.h"
#include "render_target.h"

/**
 * Native engine singleton.
 *
 * Surface and lifecycle calls arrive on the Android UI thread, rendering and
 * EGL calls run on a dedicated native thread, and audio runs on the AAudio
 * callback thread. Shared state between them is atomic or mutex protected.
 */
class Engine {
public:
    static Engine& instance();

    /** Takes ownership of the ANativeWindow reference. Must not be null. */
    void start(ANativeWindow* window);
    void stop();

    void setPaused(bool paused);
    void setPad(uint32_t buttons, float leftX, float leftY, float rightX, float rightY);

    /**
     * Applies user settings. Safe to call while the renderer is running: every
     * value is atomic and is read at the start of the next frame.
     *
     * @param frameLimitFps 0 for UNLIMITED, otherwise 30 or 60. The matching
     * EGL swap interval is 0, 2 or 1 respectively.
     * @param forceEs2 use the OpenGL ES 2 path at the next session start.
     */
    void configure(
        int resolutionScalePercent,
        bool linearFiltering,
        bool audioEnabled,
        float volume,
        bool adaptiveResolution,
        int frameLimitFps,
        bool forceEs2
    );

    /**
     * Android thermal status: 0 none, 1 light, 2 moderate, 3 severe,
     * 4 critical, 5 emergency, 6 shutdown. The engine caps the render scale as
     * pressure rises; it never changes saved settings or game logic.
     */
    void setThermalStatus(int status);

    /**
     * Releases the offscreen render target and renders directly to the window
     * for the rest of the session. Called from the low-memory callback.
     */
    void trimMemory();

    bool loadGame(const std::string& gameDirectory);
    void unloadGame();

    std::string statusJson() const;
    std::string coreStatus() const;
    std::string hdShaderStatus() const;

private:
    Engine() = default;
    ~Engine();

    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    void stopLocked();
    void renderThreadMain(ANativeWindow* window);

    std::mutex lifecycleMutex_;
    std::thread renderThread_;
    std::atomic<bool> running_{false};
    std::atomic<bool> paused_{false};

    EglRenderer egl_;
    RenderTarget renderTarget_;
    DiagnosticsScene diagnostics_;
    AudioEngine audio_;
    GameCore core_;

    std::atomic<int> resolutionScalePercent_{100};
    std::atomic<int> effectiveScalePercent_{100};
    std::atomic<bool> adaptiveResolution_{true};
    std::atomic<int> frameLimitFps_{0};
    std::atomic<bool> forceEs2_{false};
    std::atomic<int> thermalStatus_{0};
    std::atomic<bool> lowMemoryMode_{false};
    std::atomic<bool> trimRequested_{false};
    std::atomic<bool> linearFiltering_{true};
    std::atomic<bool> audioEnabled_{true};
    std::atomic<float> audioVolume_{0.7f};

    std::atomic<uint32_t> padButtons_{0};
    std::atomic<float> padLeftX_{0.0f};
    std::atomic<float> padLeftY_{0.0f};
    std::atomic<float> padRightX_{0.0f};
    std::atomic<float> padRightY_{0.0f};

    std::atomic<int> surfaceWidth_{0};
    std::atomic<int> surfaceHeight_{0};
    std::atomic<uint64_t> frameCount_{0};
    std::atomic<float> framesPerSecond_{0.0f};
    std::atomic<float> lastFrameMilliseconds_{0.0f};
};
