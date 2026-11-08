cmake_minimum_required(VERSION 3.22.1)

project(sotc_core CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
    sotc_core
    SHARED
    native_core.cpp
    engine/engine.cpp
    engine/egl_renderer.cpp
    engine/gl_util.cpp
    engine/render_target.cpp
    engine/diagnostics_scene.cpp
    engine/audio_engine.cpp
    engine/game_core.cpp
)

target_include_directories(sotc_core PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log_lib log)
find_library(android_lib android)
find_library(egl_lib EGL)
find_library(gles_lib GLESv3)
find_library(aaudio_lib aaudio)

target_link_libraries(
    sotc_core
    PRIVATE
    ${log_lib}
    ${android_lib}
    ${egl_lib}
    ${gles_lib}
    ${aaudio_lib}
)

target_compile_options(
    sotc_core
    PRIVATE
    -Wall
    -Wextra
    -Werror=return-type
    -Wno-unused-parameter
)
