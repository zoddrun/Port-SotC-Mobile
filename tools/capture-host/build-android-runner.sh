#!/usr/bin/env bash
# build-android-runner: build the Android arm64-v8a ps2EntryRunner shared
# library in /root/sotc/build-android (run configure-android.sh first).
#
# Same conventions as the x86 rebuild-runner.sh: -j2 under the 1.7 GiB RAM
# limit (B2/D15 - swap must be active), fresh log per attempt (never a
# shared name), BUILD_OK/BUILD_FAILED + artifact presence markers.
#
# Expected artifact: /root/sotc/build-android/ps2xRuntime/libps2EntryRunner.so
set -uo pipefail

BUILD=/root/sotc/build-android
LOGS=/root/sotc/logs
STAMP=$(date '+%Y%m%d-%H%M%S')
LOG=$LOGS/build-android-ps2EntryRunner-$STAMP.log
JOBS=${JOBS:-2}

mkdir -p "$LOGS"

if [ ! -f "$BUILD/build.ninja" ]; then
    echo "error: $BUILD not configured (run configure-android.sh first)" >&2
    exit 1
fi

echo "building ps2EntryRunner (arm64-v8a, -j$JOBS; log: $LOG)"
if cmake --build "$BUILD" --target ps2EntryRunner -j "$JOBS" >"$LOG" 2>&1; then
    echo BUILD_OK
else
    echo BUILD_FAILED
    tail -25 "$LOG"
    exit 1
fi

SO=$BUILD/ps2xRuntime/libps2EntryRunner.so
if [ -f "$SO" ]; then
    echo "RUNNER_SO_PRESENT: $SO ($(stat -c '%s bytes' "$SO"))"
    file "$SO" | head -1
else
    echo "RUNNER_SO_MISSING: $SO"
    exit 1
fi
