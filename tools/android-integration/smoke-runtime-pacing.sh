#!/usr/bin/env bash
# Host x86 smoke assertions for patch 0009. No device required.
#
# Rebuilds the patched celosia host runner incrementally, runs three short
# sessions with fixed environments, and checks the pacing/logging contract from
# the captured stdout. This proves parsing, pacing and adaptive decisions
# without any rendering evidence on a phone; it is NOT a gameplay verification.
#
# Usage: bash tools/android-integration/smoke-runtime-pacing.sh
# Environment: CELOSIA (default /root/celosia), SOTC (default /root/sotc)
set -uo pipefail

ROOT=${CELOSIA:-/root/celosia}
SOTC=${SOTC:-/root/sotc}
BUILD=$SOTC/build/cmake
RUNNER=$BUILD/ps2xRuntime/ps2EntryRunner
DISC=$SOTC/disc/SCUS_974.72
LOGDIR=$SOTC/logs/smoke-0009
MARKER='app-authoritative pacing on Android'

mkdir -p "$LOGDIR"

echo '=== ensure Xvfb display ==='
# xdpyinfo is not installed in this WSL image. Xvfb listens on the Linux
# abstract socket @/tmp/.X11-unix/X99, which works even though WSLg mounts
# /tmp/.X11-unix read-only. Check the server process, not an X client.
if ! ps -eo args 2>/dev/null | grep -q '[X]vfb :99'; then
    Xvfb :99 -screen 0 1280x720x24 >"$LOGDIR/xvfb.log" 2>&1 &
    sleep 2
fi
if ps -eo args 2>/dev/null | grep -q '[X]vfb :99'; then
    echo DISPLAY_OK
else
    echo DISPLAY_FAILED
    exit 1
fi

echo '=== patch 0009 present in host source ==='
if ! grep -qF "$MARKER" "$ROOT/PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp"; then
    echo 'PATCH_MARKER_MISSING - apply patch 0009 to the host source first'
    exit 1
fi
echo PATCH_MARKER_OK

echo '=== incremental host rebuild ==='
cd "$BUILD" || exit 1
if cmake --build . --target ps2EntryRunner -j 2 >"$LOGDIR/host-rebuild.log" 2>&1; then
    echo HOST_BUILD_OK
else
    echo HOST_BUILD_FAILED
    tail -30 "$LOGDIR/host-rebuild.log"
    exit 1
fi

run_case() {
    local name=$1 limit=$2 adaptive=$3 scale=$4
    local log="$LOGDIR/$name.log"
    echo "=== case $name: limit=$limit adaptive=$adaptive scale=$scale ==="
    (cd "$ROOT" && \
        DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 \
        SOTC_FRAME_LIMIT="$limit" \
        SOTC_ADAPTIVE_RESOLUTION="$adaptive" \
        SOTC_RENDER_SCALE="$scale" \
        SOTC_PERF_OVERLAY=0 \
        PS2X_STATS=1 \
        timeout 30s "$RUNNER" "$DISC") >"$log" 2>&1
    echo "exit=$? log=$log lines=$(wc -l <"$log")"
}

run_case limit30 30 0 0.500
run_case limit60 60 1 1.000
run_case limit0 0 0 1.000

echo '=== assertions ==='
fail=0
check() {
    local label=$1 file=$2 pattern=$3 expected=$4
    local count
    count=$(grep -cF "$pattern" "$file" 2>/dev/null || true)
    if [ "$expected" = yes ]; then
        if [ "$count" -gt 0 ]; then
            echo "PASS  $label ($count)"
        else
            echo "FAIL  $label missing: $pattern"
            fail=1
        fi
    else
        if [ "$count" -eq 0 ]; then
            echo "PASS  $label absent ($pattern)"
        else
            echo "FAIL  $label present: $pattern ($count)"
            fail=1
        fi
    fi
}

# raylib's SetTargetFPS logs the target once at the initial SetTargetFPS(60)
# and again when patch 0009 changes it. Limit 30 must show 33.333 ms.
check '30 target time' "$LOGDIR/limit30.log" 'TIMER: Target time per frame: 33.333 milliseconds' yes
check '60 target time' "$LOGDIR/limit60.log" 'TIMER: Target time per frame: 16.667 milliseconds' yes
check '30 pace limit' "$LOGDIR/limit30.log" '[pace] limit=30 ' yes
check '60 pace limit' "$LOGDIR/limit60.log" '[pace] limit=60 ' yes
check '0 pace limit' "$LOGDIR/limit0.log" '[pace] limit=0 ' yes
# The host desktop-GL build has no perf-publish source (frameMs stays 0), so
# adapt() cannot emit its "[scale-adaptive] on:" activation line here. The
# host-verifiable contract is the accepted adaptive flag in the [pace] record.
# Actual scale changes remain device-only and are NOT claimed by this smoke.
check 'adaptive case accepts adaptive=1' "$LOGDIR/limit60.log" 'adaptive=1 frameMs' yes
check 'disabled case reports adaptive=0' "$LOGDIR/limit30.log" 'adaptive=0 frameMs' yes
check 'uncapped case reports adaptive=0' "$LOGDIR/limit0.log" 'adaptive=0 frameMs' yes
check 'no host Frame stats in gameplay' "$LOGDIR/limit30.log" 'Frame stats:' no

echo '--- pace lines ---'
grep -hF '[pace]' "$LOGDIR"/limit*.log | head -20
echo '--- timer lines ---'
grep -hF 'TIMER: Target time per frame' "$LOGDIR"/limit*.log | head -20

if [ "$fail" -eq 0 ]; then
    echo HOST_SMOKE_0009_OK
else
    echo HOST_SMOKE_0009_FAILED
fi
exit "$fail"
