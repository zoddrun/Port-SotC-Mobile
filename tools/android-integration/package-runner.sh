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
set -euo pipefail

SOURCE=${1:-/root/sotc/build-android/ps2xRuntime/libps2EntryRunner.so}
LIBRARY=libps2EntryRunner.so

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

if ! readelf --dyn-syms --wide "$SOURCE" | grep -q 'ANativeActivity_onCreate'; then
    echo "error: $SOURCE does not export ANativeActivity_onCreate" >&2
    echo "       it is not a raylib NativeActivity library" >&2
    exit 1
fi

DEST="$REPO/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST"
cp "$SOURCE" "$DEST/$LIBRARY"

echo "PACKAGED: $DEST/$LIBRARY"
echo "SIZE:     $(stat -c '%s bytes' "$DEST/$LIBRARY")"
echo "SHA256:   $(sha256sum "$DEST/$LIBRARY" | cut -d' ' -f1)"
echo "NEEDED:"
readelf -d "$DEST/$LIBRARY" | sed -n 's/.*Shared library: \[\(.*\)\]/  \1/p'
