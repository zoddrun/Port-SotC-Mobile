#include "game_core.h"

#include <android/log.h>
#include <dlfcn.h>

#include <cstdio>
#include <cstring>
#include <fstream>
#include <vector>

#include "json_util.h"
#include "log.h"

namespace {

bool safeRelativePath(const std::string& name) {
    if (name.empty() || name.size() > 1024) return false;
    if (name[0] == '/') return false;
    if (name.find("..") != std::string::npos) return false;
    if (name.find('\\') != std::string::npos) return false;
    return name.find(':') == std::string::npos;
}

}  // namespace

GameCore::~GameCore() {
    unload();
}

bool GameCore::load(const std::string& gameDirectory) {
    unload();
    directory_ = gameDirectory;

    // Android 10 and later forbid loading executable code from writable app
    // storage. The only supported location is the read-only native library
    // directory populated from the APK, which is searched by soname.
    std::string libraryPath = "libsotc_game.so";
    handle_ = dlopen(libraryPath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle_ == nullptr) {
        const char* error = dlerror();
        SOTC_LOGI("Game core not available: %s", error != nullptr ? error : "unknown dlopen error");
        setStatus("Native game core library is not installed (libsotc_game.so)");
        return false;
    }

    auto query = reinterpret_cast<sotc_game_query_api_fn>(
        dlsym(handle_, "sotc_game_query_api")
    );
    if (query == nullptr) {
        SOTC_LOGE("Game core does not export sotc_game_query_api");
        setStatus("Game core library does not export the required sotc_game_query_api symbol");
        unload();
        return false;
    }
    api_ = query();
    if (api_ == nullptr) {
        setStatus("Game core query returned no API table");
        unload();
        return false;
    }
    if (api_->apiVersion != SOTC_GAME_API_VERSION) {
        setStatus(
            "Game core ABI version " + std::to_string(api_->apiVersion) +
            " does not match host version " + std::to_string(SOTC_GAME_API_VERSION)
        );
        api_ = nullptr;
        unload();
        return false;
    }
    if (api_->queryInfo == nullptr || api_->initialize == nullptr ||
        api_->shutdown == nullptr || api_->render == nullptr ||
        api_->audioRender == nullptr || api_->setPaused == nullptr
    ) {
        setStatus("Game core API table is incomplete");
        api_ = nullptr;
        unload();
        return false;
    }

    const SOTCGameInfo* info = api_->queryInfo();
    if (info != nullptr) {
        name_ = info->name != nullptr ? info->name : "unnamed core";
        serial_ = info->serial != nullptr ? info->serial : "-";
        buildId_ = info->buildId != nullptr ? info->buildId : "-";
    } else {
        name_ = "unnamed core";
    }

    host_ = {};
    host_.apiVersion = SOTC_GAME_API_VERSION;
    host_.userData = this;
    host_.log = &GameCore::hostLog;
    host_.padButtons = &GameCore::hostPadButtons;
    host_.padSticks = &GameCore::hostPadSticks;
    host_.readGameFile = &GameCore::hostReadGameFile;

    const int32_t result = api_->initialize(&host_, gameDirectory.c_str());
    if (result != 0) {
        setStatus(
            "Game core initialization failed with code " + std::to_string(result)
        );
        if (api_->shutdown != nullptr) {
            api_->shutdown();
        }
        api_ = nullptr;
        unload();
        return false;
    }

    initialized_.store(true);
    setStatus("Native game core ready: " + name_ + " (" + serial_ + ", build " + buildId_ + ")");
    SOTC_LOGI("Loaded game core %s from %s", name_.c_str(), libraryPath.c_str());
    return true;
}

void GameCore::unload() {
    if (initialized_.exchange(false) && api_ != nullptr && api_->shutdown != nullptr) {
        api_->shutdown();
    }
    api_ = nullptr;
    if (handle_ != nullptr) {
        dlclose(handle_);
        handle_ = nullptr;
    }
    name_ = "not loaded";
    serial_ = "-";
    buildId_ = "-";
}

const std::string GameCore::status() const {
    std::lock_guard<std::mutex> lock(statusMutex_);
    return status_;
}

void GameCore::setStatus(const std::string& status) {
    std::lock_guard<std::mutex> lock(statusMutex_);
    status_ = status;
    SOTC_LOGI("%s", status.c_str());
}

void GameCore::setPaused(bool paused) {
    paused_.store(paused);
    if (initialized_.load() && api_ != nullptr && api_->setPaused != nullptr) {
        api_->setPaused(paused ? 1 : 0);
    }
}

void GameCore::setPad(uint32_t buttons, float leftX, float leftY, float rightX, float rightY) {
    buttons_.store(buttons);
    leftX_.store(leftX);
    leftY_.store(leftY);
    rightX_.store(rightX);
    rightY_.store(rightY);
}

void GameCore::render(int width, int height, double frameTimeSeconds) {
    if (initialized_.load() && api_ != nullptr) {
        api_->render(width, height, frameTimeSeconds);
    }
}

void GameCore::audioRender(float* interleavedStereo, int frames) {
    if (initialized_.load() && api_ != nullptr) {
        api_->audioRender(interleavedStereo, frames);
    }
}

std::string GameCore::infoJson() const {
    std::string json = "{";
    json += "\"initialized\":" + std::string(initialized_.load() ? "true" : "false");
    json += ",\"libraryPresent\":" + std::string(handle_ != nullptr ? "true" : "false");
    json += ",\"name\":\"" + sotcJsonEscape(name_) + "\"";
    json += ",\"serial\":\"" + sotcJsonEscape(serial_) + "\"";
    json += ",\"buildId\":\"" + sotcJsonEscape(buildId_) + "\"";
    json += ",\"status\":\"" + sotcJsonEscape(status()) + "\"";
    json += "}";
    return json;
}

void GameCore::hostLog(void* userData, int32_t level, const char* message) {
    (void)userData;
    if (message == nullptr) return;
    int priority = ANDROID_LOG_INFO;
    if (level <= 0) priority = ANDROID_LOG_DEBUG;
    else if (level == 2) priority = ANDROID_LOG_WARN;
    else if (level >= 3) priority = ANDROID_LOG_ERROR;
    __android_log_write(priority, "SotcGameCore", message);
}

uint32_t GameCore::hostPadButtons(void* userData) {
    auto* self = static_cast<GameCore*>(userData);
    return self != nullptr ? self->buttons_.load() : 0;
}

void GameCore::hostPadSticks(
    void* userData,
    float* leftX,
    float* leftY,
    float* rightX,
    float* rightY
) {
    auto* self = static_cast<GameCore*>(userData);
    if (self == nullptr) return;
    if (leftX != nullptr) *leftX = self->leftX_.load();
    if (leftY != nullptr) *leftY = self->leftY_.load();
    if (rightX != nullptr) *rightX = self->rightX_.load();
    if (rightY != nullptr) *rightY = self->rightY_.load();
}

int64_t GameCore::hostReadGameFile(
    void* userData,
    const char* name,
    void* buffer,
    uint32_t capacity
) {
    auto* self = static_cast<GameCore*>(userData);
    if (self == nullptr || name == nullptr || buffer == nullptr) return -1;
    const std::string relative(name);
    if (!safeRelativePath(relative)) return -1;

    const std::string candidates[] = {
        self->directory_ + "/" + relative,
        self->directory_ + "/modules/" + relative
    };
    for (const std::string& path : candidates) {
        std::ifstream input(path, std::ios::binary);
        if (!input.is_open()) continue;
        input.read(static_cast<char*>(buffer), capacity);
        const std::streamsize read = input.gcount();
        return static_cast<int64_t>(read);
    }
    return -1;
}
