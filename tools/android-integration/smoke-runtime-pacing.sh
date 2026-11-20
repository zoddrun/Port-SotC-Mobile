#!/usr/bin/env bash
# Host x86 smoke assertions for patches 0009, 0010 and 0011. No device required.
#
# Rebuilds the patched celosia host runner incrementally, runs short sessions
# with fixed environments, and checks the pacing/logging contract from the
# captured stdout. Patch 0011 gets its own session and its own assertions, so
# the original 13 pacing checks are untouched. This proves parsing, pacing,
# shader-module compilation and telemetry; it is NOT rendering or gameplay
# verification on a phone.
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

# Patch 0011: the runner reads the shader pack from the environment. WSL can
# read the repo's asset directory, so use the real ES 1.00 pack when present.
# If it is absent, the fallback assertion is explicitly the no-pack path.
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
HD_PACK="$REPO_ROOT/app/src/main/assets/shaders"
if [ -f "$HD_PACK/sotc_hd_es2.vert" ] && [ -f "$HD_PACK/sotc_hd_es2.frag" ]; then
    HD_SHADER_MODE=loaded
    echo "HD_PACK_OK $HD_PACK"
else
    HD_SHADER_MODE=disabled
    HD_PACK=''
    echo 'HD_PACK_UNAVAILABLE - asserting disabled no-pack fallback only'
fi

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
if [ "${SOTC_SKIP_HOST_REBUILD:-0}" = 1 ]; then
    # The changed objects were compiled and the executable relinked with the
    # exact commands from ninja -t commands. This is used when the local Ninja
    # build log lost its command hashes and would otherwise recompile the whole
    # 9,000-object capture tree for no source change.
    echo HOST_REBUILD_SKIPPED_MANUAL_LINK
else
    if cmake --build . --target ps2EntryRunner -j 2 >"$LOGDIR/host-rebuild.log" 2>&1; then
        echo HOST_BUILD_OK
    else
        echo HOST_BUILD_FAILED
        tail -30 "$LOGDIR/host-rebuild.log"
        exit 1
    fi
fi

run_case() {
    local name=$1 limit=$2 adaptive=$3 scale=$4 duration=${5:-30} pack=${6:-}
    local log="$LOGDIR/$name.log"
    echo "=== case $name: limit=$limit adaptive=$adaptive scale=$scale duration=${duration}s pack=${pack:-none} ==="
    (cd "$ROOT" && \
        unset SOTC_HD_SHADER_PACK SOTC_HD_SHADER_PRESET SOTC_HD_SHADER_VERSION && \
        if [ -n "$pack" ]; then
            export SOTC_HD_SHADER_PACK="$pack"
            export SOTC_HD_SHADER_PRESET=balanced
            export SOTC_HD_SHADER_VERSION=1.0.0
        fi && \
        DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 \
        SOTC_FRAME_LIMIT="$limit" \
        SOTC_ADAPTIVE_RESOLUTION="$adaptive" \
        SOTC_RENDER_SCALE="$scale" \
        SOTC_PERF_OVERLAY=0 \
        PS2X_STATS=1 \
        timeout "${duration}s" "$RUNNER" "$DISC") >"$log" 2>&1
    echo "exit=$? log=$log lines=$(wc -l <"$log")"
}

run_case limit30 30 0 0.500
# Software GL at scale 1.000 is much slower than the reduced-scale cases. Both
# the adaptive case and the shader case use the full scale, so give them enough
# wall-clock time to reach at least two five-second telemetry records. These
# durations are harness settings only; the 13 assertions below are unchanged.
run_case limit60 60 1 1.000 75
run_case limit0 0 0 1.000 60
# Patch 0011 runs in its own session so no shader environment leaks into the
# three pacing cases above.
if [ "$HD_SHADER_MODE" = loaded ]; then
    run_case hdshader 60 0 1.000 90 "$HD_PACK"
else
    run_case hdshader 60 0 1.000 60
fi

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

# A startup [pace] record can legitimately report frameMs=0.000 before the
# first rendered frame. The contract is that a live measurement appears at
# least once, not that every record is already populated.
check_positive_frame_ms() {
    local label=$1 file=$2
    local count
    count=$(grep -Ec 'frameMs=(0\.[1-9][0-9]*|[1-9][0-9]*\.)' "$file" 2>/dev/null || true)
    if [ "$count" -gt 0 ]; then
        echo "PASS  $label ($count)"
    else
        echo "FAIL  $label missing positive frameMs"
        fail=1
    fi
}

# Patch 0011 telemetry repeats with the five-second [pace] record; count only
# the shader lines, never the nearby [stats] lines.
check_min() {
    local label=$1 file=$2 pattern=$3 minimum=$4
    local count
    count=$(grep -cF "$pattern" "$file" 2>/dev/null || true)
    if [ "$count" -ge "$minimum" ]; then
        echo "PASS  $label ($count >= $minimum)"
    else
        echo "FAIL  $label count $count < $minimum: $pattern"
        fail=1
    fi
}

# raylib's SetTargetFPS logs the target once at the initial SetTargetFPS(60)
# and again when patch 0009 changes it. Limit 30 must show 33.333 ms.
check '30 target time' "$LOGDIR/limit30.log" 'TIMER: Target time per frame: 33.333 milliseconds' yes
check '60 target time' "$LOGDIR/limit60.log" 'TIMER: Target time per frame: 16.667 milliseconds' yes
check '30 pace limit' "$LOGDIR/limit30.log" '[pace] limit=30 ' yes
check '60 pace limit' "$LOGDIR/limit60.log" '[pace] limit=60 ' yes
check '0 pace limit' "$LOGDIR/limit0.log" '[pace] limit=0 ' yes
check 'adaptive case accepts adaptive=1' "$LOGDIR/limit60.log" 'adaptive=1 frameMs' yes
check 'disabled case reports adaptive=0' "$LOGDIR/limit30.log" 'adaptive=0 frameMs' yes
check 'uncapped case reports adaptive=0' "$LOGDIR/limit0.log" 'adaptive=0 frameMs' yes
# Patch 0010 feeds adapt() from raylib's real frame time when the optional
# perf-statistics overlay is off. That is the shipped default, so this smoke
# can now prove the activation and at least one actual scale change without a
# device. It still proves nothing about gameplay presentation or GPU output.
check_positive_frame_ms 'live frame time in pace record' "$LOGDIR/limit60.log"
check 'adaptive activation recorded' "$LOGDIR/limit60.log" '[scale-adaptive] on:' yes
check 'adaptive scale actually changed' "$LOGDIR/limit60.log" '[scale-adaptive] frame' yes
check 'disabled adaptive emits no scale change' "$LOGDIR/limit30.log" '[scale-adaptive]' no
check 'no host Frame stats in gameplay' "$LOGDIR/limit30.log" 'Frame stats:' no

# Patch 0011 assertions are independent of the 13 pacing checks above. The
# [pace] prefix and field order are still checked unchanged there.
echo '--- hd-shader assertions ---'
# One assertion covers source registration and the exact shipped defaults:
# ultra bloom 0.35 and balanced bloom 0.26. Writing a sentinel keeps the
# assertion count unchanged while still failing if any of the three lines is
# absent from the real patched source.
HD_SOURCE=$ROOT/PS2Recomp/ps2xRuntime/src/lib/sotc_hd_shader.cpp
{
    grep -F 'src/lib/sotc_hd_shader.cpp' "$ROOT/PS2Recomp/ps2xRuntime/CMakeLists.txt" || true
    grep -F 'return {0.50f, 0.35f, 1.0f};' "$HD_SOURCE" || true
    grep -F 'return {0.35f, 0.26f, 1.0f};' "$HD_SOURCE" || true
} >"$LOGDIR/hd-source-evidence.txt"
if [ "$(wc -l <"$LOGDIR/hd-source-evidence.txt")" -eq 3 ]; then
    echo 'HD_SOURCE_OK' >"$LOGDIR/hd-source-marker.txt"
else
    : >"$LOGDIR/hd-source-marker.txt"
fi
check 'hd shader source and exact preset defaults registered' \
    "$LOGDIR/hd-source-marker.txt" 'HD_SOURCE_OK' yes
nm -C "$RUNNER" >"$LOGDIR/runner-symbols.txt" 2>/dev/null || true
check 'hd shader linked into runner' "$LOGDIR/runner-symbols.txt" 'sotc_hd_shader::initialize()' yes
if [ "$HD_SHADER_MODE" = loaded ]; then
    check 'hd shader compiled and loaded' "$LOGDIR/hdshader.log" '[hd-shader] enabled=1 compiled=1 loaded=1 backend=' yes
    check_min 'hd shader telemetry cadence' "$LOGDIR/hdshader.log" '[hd-shader] enabled=1' 2
    check 'hd shader reports chosen preset' "$LOGDIR/hdshader.log" 'preset=balanced version=1.0.0' yes
    check 'hd shader failure absent' "$LOGDIR/hdshader.log" '[hd-shader] enabled=0' no
    check 'pace unchanged beside shader' "$LOGDIR/hdshader.log" '[pace] limit=60 ' yes
else
    check 'hd shader honest no-pack fallback' "$LOGDIR/hdshader.log" '[hd-shader] enabled=0 compiled=0 loaded=0 reason=no-pack' yes
    check 'pace unchanged without shader' "$LOGDIR/hdshader.log" '[pace] limit=60 ' yes
    echo 'SKIP  hd shader compile assertion (pack path unavailable)'
fi

echo '--- pace lines ---'
grep -hF '[pace]' "$LOGDIR"/limit*.log | head -20
echo '--- timer lines ---'
grep -hF 'TIMER: Target time per frame' "$LOGDIR"/limit*.log | head -20
echo '--- hd-shader lines ---'
grep -hF '[hd-shader]' "$LOGDIR"/limit*.log "$LOGDIR"/hdshader.log | head -20

if [ "$fail" -eq 0 ]; then
    echo HOST_SMOKE_0009_OK
else
    echo HOST_SMOKE_0009_FAILED
fi
exit "$fail"
