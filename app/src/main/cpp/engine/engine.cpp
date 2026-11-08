#include "engine.h"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <thread>

#include "json_util.h"
#include "log.h"

using Clock = std::chrono::steady_clock;

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
    paused_.store(paused);
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
    float volume
) {
    resolutionScalePercent_.store(std::clamp(resolutionScalePercent, 25, 100));
    linearFiltering_.store(linearFiltering);
    audioEnabled_.store(audioEnabled);
    audioVolume_.store(std::clamp(volume, 0.0f, 1.0f));
    audio_.setEnabled(audioEnabled);
    audio_.setVolume(volume);
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
    if (!diagnostics_.initialize()) {
        SOTC_LOGE("Diagnostic renderer initialization failed; only clearing the surface");
    }

    const auto startTime = Clock::now();
    auto lastFrame = startTime;
    auto fpsWindowStart = startTime;
    int framesInWindow = 0;

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

        const auto now = Clock::now();
        double frameSeconds = std::chrono::duration<double>(now - lastFrame).count();
        lastFrame = now;
        if (frameSeconds <= 0.0 || frameSeconds > 0.1) {
            frameSeconds = 1.0 / 60.0;
        }

        const int scale = resolutionScalePercent_.load();
        const int renderWidth = std::max(1, width * scale / 100);
        const int renderHeight = std::max(1, height * scale / 100);

        if (renderTarget_.ensure(renderWidth, renderHeight)) {
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
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, width, height);
            glClearColor(0.031f, 0.039f, 0.047f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            if (!core_.initialized()) {
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
        lastFrameMilliseconds_.store(static_cast<float>(frameSeconds * 1000.0));
    }

    renderTarget_.shutdown();
    diagnostics_.shutdown();
    egl_.shutdown();
    ANativeWindow_release(window);
    SOTC_LOGI("Render thread stopped");
}

std::string Engine::statusJson() const {
    char buffer[128];
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
