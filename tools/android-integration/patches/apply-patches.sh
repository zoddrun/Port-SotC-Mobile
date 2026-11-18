#!/usr/bin/env bash
# Apply the android-integration patches to a celosia checkout. The patches are
# unified diffs against the pinned commit f8c269e; git apply is content-checked,
# so a tree that drifted fails loudly. "Already applied" is detected by an
# explicit marker per patch (the tree also carries unrelated uncommitted
# changes - the developer's non-Vita fixes - so a whole-tree diff check would
# be ambiguous).
#
# Apply these at Milestone 4 start (first ARM64 configure), together with the
# capture-host series; never while a capture-host build is running.
#
# Usage: ./apply-patches.sh [path-to-celosia-checkout]   (default: /root/celosia)
set -euo pipefail

CELOSIA=${1:-/root/celosia}
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
EXPECTED_COMMIT=f8c269e

cd "$CELOSIA"
head=$(git rev-parse --short HEAD)
if [ "$head" != "$EXPECTED_COMMIT" ]; then
    echo "error: celosia at $head, patches are made against $EXPECTED_COMMIT" >&2
    echo "       (review the diffs under $HERE and rebase them onto the new commit)" >&2
    exit 1
fi

applied() {
    # $1: file, $2: marker string that exists only in the patched state.
    grep -qF -- "$2" "$1" 2>/dev/null
}

# Patch -> file it touches -> marker proving it is already in the tree.
check_patch() {
    case "$1" in
        0001-english-only-host-menu.patch)
            applied PS2Recomp/ps2xRuntime/src/lib/host_menu.cpp "Port-SotC-Mobile (D7): English only" ;;
        0002-games-database-scus-97472.patch)
            applied PS2Recomp/ps2xRuntime/src/lib/games_database.cpp '{ "SCUS-97472", "Shadow of the Colossus (USA)" }' ;;
        0003-android-quit-graceful.patch)
            applied PS2Recomp/ps2xRuntime/src/main.cpp "Port-SotC-Mobile (D18): return instead of hard-exiting" ;;
        0004-android-boot-path.patch)
            applied PS2Recomp/ps2xRuntime/src/main.cpp "Port-SotC-Mobile (D18): SOTC_BOOT_ELF" ;;
        0005-android-touch-input.patch)
            applied PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp "ps2_android_input::update();" ;;
        *)
            echo "error: no marker defined for $1 (add one before shipping a new patch)" >&2
            return 2 ;;
    esac
}

for p in "$HERE"/[0-9]*.patch; do
    name=$(basename "$p")
    echo "==> applying $name"
    if check_patch "$name"; then
        echo "    already applied (marker present)"
        continue
    fi
    if git apply --check "$p" 2>/dev/null; then
        git apply "$p"
        echo "    applied"
    else
        echo "error: $name does not apply cleanly and its marker is absent" >&2
        echo "       (the tree drifted; review the diff and rebase the patch)" >&2
        exit 1
    fi
done
echo "PATCHES_APPLIED"
