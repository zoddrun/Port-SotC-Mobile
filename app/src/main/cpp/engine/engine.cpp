#include "engine.h"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <thread>

#include "json_util.h"
#include "log.h"

using Clock = std::chrono::steady_clock;

namespace {

/** Never render below this scale, even when the device is under pressure. */
constexpr int kMinimumScalePercent = 25;

/** Only the documented choices are accepted; anything else becomes 60. */
int sanitizeFrameLimit(int framesPerSecond) {
    return framesPerSecond == 0 || framesPerSecond == 30 || framesPerSecond == 60
        ? framesPerSecond
        : 60;
}

/**
 * Caps the render scale as Android reports thermal pressure. This is a
 * ceiling, not a fixed value: adaptive resolution may still pick something
 * lower, and saved settings are never rewritten.
 */
int thermalScaleCap(int status) {
    switch (status) {
        case 0:
        case 1: return 100;
        case 2: return 85;
        case 3: return 70;
        case 4: return 60;
        default: return 50;
    }
}

}  // namespace

Engine& Engine::instance() {
    static Engine engine;
    return engine;
}

Engine::~Engine() {
    stop();
}

void Engine::start(ANativeWindow* window) {
    if (window == nullptr) return;
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    stopLocked();

    paused_.store(false);
    running_.store(true);
    lowMemoryMode_.store(false);
    trimRequested_.store(false);
    effectiveScalePercent_.store(resolutionScalePercent_.load());
    egl_.setPreferredClientVersion(forceEs2_.load() ? 2 : 0);

    audio_.setGameCore(&core_);
    audio_.setEnabled(audioEnabled_.load());
    audio_.setVolume(audioVolume_.load());
    audio_.setPaused(false);
    audio_.start();

    renderThread_ = std::thread(&Engine::renderThreadMain, this, window);
}

void Engine::stop() {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    stopLocked();
}

void Engine::stopLocked() {
    running_.store(false);
    if (renderThread_.joinable()) {
        renderThread_.join();
    }
    audio_.stop();
    surfaceWidth_.store(0);
    surfaceHeight_.store(0);
}

void Engine::setPaused(bool paused) {
    const bool wasPaused = paused_.exchange(paused);
    if (wasPaused != paused) {
        SOTC_LOGI(paused ? "Engine paused" : "Engine resumed");
    }
    audio_.setPaused(paused);
    core_.setPaused(paused);
}

void Engine::setPad(uint32_t buttons, float leftX, float leftY, float rightX, float rightY) {
    padButtons_.store(buttons);
    padLeftX_.store(leftX);
    padLeftY_.store(leftY);
    padRightX_.store(rightX);
    padRightY_.store(rightY);
    core_.setPad(buttons, leftX, leftY, rightX, rightY);
}

void Engine::configure(
    int resolutionScalePercent,
    bool linearFiltering,
    bool audioEnabled,
    float volume,
    bool adaptiveResolution,
    int frameLimitFps,
    bool forceEs2
) {
    resolutionScalePercent_.store(std::clamp(resolutionScalePercent, kMinimumScalePercent, 100));
    linearFiltering_.store(linearFiltering);
    audioEnabled_.store(audioEnabled);
    audioVolume_.store(std::clamp(volume, 0.0f, 1.0f));
    adaptiveResolution_.store(adaptiveResolution);
    frameLimitFps_.store(sanitizeFrameLimit(frameLimitFps));
    forceEs2_.store(forceEs2);
    audio_.setEnabled(audioEnabled);
    audio_.setVolume(volume);
}

void Engine::setThermalStatus(int status) {
    const int clamped = std::clamp(status, 0, 6);
    const int previous = thermalStatus_.exchange(clamped);
    if (previous != clamped) {
        SOTC_LOGI(
            "Thermal status %d -> %d; render scale cap is now %d%%",
            previous,
            clamped,
            thermalScaleCap(clamped)
        );
    }
}

void Engine::trimMemory() {
    trimRequested_.store(true);
    SOTC_LOGW("Low-memory notification received; dropping the offscreen render target");
}

bool Engine::loadGame(const std::string& gameDirectory) {
    if (running_.load()) {
        SOTC_LOGW("Refusing to load a game core while the renderer is running");
        return false;
    }
    return core_.load(gameDirectory);
}

void Engine::unloadGame() {
    if (running_.load()) {
        SOTC_LOGW("Refusing to unload a game core while the renderer is running");
        return;
    }
    core_.unload();
}

void Engine::renderThreadMain(ANativeWindow* window) {
    if (window == nullptr) return;
    if (!egl_.initialize(window)) {
        SOTC_LOGE("Unable to initialize EGL; render thread will stop");
        running_.store(false);
        ANativeWindow_release(window);
        return;
    }
    SOTC_LOGI("Renderer backend: %s", egl_.backendName().c_str());
    if (!diagnostics_.initialize()) {
        SOTC_LOGE("Diagnostic renderer initialization failed; only clearing the surface");
    }

    const auto startTime = Clock::now();
    auto lastFrame = startTime;
    auto fpsWindowStart = startTime;
    auto lastAdaptation = startTime;
    double smoothedFrameMs = 0.0;
    int framesInWindow = 0;
#ifndef NDEBUG
    auto statusWindowStart = startTime;
#endif

    while (running_.load()) {
        const int width = ANativeWindow_getWidth(window);
        const int height = ANativeWindow_getHeight(window);
        if (width <= 0 || height <= 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        surfaceWidth_.store(width);
        surfaceHeight_.store(height);

        if (paused_.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(16));
            lastFrame = Clock::now();
            continue;
        }

        if (trimRequested_.exchange(false)) {
            // GL calls belong to this thread, so the release happens here.
            renderTarget_.shutdown();
            lowMemoryMode_.store(true);
            SOTC_LOGI("Offscreen render target released; rendering directly to the window");
        }

        const auto now = Clock::now();
        const double measuredSeconds = std::chrono::duration<double>(now - lastFrame).count();
        lastFrame = now;
        // Keep the reported frame time honest, but clamp the simulation delta
        // so a slow frame (for example under a software GL driver) does not
        // make the game core simulate a large catch-up step.
        lastFrameMilliseconds_.store(static_cast<float>(measuredSeconds * 1000.0));
        double frameSeconds = measuredSeconds;
        if (frameSeconds <= 0.0 || frameSeconds > 0.1) {
            frameSeconds = 1.0 / 60.0;
        }

        // The effective scale is the configured scale reduced by the thermal
        // ceiling and by adaptive resolution, and never below the floor.
        const int configuredScale = resolutionScalePercent_.load();
        const int scaleCeiling =
            std::min(configuredScale, thermalScaleCap(thermalStatus_.load()));
        int scale = effectiveScalePercent_.load();
        if (!adaptiveResolution_.load() || scale > scaleCeiling) {
            scale = scaleCeiling;
        }
        scale = std::clamp(scale, kMinimumScalePercent, scaleCeiling);
        effectiveScalePercent_.store(scale);

        const int renderWidth = std::max(1, width * scale / 100);
        const int renderHeight = std::max(1, height * scale / 100);

        if (!lowMemoryMode_.load() && renderTarget_.ensure(renderWidth, renderHeight)) {
            renderTarget_.bind();
            glClearColor(0.031f, 0.039f, 0.047f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            if (core_.initialized()) {
                core_.render(renderWidth, renderHeight, frameSeconds);
            } else {
                const double elapsed =
                    std::chrono::duration<double>(now - startTime).count();
                diagnostics_.render(renderWidth, renderHeight, elapsed);
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            renderTarget_.present(width, height, linearFiltering_.load());
        } else {
            // Direct path: either the low-memory fallback or a driver that
            // refused the offscreen framebuffer. It must still draw the game
            // core, not only the diagnostic scene.
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, width, height);
            glClearColor(0.031f, 0.039f, 0.047f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            if (core_.initialized()) {
                core_.render(width, height, frameSeconds);
            } else {
                const double elapsed =
                    std::chrono::duration<double>(now - startTime).count();
                diagnostics_.render(width, height, elapsed);
            }
        }

        egl_.swapBuffers();

        frameCount_.fetch_add(1);
        framesInWindow++;
        const double fpsElapsed = std::chrono::duration<double>(now - fpsWindowStart).count();
        if (fpsElapsed >= 0.5) {
            framesPerSecond_.store(static_cast<float>(framesInWindow / fpsElapsed));
            fpsWindowStart = now;
            framesInWindow = 0;
        }

        const int frameLimit = frameLimitFps_.load();
        if (frameLimit > 0) {
            // The simulation delta is measured wall-clock time, so pacing the
            // presentation slows the number of updates without speeding up or
            // slowing down the game's own timeline.
            const auto deadline = now + std::chrono::duration_cast<Clock::duration>(
                std::chrono::duration<double>(1.0 / frameLimit)
            );
            const auto finished = Clock::now();
            if (finished < deadline) {
                std::this_thread::sleep_until(deadline);
            }
        }

        if (adaptiveResolution_.load()) {
            const double frameMs = measuredSeconds * 1000.0;
            smoothedFrameMs = smoothedFrameMs <= 0.0
                ? frameMs
                : smoothedFrameMs * 0.9 + frameMs * 0.1;
            const double budgetMs = 1000.0 / (frameLimit > 0 ? frameLimit : 60);
            const double sinceAdaptation =
                std::chrono::duration<double>(now - lastAdaptation).count();
            if (sinceAdaptation >= 0.75) {
                const int floorScale = std::max(kMinimumScalePercent, scaleCeiling / 2);
                int next = effectiveScalePercent_.load();
                if (smoothedFrameMs > budgetMs * 1.20 && next > floorScale) {
                    next = std::max(floorScale, next - 5);
                } else if (smoothedFrameMs < budgetMs * 0.75 && next < scaleCeiling) {
                    next = std::min(scaleCeiling, next + 5);
                }
                if (next != effectiveScalePercent_.load()) {
                    SOTC_LOGI(
                        "Adaptive resolution: %d%% -> %d%% (%.1f ms smoothed, %.1f ms budget)",
                        effectiveScalePercent_.load(),
                        next,
                        smoothedFrameMs,
                        budgetMs
                    );
                    effectiveScalePercent_.store(next);
                    // Let the new size settle before the next step.
                    smoothedFrameMs = 0.0;
                }
                lastAdaptation = now;
            }
        }

#ifndef NDEBUG
        // Debug builds publish a periodic frame summary so that rendering and
        // resolution scaling can be measured from adb without reading the
        // on-screen overlay.
        const double statusElapsed =
            std::chrono::duration<double>(now - statusWindowStart).count();
        if (statusElapsed >= 5.0) {
            SOTC_LOGI(
                "Frame stats: %.1f fps, %.2f ms, %dx%d surface, %d%% scale (%s), "
                "%d%% configured, limit %d fps, thermal %d, %llu audio callbacks",
                framesPerSecond_.load(),
                lastFrameMilliseconds_.load(),
                width,
                height,
                effectiveScalePercent_.load(),
                egl_.backendName().c_str(),
                resolutionScalePercent_.load(),
                frameLimitFps_.load(),
                thermalStatus_.load(),
                static_cast<unsigned long long>(audio_.callbackCount())
            );
            statusWindowStart = now;
        }
#endif
    }

    renderTarget_.shutdown();
    diagnostics_.shutdown();
    egl_.shutdown();
    ANativeWindow_release(window);
    SOTC_LOGI("Render thread stopped");
}

std::string Engine::statusJson() const {
    char buffer[160];
    std::string json = "{";
    json += "\"running\":" + std::string(running_.load() ? "true" : "false");
    json += ",\"paused\":" + std::string(paused_.load() ? "true" : "false");
    std::snprintf(buffer, sizeof(buffer), ",\"width\":%d", surfaceWidth_.load());
    json += buffer;
    std::snprintf(buffer, sizeof(buffer), ",\"height\":%d", surfaceHeight_.load());
    json += buffer;
    std::snprintf(buffer, sizeof(buffer), ",\"fps\":%.1f", framesPerSecond_.load());
    json += buffer;
    std::snprintf(
        buffer,
        sizeof(buffer),
        ",\"frameMs\":%.2f",
        lastFrameMilliseconds_.load()
    );
    json += buffer;
    std::snprintf(buffer, sizeof(buffer), ",\"scalePercent\":%d", resolutionScalePercent_.load());
    json += buffer;
    std::snprintf(
        buffer,
        sizeof(buffer),
        ",\"effectiveScalePercent\":%d",
        effectiveScalePercent_.load()
    );
    json += buffer;
    std::snprintf(buffer, sizeof(buffer), ",\"frameLimitFps\":%d", frameLimitFps_.load());
    json += buffer;
    std::snprintf(buffer, sizeof(buffer), ",\"thermalStatus\":%d", thermalStatus_.load());
    json += buffer;
    json += ",\"adaptiveResolution\":" +
        std::string(adaptiveResolution_.load() ? "true" : "false");
    json += ",\"lowMemoryMode\":" + std::string(lowMemoryMode_.load() ? "true" : "false");
    json += ",\"backend\":\"" + sotcJsonEscape(egl_.backendName()) + "\"";
    json += ",\"linearFiltering\":" + std::string(linearFiltering_.load() ? "true" : "false");
    json += ",\"audioEnabled\":" + std::string(audioEnabled_.load() ? "true" : "false");
    json += ",\"padButtons\":" + std::to_string(padButtons_.load());
    std::snprintf(buffer, sizeof(buffer), ",\"frameCount\":%llu",
                  static_cast<unsigned long long>(frameCount_.load()));
    json += buffer;
    json += ",\"core\":" + core_.infoJson();
    json += "}";
    return json;
}

std::string Engine::coreStatus() const {
    return core_.status();
}
