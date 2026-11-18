#!/usr/bin/env bash
# Inspect a built runner library before it is packaged into the APK.
#
# The Milestone 4 build produces a shared object that the app loads through
# NativeActivity. A successful compile does not prove the artifact is usable:
# it has to be AArch64, it has to export the NativeActivity entry point, it
# must not depend on a C++ runtime the APK does not ship, and its LOAD segments
# have to be aligned for 16 KB page devices. This script checks those
# properties and prints evidence that can be quoted in a release note.
#
# Usage: bash tools/android-integration/verify-runner.sh [path-to-.so]
#
# Defaults: /root/sotc/build-android/ps2xRuntime/libps2EntryRunner.so
# Environment: ANDROID_NDK (default /root/android-ndk-r26b), SOTC (default /root/sotc)
set -uo pipefail

SOTC=${SOTC:-/root/sotc}
NDK=${ANDROID_NDK:-/root/android-ndk-r26b}
TOOLS=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
READELF=$TOOLS/llvm-readelf
NM=$TOOLS/llvm-nm
LIB=${1:-$SOTC/build-android/ps2xRuntime/libps2EntryRunner.so}

fail=0
warn=0
ok()   { printf 'PASS  %s\n' "$*"; }
bad()  { printf 'FAIL  %s\n' "$*"; fail=$((fail + 1)); }
note() { printf 'WARN  %s\n' "$*"; warn=$((warn + 1)); }

if [ ! -f "$READELF" ] || [ ! -x "$NM" ]; then
    printf 'FAIL  NDK tools not found under %s (set ANDROID_NDK)\n' "$TOOLS"
    exit 1
fi
if [ ! -f "$LIB" ]; then
    printf 'FAIL  missing %s\n' "$LIB"
    exit 1
fi

printf 'library  %s\n' "$LIB"
printf 'size     %s bytes\n' "$(stat -c '%s' "$LIB")"
printf 'sha256   %s\n' "$(sha256sum "$LIB" | cut -d' ' -f1)"

header=$("$READELF" -h "$LIB")
if printf '%s\n' "$header" | grep -q 'Class:.*ELF64'; then
    ok 'ELF64'
else
    bad 'not an ELF64 object'
fi
if printf '%s\n' "$header" | grep -q 'Machine:.*AArch64'; then
    ok 'machine AArch64'
else
    bad 'machine is not AArch64'
fi
if printf '%s\n' "$header" | grep -q 'Type:.*DYN'; then
    ok 'shared object (DYN)'
else
    bad 'not a shared object'
fi

# Android 15 devices can run a 16 KB page-size kernel. A library whose LOAD
# segments are aligned to 4 KB cannot be mapped there.
aligns=$("$READELF" -lW "$LIB" | awk '$1 == "LOAD" { print $NF }' | sort -u)
printf 'load_align %s\n' "$(printf '%s' "$aligns" | tr '\n' ' ')"
if [ -z "$aligns" ]; then
    bad 'no LOAD segments found'
else
    too_small=0
    for a in $aligns; do
        value=$((a))
        [ "$value" -lt 16384 ] && too_small=1
    done
    if [ "$too_small" -eq 0 ]; then
        ok 'LOAD segments aligned for 16 KB pages'
    else
        note 'LOAD segments aligned below 16 KB; add -Wl,-z,max-page-size=16384'
    fi
fi

defined=$("$NM" -D --defined-only -C "$LIB" 2>/dev/null)
if printf '%s\n' "$defined" | grep -q ' T ANativeActivity_onCreate$'; then
    ok 'exports ANativeActivity_onCreate'
else
    bad 'ANativeActivity_onCreate is not exported (the app cannot start it)'
fi
if printf '%s\n' "$defined" | grep -q 'ps2_android_input::update'; then
    ok 'exports the touch overlay entry points'
else
    bad 'touch overlay symbols are missing (patch 0005 not applied?)'
fi

needed=$("$READELF" -dW "$LIB" | awk '/NEEDED/ { print $NF }' | tr -d '[]' | sort)
printf 'needed   %s\n' "$(printf '%s' "$needed" | tr '\n' ' ')"
if printf '%s\n' "$needed" | grep -qx 'libc++_shared.so'; then
    note 'links libc++_shared.so; the app would have to ship it for this ABI'
else
    ok 'no libc++_shared.so dependency'
fi
if printf '%s\n' "$needed" | grep -qx 'libandroid.so'; then
    ok 'links libandroid.so (native_app_glue)'
else
    note 'libandroid.so is not a dependency; NativeActivity glue may be missing'
fi

if [ "$fail" -eq 0 ]; then
    printf 'RUNNER_VERIFY_OK (%d warnings)\n' "$warn"
    exit 0
fi
printf 'RUNNER_VERIFY_FAILED (%d failures, %d warnings)\n' "$fail" "$warn"
exit 1
