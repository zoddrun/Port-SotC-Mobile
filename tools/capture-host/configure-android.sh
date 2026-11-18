#!/usr/bin/env bash
# configure-android: Milestone 4 - ARM64 cross-configure of the recompiled
# SotC core for Android (arm64-v8a) against the celosia/PS2Recomp tree in
# the adopted WSL workspace (/root - space-free per D6/D10).
#
# RUN ONLY AFTER THE PIPELINE IS DONE. Never while a capture build runs:
# the android-integration patch series changes files in the runner build
# set (host_menu.cpp / games_database.cpp / main.cpp), and the x86
# pipeline rebuilds those. This script refuses to start while
# pipeline-remaining.sh is running or its master log lacks the PIPELINE_DONE
# marker (FORCE=1 overrides - know what you are doing).
#
# Steps:
#   1. Guards: pipeline done, NDK r26b present, generated sources present.
#   2. Capture-host fixes (0001-non-vita-build-fixes): on the live tree
#      these are the developer's own uncommitted edits (KNOWN_ISSUES #12)
#      - verified by marker and skipped when present; applied from the
#      mirrored patch dir only on a fresh checkout.
#   3. Android-integration series (D7 English-only host menu, SCUS-97472
#      database entry, D18 graceful quit, D18 SOTC_BOOT_ELF): marker-based,
#      idempotent.
#   4. Fresh configure of /root/sotc/build-android with the NDK toolchain
#      (recipe from the driver's cmd_configure + the live CMake's Android
#      branches; NEXT_STEPS M4, TECHNICAL_DECISIONS D18-D20).
#
# PS2X_DEFAULT_BOOT_ELF is deliberately left unset: a user-installed game's
# private path depends on the device user. Patch 0004 reads SOTC_BOOT_ELF at
# run time and GameNativeActivity publishes it before the runner loads.
#
# Afterwards build with: build-android-runner.sh
# Log: /root/sotc/logs/configure-android.log
set -euo pipefail

SOTC=/root/sotc
CELOSIA=/root/celosia
NDK=/root/android-ndk-r26b
BUILD_ANDROID=$SOTC/build-android
PIPELINE_LOG=$SOTC/logs/pipeline-remaining.log
LOG=$SOTC/logs/configure-android.log

log() { printf '%s [configure-android] %s\n' "$(date '+%F %T')" "$*" | tee -a "$LOG"; }
die() { log "ABORT: $*"; exit 1; }

mkdir -p "$SOTC/logs"
log "===== configure-android start ====="

# --- 1. Guards --------------------------------------------------------------
if pgrep -f pipeline-remaining >/dev/null 2>&1; then
    die "pipeline-remaining.sh is still running (check $PIPELINE_LOG)"
fi
if ! grep -q PIPELINE_DONE "$PIPELINE_LOG" 2>/dev/null; then
    if [ "${FORCE:-0}" != "1" ]; then
        die "pipeline did not finish (no PIPELINE_DONE in $PIPELINE_LOG); use FORCE=1 to override"
    fi
    log "FORCE=1: overriding the PIPELINE_DONE check"
fi
[ -f "$NDK/source.properties" ] || die "NDK missing: $NDK (run install-ndk.sh)"
[ -n "$(find "$SOTC/build/runner" -maxdepth 1 -name '*.cpp' -print -quit 2>/dev/null)" ] || die "no recompiled runner sources in $SOTC/build/runner"
[ -d "$SOTC/build/vu1rec" ] || die "no $SOTC/build/vu1rec (VU1 stage not run)"
[ -x "$SOTC/patches-android-integration/apply-patches.sh" ] || die "missing $SOTC/patches-android-integration/ (mirror from tools/android-integration/patches/)"
[ -x "$SOTC/patches-capture-host/apply-patches.sh" ] || die "missing $SOTC/patches-capture-host/ (mirror from tools/capture-host/patches/)"

# --- 2. Capture-host fixes: verify-or-apply ---------------------------------
if grep -q vf0u "$CELOSIA/game/overrides/coll125.h" 2>/dev/null && \
   grep -q PS2X_VU_NEON_A32 "$CELOSIA/game/overrides/decomp_collision.cpp" 2>/dev/null; then
    log "capture-host fixes already present in the live tree (developer edits, KNOWN_ISSUES #12) - skipping that series"
else
    log "capture-host fix markers absent - applying the series (fresh-checkout case)"
    bash "$SOTC/patches-capture-host/apply-patches.sh" "$CELOSIA" 2>&1 | tee -a "$LOG"
fi

# --- 3. Android-integration series (idempotent, marker-based) ---------------
log "applying the android-integration patch series"
bash "$SOTC/patches-android-integration/apply-patches.sh" "$CELOSIA" 2>&1 | tee -a "$LOG"

# --- 4. Configure -------------------------------------------------------------
log "configuring $BUILD_ANDROID (NDK r26b, arm64-v8a, android-28 = app minSdk)"

# Reuse the raylib 5.5 source tree the capture build already fetched, so the
# Android configure is offline-safe and compiles the exact same code. Only the
# host build's source tree is shared; objects stay per-build-directory.
EXTRA_CMAKE_ARGS=()
RAYLIB_SRC=$SOTC/build/cmake/_deps/raylib-src
if [ -d "$RAYLIB_SRC/src" ]; then
    log "reusing raylib source: $RAYLIB_SRC"
    EXTRA_CMAKE_ARGS+=("-DFETCHCONTENT_SOURCE_DIR_RAYLIB=$RAYLIB_SRC")
fi

if ! cmake -S "$CELOSIA/PS2Recomp" -B "$BUILD_ANDROID" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-28 \
    -DANDROID_STL=c++_static \
    -DPS2X_BUILD_STUDIO=OFF -DPS2X_BUILD_TEST=OFF -DPS2X_BUILD_ANALYZER=OFF \
    -DPS2X_ENABLE_AGRESSIVE_LOGS=OFF \
    -DPS2X_ENABLE_RUNNER_UNITY_BUILD=OFF \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    "-DCMAKE_CXX_FLAGS=-I/root/sotc/android-shim" \
    "-DPS2X_RUNNER_DIRS=$SOTC/build/runner;$SOTC/build/vu1rec;$CELOSIA/game/overrides" \
    "${EXTRA_CMAKE_ARGS[@]}" \
    >>"$LOG" 2>&1; then
    tail -40 "$LOG"
    die "configure failed (full log: $LOG) - if it is an NDK-toolchain/CMake-4 clash, see the D20 fallback (NDK 28.2)"
fi

log "CONFIGURE_OK"
log "build next: bash $SOTC/build-android-runner.sh"
log "expected artifact: $BUILD_ANDROID/ps2xRuntime/libps2EntryRunner.so (SHARED per the ANDROID branch)"
