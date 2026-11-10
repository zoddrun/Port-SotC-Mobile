#!/usr/bin/env bash
# Verify the mach-o/dyld.h shim (D16): install it into the shim directory,
# then check that _dyld_get_image_header(0) really resolves to the main
# executable's image header (ELF magic present at the returned address) in
# PIE, no-PIE, and the unity TU layout (arm_neon.h force-included too).
#
# Self-relative: the shim source is at ../neon-shim relative to this script.
set -e
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SHIM_SRC=$SCRIPT_DIR/../neon-shim/mach-o/dyld.h

mkdir -p /root/sotc/neon-shim/mach-o
cp "$SHIM_SRC" /root/sotc/neon-shim/mach-o/dyld.h

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$SCRIPT_DIR/mach_shim_test.cpp" "$WORK/"
cd "$WORK"

echo "== 1. PIE (default) =="
g++ -std=gnu++20 -msse4.1 -I/root/sotc/neon-shim mach_shim_test.cpp -o mach_pie
./mach_pie

echo "== 2. no-PIE (ET_EXEC) =="
g++ -std=gnu++20 -msse4.1 -I/root/sotc/neon-shim -no-pie mach_shim_test.cpp -o mach_nopie
./mach_nopie

echo "== 3. unity TU layout: arm_neon.h force-included too =="
g++ -std=gnu++20 -msse4.1 -I/root/sotc/neon-shim -include /root/sotc/neon-shim/arm_neon.h mach_shim_test.cpp -o mach_unity
./mach_unity

echo "MACH_SHIM_TEST_PASSED"
