#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <cstdlib>
#include <string>

#include "engine/engine.h"
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
    jfloat volume
) {
    Engine::instance().configure(
        static_cast<int>(resolutionScalePercent),
        linearFiltering == JNI_TRUE,
        audioEnabled == JNI_TRUE,
        static_cast<float>(volume)
    );
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
