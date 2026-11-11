#!/usr/bin/env bash
# pipeline-remaining: continue the celosia pipeline from the current state
# (stage_boot runner built as attempt 6) through the game stages, unattended.
#
# Why not `celosia setup`: cmd_setup re-runs stage_boot, and the driver's
# recompile() always deletes and re-copies build/runner/* (fresh mtimes, so
# ninja rebuilds every object), which would discard attempt 6's multi-hour
# stage_boot build. This script runs only the steps cmd_setup sequences AFTER
# stage_boot, in the same order:
#   capture KERNEL -> recompile (stage_game) -> capture MANAGER -> recompile
#   -> capture GAMECORE -> recompile -> vu1
#
# Logs:
#   master      /root/sotc/logs/pipeline-remaining.log (this script)
#   captures    /root/sotc/logs/capture-*.log (capture-module.sh)
#   driver      /root/celosia/logs/{link,configure,build-*}.log (celosia),
#               copied to /root/sotc/logs/stagegame-*.log after each stage
#   verify pass /root/sotc/logs/build-ps2EntryRunner-verify.log
set -uo pipefail   # not -e: each stage is checked explicitly so the abort names the failing step

SOTC=/root/sotc
BUILD=$SOTC/build
CMAKE_DIR=$BUILD/cmake
RUNNER=$CMAKE_DIR/ps2xRuntime/ps2EntryRunner
LOG=$SOTC/logs/pipeline-remaining.log

log() { printf '%s [pipeline] %s\n' "$(date '+%F %T')" "$*" | tee -a "$LOG"; }
die() { log "ABORT: $*"; exit 1; }

mkdir -p "$SOTC/logs"
log "===== pipeline-remaining start ====="

# --- 0. Host prerequisites (per-boot resets, KNOWN_ISSUES #10) ------------
if ! swapon --show=NAME --noheadings 2>/dev/null | grep -q swapfile; then
    log "8 GB swapfile not active; activating"
    swapon /root/swapfile || die "could not activate /root/swapfile (needed for the LTO link)"
fi

display_ok() { pgrep -x Xvfb >/dev/null 2>&1 && [ -S /tmp/.X11-unix/X99 ]; }
if ! display_ok; then
    log "Xvfb :99 not healthy; running fix-x11.sh and restarting it"
    bash "$SOTC/fix-x11.sh" >>"$LOG" 2>&1 || true
    pkill -x Xvfb 2>/dev/null || true
    sleep 1
    Xvfb :99 -screen 0 1024x768x24 -nolisten tcp >>"$LOG" 2>&1 &
    sleep 2
    display_ok || die "Xvfb :99 could not be started; run fix-x11.sh manually"
fi
export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1

# --- 1. Wait for attempt 6 to finish, then verify the runner target -------
# The running build is ninja (attempt 6). Wait for it to exit first: a second
# ninja on the same build dir would race it. Then run the build target once
# more: success means an instant no-op; a failed attempt 6 means this resumes
# it (a transient link OOM can pass on retry) or fails for real and aborts.
log "waiting for the attempt 6 ninja to exit"
waited=0
while pgrep -x ninja >/dev/null 2>&1; do
    sleep 30
    waited=$((waited + 30))
    [ "$waited" -ge 10800 ] && die "attempt 6 still building after 3 h"
done
log "no ninja running (waited ${waited}s); verifying the runner target"
VERIFY_LOG=$SOTC/logs/build-ps2EntryRunner-verify.log
if ! (cd "$CMAKE_DIR" && cmake --build . --target ps2EntryRunner -j 2 >"$VERIFY_LOG" 2>&1); then
    tail -25 "$VERIFY_LOG" | tee -a "$LOG"
    die "ps2EntryRunner build failed (verify pass; see $VERIFY_LOG)"
fi
[ -x "$RUNNER" ] || die "runner binary missing after successful build"
log "runner ready: $RUNNER ($(stat -c '%s bytes' "$RUNNER"))"

# --- 2. capture + stage_game rounds (driver order: KERNEL MANAGER GAMECORE)
# cmd_recompile refuses to run before $WORK/game exists (its ordering guard);
# stage_game itself creates that directory, so pre-creating it is the same
# state the driver's setup flow would have.
mkdir -p "$BUILD/recomp/game"
for m in KERNEL MANAGER GAMECORE; do
    low=$(echo "$m" | tr '[:upper:]' '[:lower:]')
    log "=== capture $m ==="
    if ! bash "$SOTC/capture-module.sh" "$m" >>"$LOG" 2>&1; then
        log "capture $m not reached without input; retrying with PS2X_INPUT_SCRIPT progression"
        if ! PS2X_INPUT_SCRIPT="11:DOWN:0.3;12:CROSS:0.3" bash "$SOTC/capture-module.sh" "$m" >>"$LOG" 2>&1; then
            tail -25 "$SOTC/logs/capture-$low.log" 2>/dev/null | tee -a "$LOG"
            die "capture $m failed twice (log: $SOTC/logs/capture-$low.log)"
        fi
    fi
    log "=== stage_game after $m ==="
    if ! (cd "$SOTC" && ./celosia-env.sh recompile >>"$LOG" 2>&1); then
        cp -f /root/celosia/logs/build-ps2EntryRunner.log "$SOTC/logs/stagegame-$low-build-failed.log" 2>/dev/null || true
        tail -30 /root/celosia/logs/build-ps2EntryRunner.log 2>/dev/null | tee -a "$LOG"
        die "stage_game after $m failed (driver logs in /root/celosia/logs/)"
    fi
    cp -f /root/celosia/logs/build-ps2EntryRunner.log "$SOTC/logs/stagegame-$low-build.log" 2>/dev/null || true
    cp -f /root/celosia/logs/link.log "$SOTC/logs/stagegame-$low-link.log" 2>/dev/null || true
    log "stage_game after $m complete; runner rebuilt ($(stat -c '%s bytes' "$RUNNER"))"
done

# --- 3. VU1 microcode collection + codegen + final rebuild ----------------
log "=== VU1 (autotest if needed, codegen, rebuild) ==="
if ! (cd "$SOTC" && ./celosia-env.sh vu1 >>"$LOG" 2>&1); then
    tail -25 /root/celosia/logs/autotest.log 2>/dev/null | tee -a "$LOG"
    die "vu1 stage failed"
fi

log "PIPELINE_DONE"
log "next: Milestone 4 (apply both patch series, then the NDK cross-configure; see docs/agent/NEXT_STEPS.md)"
