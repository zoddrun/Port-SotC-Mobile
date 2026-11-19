#!/usr/bin/env bash
# package-runner: verify the Android build of the recompiled runtime and copy
# it into the app's jniLibs directory so the APK can load it.
#
# Usage:
#   wsl -e bash tools/android-integration/package-runner.sh [path-to-so]
#
# Default source: /root/sotc/build-android/ps2xRuntime/libps2EntryRunner.so
# The destination is app/src/main/jniLibs/arm64-v8a/libps2EntryRunner.so in
# the repository that contains this script. The library itself is gitignored:
# it is a derived build artifact and is never committed.
#
# The build is linked with debug info (777 MB for the Milestone 4 library), so
# the copy is stripped here with --strip-unneeded: that drops .symtab and the
# DWARF sections while keeping .dynsym, so ANativeActivity_onCreate and the
# touch overlay entry points stay exported. The unstripped library in the
# build tree is left untouched. Set SOTC_STRIP=0 to package it verbatim (for
# offline symbolisation), or ANDROID_NDK to point at another NDK.
set -euo pipefail

SOURCE=${1:-/root/sotc/build-android/ps2xRuntime/libps2EntryRunner.so}
LIBRARY=libps2EntryRunner.so
NDK=${ANDROID_NDK:-/root/android-ndk-r26b}
STRIP=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip
SOTC_STRIP=${SOTC_STRIP:-1}

if [ ! -f "$SOURCE" ]; then
    echo "error: runner library not found: $SOURCE" >&2
    echo "       run tools/capture-host/configure-android.sh and" >&2
    echo "       tools/capture-host/build-android-runner.sh first" >&2
    exit 1
fi

REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null || true)
if [ -z "$REPO" ]; then
    echo "error: not inside the Port-SotC-Mobile repository" >&2
    echo "       run this script from the repository (or set REPO and DEST)" >&2
    exit 1
fi

HEADER=$(readelf -h "$SOURCE" 2>/dev/null || true)
if ! grep -q 'Machine:.*AArch64' <<<"$HEADER"; then
    echo "error: $SOURCE is not an AArch64 ELF" >&2
    echo "$HEADER" >&2
    exit 1
fi

if ! grep -q 'ANativeActivity_onCreate' <<<"$(readelf --dyn-syms --wide "$SOURCE")"; then
    echo "error: $SOURCE does not export ANativeActivity_onCreate" >&2
    echo "       it is not a raylib NativeActivity library" >&2
    exit 1
fi

DEST="$REPO/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST"
cp "$SOURCE" "$DEST/$LIBRARY"

SOURCE_SIZE=$(stat -c '%s' "$SOURCE")
PACKAGED_SIZE=$(stat -c '%s' "$DEST/$LIBRARY")

if [ "$SOTC_STRIP" != 0 ]; then
    if [ ! -x "$STRIP" ]; then
        echo "error: llvm-strip not found: $STRIP" >&2
        echo "       set ANDROID_NDK, or SOTC_STRIP=0 to package unstripped" >&2
        exit 1
    fi
    "$STRIP" --strip-unneeded "$DEST/$LIBRARY"
    PACKAGED_SIZE=$(stat -c '%s' "$DEST/$LIBRARY")
fi

echo "PACKAGED: $DEST/$LIBRARY"
echo "SIZE:     $PACKAGED_SIZE bytes (built $SOURCE_SIZE bytes, stripped=$([ "$SOTC_STRIP" != 0 ] && echo yes || echo no))"
echo "SHA256:   $(sha256sum "$DEST/$LIBRARY" | cut -d' ' -f1)"
echo "NEEDED:"
readelf -d "$DEST/$LIBRARY" | sed -n 's/.*Shared library: \[\(.*\)\]/  \1/p'

VERIFY="$(dirname "$0")/verify-runner.sh"
if [ -f "$VERIFY" ]; then
    echo
    bash "$VERIFY" "$DEST/$LIBRARY"
fi
