#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>

#include "sotc_game_api.h"

/**
 * Runtime loader for a separately built static-recompilation game core.
 *
 * The host never fabricates gameplay when the core is absent. load() reports a
 * precise reason in status() and leaves initialized() false.
 */
class GameCore {
public:
    GameCore() = default;
    ~GameCore();

    GameCore(const GameCore&) = delete;
    GameCore& operator=(const GameCore&) = delete;

    bool load(const std::string& gameDirectory);
    void unload();

    bool initialized() const { return initialized_.load(); }
    const std::string status() const;

    void setPaused(bool paused);
    void setPad(uint32_t buttons, float leftX, float leftY, float rightX, float rightY);
    void render(int width, int height, double frameTimeSeconds);
    void audioRender(float* interleavedStereo, int frames);

    std::string infoJson() const;

private:
    static void hostLog(void* userData, int32_t level, const char* message);
    static uint32_t hostPadButtons(void* userData);
    static void hostPadSticks(
        void* userData,
        float* leftX,
        float* leftY,
        float* rightX,
        float* rightY
    );
    static int64_t hostReadGameFile(
        void* userData,
        const char* name,
        void* buffer,
        uint32_t capacity
    );

    void setStatus(const std::string& status);

    void* handle_ = nullptr;
    const SOTCGameApi* api_ = nullptr;
    SOTCGameHost host_{};
    std::atomic<bool> initialized_{false};
    std::atomic<bool> paused_{false};
    std::atomic<uint32_t> buttons_{0};
    std::atomic<float> leftX_{0.0f};
    std::atomic<float> leftY_{0.0f};
    std::atomic<float> rightX_{0.0f};
    std::atomic<float> rightY_{0.0f};

    std::string directory_;
    std::string name_ = "not loaded";
    std::string serial_ = "-";
    std::string buildId_ = "-";

    mutable std::mutex statusMutex_;
    std::string status_ = "Native game core library is not installed (libsotc_game.so)";
};
