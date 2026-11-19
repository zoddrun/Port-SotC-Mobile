#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <cstdio>
#include <cstdlib>
#include <string>

#include "engine/engine.h"
#include "engine/gpu_info.h"
#include "engine/log.h"

namespace {

std::string fromJavaString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* characters = env->GetStringUTFChars(value, nullptr);
    if (characters == nullptr) return {};
    std::string result(characters);
    env->ReleaseStringUTFChars(value, characters);
    return result;
}

jstring toJavaString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeStart(
    JNIEnv* env,
    jobject /* thiz */,
    jobject surface
) {
    if (surface == nullptr) {
        SOTC_LOGE("nativeStart called with a null Surface");
        return;
    }
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        SOTC_LOGE("Unable to obtain ANativeWindow from Surface");
        return;
    }
    SOTC_LOGI("Starting native engine for surface %p", window);
    Engine::instance().start(window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeStop(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    SOTC_LOGI("Stopping native engine");
    Engine::instance().stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeSetPaused(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jboolean paused
) {
    Engine::instance().setPaused(paused == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeSetPad(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint buttons,
    jfloat leftX,
    jfloat leftY,
    jfloat rightX,
    jfloat rightY
) {
    Engine::instance().setPad(
        static_cast<uint32_t>(buttons),
        static_cast<float>(leftX),
        static_cast<float>(leftY),
        static_cast<float>(rightX),
        static_cast<float>(rightY)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeConfigure(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint resolutionScalePercent,
    jboolean linearFiltering,
    jboolean audioEnabled,
    jfloat volume,
    jboolean adaptiveResolution,
    jint frameLimitFps,
    jboolean forceEs2
) {
    Engine::instance().configure(
        static_cast<int>(resolutionScalePercent),
        linearFiltering == JNI_TRUE,
        audioEnabled == JNI_TRUE,
        static_cast<float>(volume),
        adaptiveResolution == JNI_TRUE,
        static_cast<int>(frameLimitFps),
        forceEs2 == JNI_TRUE
    );
}

/*
 * Creates a short-lived offscreen EGL context and reports what the driver
 * actually supports. It runs off the UI thread and never crashes the app: a
 * driver that rejects every context produces an `available=0` report.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeGpuInfo(
    JNIEnv* env,
    jobject /* thiz */,
    jboolean forceEs2
) {
    return toJavaString(
        env,
        gpuCapabilitiesReport(queryGpuCapabilities(forceEs2 == JNI_TRUE))
    );
}

/*
 * Android thermal status (PowerManager.THERMAL_STATUS_*). The engine lowers its
 * render scale ceiling as pressure rises without touching saved settings.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeSetThermalStatus(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint status
) {
    Engine::instance().setThermalStatus(static_cast<int>(status));
}

/*
 * The system is short on memory. The engine releases its offscreen render
 * target and renders directly to the window for the rest of the session.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeTrimMemory(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    Engine::instance().trimMemory();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeLoadGame(
    JNIEnv* env,
    jobject /* thiz */,
    jstring gameDirectory
) {
    const std::string directory = fromJavaString(env, gameDirectory);
    if (directory.empty()) {
        SOTC_LOGE("nativeLoadGame called with an empty directory");
        return JNI_FALSE;
    }
    const bool loaded = Engine::instance().loadGame(directory);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeUnloadGame(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    Engine::instance().unloadGame();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeStatus(
    JNIEnv* env,
    jobject /* thiz */
) {
    return toJavaString(env, Engine::instance().statusJson());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeCoreStatus(
    JNIEnv* env,
    jobject /* thiz */
) {
    return toJavaString(env, Engine::instance().coreStatus());
}

/*
 * Publishes an environment variable for other native libraries in this
 * process. The recompiled runtime runs as its own shared library and reads
 * SOTC_BOOT_ELF before loading the guest executable, because Android starts
 * it without argv and its private storage path is only known at run time.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeSetEnvironment(
    JNIEnv* env,
    jobject /* thiz */,
    jstring name,
    jstring value
) {
    const std::string key = fromJavaString(env, name);
    if (key.empty()) {
        return JNI_FALSE;
    }
    const std::string text = fromJavaString(env, value);
    if (text.empty()) {
        unsetenv(key.c_str());
        return JNI_TRUE;
    }
    return setenv(key.c_str(), text.c_str(), 1) == 0 ? JNI_TRUE : JNI_FALSE;
}

/*
 * Points this process's stdout and stderr at a file. The recompiled runtime
 * reports its progress and failures with std::cout/std::cerr, and Android
 * discards a framework-started app's stdio, so without this the first boot
 * would leave no trace beyond the in-memory ring the runtime's own menu shows.
 * The descriptors are process-wide, so redirecting here (before the runner
 * library loads) captures the runner's output as well.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_portsotc_mobile_nativebridge_NativeCore_nativeRedirectStdio(
    JNIEnv* env,
    jobject /* thiz */,
    jstring path
) {
    const std::string file = fromJavaString(env, path);
    if (file.empty()) {
        return JNI_FALSE;
    }
    if (std::freopen(file.c_str(), "a", stdout) == nullptr) {
        return JNI_FALSE;
    }
    if (std::freopen(file.c_str(), "a", stderr) == nullptr) {
        return JNI_FALSE;
    }
    // Line buffered, so a crash still leaves the last lines on disk.
    std::setvbuf(stdout, nullptr, _IOLBF, 0);
    std::setvbuf(stderr, nullptr, _IOLBF, 0);
    return JNI_TRUE;
}
