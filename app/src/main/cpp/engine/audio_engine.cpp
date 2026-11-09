#include "audio_engine.h"

#include <algorithm>
#include <cmath>

#include "game_core.h"
#include "log.h"

namespace {
constexpr double kTwoPi = 6.28318530717958647692;
constexpr float kMaxVolume = 1.0f;
}  // namespace

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start() {
    if (stream_ != nullptr) return true;

    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK || builder == nullptr) {
        SOTC_LOGE("Unable to create AAudio stream builder");
        return false;
    }
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, 48000);
    AAudioStreamBuilder_setDataCallback(builder, &AudioEngine::dataCallback, this);

    AAudioStream* stream = nullptr;
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK || stream == nullptr) {
        SOTC_LOGE("AAudio openStream failed: %s", AAudio_convertResultToText(result));
        return false;
    }

    stream_ = stream;
    result = AAudioStream_requestStart(stream_);
    if (result != AAUDIO_OK) {
        SOTC_LOGE("AAudio requestStart failed: %s", AAudio_convertResultToText(result));
        AAudioStream_close(stream_);
        stream_ = nullptr;
        return false;
    }
    SOTC_LOGI(
        "AAudio started: %d Hz, %d channels",
        AAudioStream_getSampleRate(stream_),
        AAudioStream_getChannelCount(stream_)
    );
    return true;
}

void AudioEngine::stop() {
    if (stream_ == nullptr) return;
    AAudioStream_requestStop(stream_);
    AAudioStream_close(stream_);
    stream_ = nullptr;
    SOTC_LOGI("AAudio stopped");
}

aaudio_data_callback_result_t AudioEngine::dataCallback(
    AAudioStream* stream,
    void* userData,
    void* audioData,
    int32_t numFrames
) {
    auto* self = static_cast<AudioEngine*>(userData);
    auto* output = static_cast<float*>(audioData);
    if (self == nullptr || output == nullptr || numFrames <= 0) {
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    const int32_t sampleRate = AAudioStream_getSampleRate(stream);
    self->callbackCount_.fetch_add(1, std::memory_order_relaxed);
    self->render(output, numFrames, sampleRate > 0 ? sampleRate : 48000);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AudioEngine::render(float* output, int32_t frames, int32_t sampleRate) {
    const int32_t channels = 2;
    const bool silent = paused_.load() || !enabled_.load();
    const float targetGain = silent ? 0.0f : volume_.load() * kMaxVolume;

    if (core_ != nullptr && core_->initialized()) {
        std::fill(output, output + static_cast<size_t>(frames) * channels, 0.0f);
        core_->audioRender(output, frames);
        for (int32_t i = 0; i < frames * channels; i++) {
            gain_ += (targetGain - gain_) * 0.0008f;
            output[i] *= gain_;
        }
        return;
    }

    const double sampleRateDouble = static_cast<double>(sampleRate);
    const double phaseIncrement1 = kTwoPi * 55.0 / sampleRateDouble;
    const double phaseIncrement2 = kTwoPi * 82.5 / sampleRateDouble;
    const double phaseIncrement3 = kTwoPi * 110.0 / sampleRateDouble;
    const double lfoIncrement = kTwoPi * 0.07 / sampleRateDouble;

    for (int32_t frame = 0; frame < frames; frame++) {
        gain_ += (targetGain - gain_) * 0.0008f;

        const float white = nextNoise();
        lowpass_ += 0.018f * (white - lowpass_);
        const float lfo = 0.55f + 0.45f * static_cast<float>(std::sin(lfoPhase_));
        const float wind = lowpass_ * 0.9f * lfo;

        const float drone =
            static_cast<float>(std::sin(phase1_)) * 0.030f +
            static_cast<float>(std::sin(phase2_)) * 0.018f +
            static_cast<float>(std::sin(phase3_)) * 0.012f;
        const float sample = (wind * 0.22f + drone) * gain_;

        output[frame * channels] = sample;
        output[frame * channels + 1] = sample;

        phase1_ += phaseIncrement1;
        phase2_ += phaseIncrement2;
        phase3_ += phaseIncrement3;
        lfoPhase_ += lfoIncrement;
        if (phase1_ > kTwoPi) phase1_ -= kTwoPi;
        if (phase2_ > kTwoPi) phase2_ -= kTwoPi;
        if (phase3_ > kTwoPi) phase3_ -= kTwoPi;
        if (lfoPhase_ > kTwoPi) lfoPhase_ -= kTwoPi;
    }
}

float AudioEngine::nextNoise() {
    randomState_ ^= randomState_ << 13;
    randomState_ ^= randomState_ >> 17;
    randomState_ ^= randomState_ << 5;
    return static_cast<float>(randomState_ & 0x00FFFFFFu) / 8388608.0f - 1.0f;
}
