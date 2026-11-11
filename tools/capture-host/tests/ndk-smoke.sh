#!/usr/bin/env bash
# ndk-smoke: prove the Milestone 4 toolchain combination works BEFORE the
# real cross-configure: NDK r26b android.toolchain.cmake + CMake 4.2.3 +
# Ninja + arm64-v8a + android-28, with the D11 policy flag present (as
# configure-android.sh passes it). Validates the D20 choice early; if this
# fails on a toolchain/CMake-4 clash, the D20 fallback (NDK 28.2) applies
# before the pipeline even finishes.
#
# Trivial CPU + disk only - safe to run while the x86 pipeline builds.
# Log: /root/sotc/logs/ndk-smoke.log
set -euo pipefail

NDK=/root/android-ndk-r26b
DIR=/root/sotc/ndk-smoke
LOG=/root/sotc/logs/ndk-smoke.log

log() { printf '%s [ndk-smoke] %s\n' "$(date '+%F %T')" "$*" | tee -a "$LOG"; }

mkdir -p /root/sotc/logs
rm -rf "$DIR"
mkdir -p "$DIR/src"

cat >"$DIR/src/CMakeLists.txt" <<'EOF'
cmake_minimum_required(VERSION 3.22.1)
project(ndk_smoke C CXX)
add_library(smoke SHARED smoke.c)
add_executable(smokebin smoke.c)
EOF

cat >"$DIR/src/smoke.c" <<'EOF'
#include <jni.h>
int smoke_value(void) { return 42; }
int main(void) { return smoke_value() == 42 ? 0 : 1; }
EOF

log "configuring (NDK r26b, arm64-v8a, android-28, D11 policy flag)"
if ! cmake -S "$DIR/src" -B "$DIR/build" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-28 \
    -DANDROID_STL=c++_static \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    >>"$LOG" 2>&1; then
    tail -30 "$LOG"
    log "ABORT: configure failed (CMake/NDK toolchain clash - see D20 fallback)"
    exit 1
fi

log "building"
if ! cmake --build "$DIR/build" >>"$LOG" 2>&1; then
    tail -30 "$LOG"
    log "ABORT: build failed"
    exit 1
fi

log "artifact check (must be AArch64)"
file "$DIR/build/libsmoke.so" "$DIR/build/smokebin" | tee -a "$LOG"
readelf -h "$DIR/build/libsmoke.so" | grep -E 'Class:|Machine:' | tee -a "$LOG"
log "NDK_SMOKE_OK"
