#pragma once

#include <android/log.h>

#define SOTC_LOG_TAG "SotcNative"

#define SOTC_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, SOTC_LOG_TAG, __VA_ARGS__)
#define SOTC_LOGI(...) __android_log_print(ANDROID_LOG_INFO, SOTC_LOG_TAG, __VA_ARGS__)
#define SOTC_LOGW(...) __android_log_print(ANDROID_LOG_WARN, SOTC_LOG_TAG, __VA_ARGS__)
#define SOTC_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SOTC_LOG_TAG, __VA_ARGS__)
