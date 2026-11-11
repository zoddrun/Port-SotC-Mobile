#!/usr/bin/env bash
# install-ndk: fetch the Linux Android NDK r26b (26.1.10909125) into
# /root/android-ndk-r26b for the Milestone 4 arm64-v8a cross-compile (D20).
#
# Why r26b: app/build.gradle pins ndkVersion 26.1.10909125 - one NDK version
# across the project (the NDK CMake toolchain defaults to c++_static, so the
# runner ships without a shared-STL dependency either way). WSL has no Android
# SDK/NDK, and the Windows-side NDK cannot drive a Linux CMake build.
# Fallback if the r26b toolchain trips CMake 4.2 at configure: the scaffold's
# 28.2.13676358 (see D20 in docs/agent/TECHNICAL_DECISIONS.md).
#
# Safe to run while the x86 pipeline builds: network + disk only, no source
# tree changes. Idempotent: exits cleanly if the NDK is already present.
#
# Verifies: zip integrity (unzip -t), exact Pkg.Revision, working clang.
# Log: /root/sotc/logs/install-ndk.log
set -euo pipefail

NDK_DIR=/root/android-ndk-r26b
ZIP=/root/android-ndk-r26b-linux.zip
URL=https://dl.google.com/android/repository/android-ndk-r26b-linux.zip
LOG=/root/sotc/logs/install-ndk.log

log() { printf '%s [install-ndk] %s\n' "$(date '+%F %T')" "$*" | tee -a "$LOG"; }
die() { log "ABORT: $*"; exit 1; }

mkdir -p /root/sotc/logs

if [ -f "$NDK_DIR/source.properties" ]; then
    log "NDK already present: $NDK_DIR ($(stat -c '%s bytes' "$NDK_DIR/source.properties"))"
    sed -n 's/^Pkg.Revision[ =]*/Pkg.Revision:/p' "$NDK_DIR/source.properties" | tee -a "$LOG"
    exit 0
fi

command -v curl >/dev/null 2>&1 || die "curl not found (apt install curl)"
command -v unzip >/dev/null 2>&1 || die "unzip not found (apt install unzip)"

log "downloading $URL"
curl -fL --retry 3 -o "$ZIP" "$URL" >>"$LOG" 2>&1 || die "download failed"
log "downloaded $(stat -c '%s' "$ZIP") bytes; testing archive integrity"
unzip -t -q "$ZIP" >/dev/null 2>&1 || die "zip integrity test failed"

log "extracting to /root"
unzip -q "$ZIP" -d /root || die "unzip failed"
[ -f "$NDK_DIR/source.properties" ] || die "expected $NDK_DIR after extraction"

rev=$(sed -n 's/^Pkg.Revision[ =]*//p' "$NDK_DIR/source.properties" | tr -d '[:space:]')
[ "$rev" = "26.1.10909125" ] || die "unexpected Pkg.Revision: $rev"
log "Pkg.Revision OK: $rev"

"$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --version 2>&1 | head -2 | tee -a "$LOG"

rm -f "$ZIP"
log "NDK_READY: $NDK_DIR"
