#pragma once

#include <aaudio/AAudio.h>

#include <atomic>
#include <cstdint>

class GameCore;

/**
 * AAudio output backend.
 *
 * When a recompiled game core is loaded, its audioRender() is called from the
 * AAudio callback. Without a core the engine emits a low-level procedural
 * ambience so the audio path can be verified end to end; this is explicitly a
 * diagnostic signal, not game audio.
 */
class AudioEngine {
public:
    AudioEngine() = default;
    ~AudioEngine();

    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    bool start();
    void stop();
    bool running() const { return stream_ != nullptr; }

    /** Number of audio callbacks served since the engine started. */
    uint64_t callbackCount() const {
        return callbackCount_.load(std::memory_order_relaxed);
    }

    void setGameCore(GameCore* core) { core_ = core; }
    void setEnabled(bool enabled) { enabled_.store(enabled); }
    void setVolume(float volume) { volume_.store(volume); }
    void setPaused(bool paused) { paused_.store(paused); }

private:
    static aaudio_data_callback_result_t dataCallback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames
    );

    void render(float* output, int32_t frames, int32_t sampleRate);
    float nextNoise();

    AAudioStream* stream_ = nullptr;
    GameCore* core_ = nullptr;

    std::atomic<bool> enabled_{true};
    std::atomic<bool> paused_{false};
    std::atomic<float> volume_{0.7f};
    std::atomic<uint64_t> callbackCount_{0};

    float gain_ = 0.0f;
    float lowpass_ = 0.0f;
    uint32_t randomState_ = 0x9E3779B9u;
    double phase1_ = 0.0;
    double phase2_ = 0.0;
    double phase3_ = 0.0;
    double lfoPhase_ = 0.0;
};
