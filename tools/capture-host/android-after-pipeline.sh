#!/usr/bin/env bash
# android-after-pipeline: unattended Milestone 4 handoff.
#
# Waits for the capture pipeline to finish (pipeline-remaining.sh exits and
# PIPELINE_DONE appears in its master log), then runs the ARM64 configure and
# the ps2EntryRunner build in sequence. Nothing here touches the pipeline while
# it runs: the wait is a 60 s poll of pgrep and the log, and configure-android.sh
# has its own guards.
#
# Why: the remaining pipeline stages are hours of unattended work on a 4-core /
# 1.7 GiB host, and the ARM64 build cannot start before PIPELINE_DONE. This
# script turns the end of the pipeline into the start of Milestone 4 without
# needing a human (or an agent) to be awake at that moment.
#
# Markers in /root/sotc/logs/android-after-pipeline.out:
#   ANDROID_PIPELINE_START   configure-android.sh is starting
#   ANDROID_CONFIGURE_OK     configure succeeded
#   ANDROID_BUILD_OK         libps2EntryRunner.so exists
#   ANDROID_PIPELINE_FAILED  one of the steps failed (see the logs it names)
#
# Logs: logs/android-after-pipeline.out (this script)
#       logs/configure-android.log
#       logs/build-android-ps2EntryRunner-<stamp>.log
set -uo pipefail

SOTC=/root/sotc
PIPELINE_LOG=$SOTC/logs/pipeline-remaining.log
OUT=$SOTC/logs/android-after-pipeline.out
POLL=${POLL:-60}
MAX_WAIT=${MAX_WAIT:-172800} # 48 h, then give up instead of waiting forever

log() { printf '%s [android-after-pipeline] %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ANDROID_PIPELINE_FAILED: $*"; exit 1; }

mkdir -p "$SOTC/logs"
log "waiting for pipeline-remaining.sh to finish (poll ${POLL}s, max ${MAX_WAIT}s)"

waited=0
while pgrep -f pipeline-remaining >/dev/null 2>&1; do
    if [ "$waited" -ge "$MAX_WAIT" ]; then
        die "pipeline still running after ${MAX_WAIT}s (check $PIPELINE_LOG)"
    fi
    sleep "$POLL"
    waited=$((waited + POLL))
    if [ $((waited % 1800)) -eq 0 ]; then
        log "still waiting: ${waited}s elapsed, $(grep -c '==> ' "$PIPELINE_LOG" 2>/dev/null || echo 0) stage markers in the log"
    fi
done

log "pipeline process is gone after ${waited}s; checking the completion marker"
if ! grep -q PIPELINE_DONE "$PIPELINE_LOG" 2>/dev/null; then
    die "pipeline exited without PIPELINE_DONE in $PIPELINE_LOG"
fi

log "ANDROID_PIPELINE_START: configure-android.sh"
if ! bash "$SOTC/configure-android.sh" >>"$OUT" 2>&1; then
    die "configure-android.sh failed (log: $SOTC/logs/configure-android.log)"
fi
if ! grep -q CONFIGURE_OK "$SOTC/logs/configure-android.log"; then
    die "configure-android.sh exited without CONFIGURE_OK (log: $SOTC/logs/configure-android.log)"
fi
log "ANDROID_CONFIGURE_OK"

log "building ps2EntryRunner for arm64-v8a"
if ! bash "$SOTC/build-android-runner.sh" >>"$OUT" 2>&1; then
    die "build-android-runner.sh failed (log: the newest $SOTC/logs/build-android-ps2EntryRunner-*.log)"
fi
SO=$SOTC/build-android/ps2xRuntime/libps2EntryRunner.so
[ -f "$SO" ] || die "build reported success but $SO is missing"
log "ANDROID_BUILD_OK: $SO ($(stat -c '%s bytes' "$SO"))"
