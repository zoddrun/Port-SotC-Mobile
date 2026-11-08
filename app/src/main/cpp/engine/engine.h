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
    void configure(int resolutionScalePercent, bool linearFiltering, bool audioEnabled, float volume);

    bool loadGame(const std::string& gameDirectory);
    void unloadGame();

    std::string statusJson() const;
    std::string coreStatus() const;

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
