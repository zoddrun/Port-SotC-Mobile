#!/usr/bin/env bash
# Verify the arm_neon.h shim (restricted SIMDE_ARM_NEON_A32V7 aliases) works
# in BOTH include orders the ps2EntryRunner unity build can produce:
#   A. forced shim include first, PCH (immintrin + cmath) after it
#   B. unity-style: immintrin first, then <arm_neon.h> via -I
# Expected output: lane=0 from mini_tu (all-positive w-column), orderB
# lane=1073741824 (bits of 2.0f), then ALL_ORDERS_PASSED.
#
# Self-relative: run from this directory in the repo checkout (via /mnt/c in
# WSL) or from a copy anywhere; it installs nothing.
set -e
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SHIM=/root/sotc/neon-shim/arm_neon.h
[ -f "$SHIM" ] || { echo "error: shim not installed at $SHIM (see ../README.md)" >&2; exit 1; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$SCRIPT_DIR/mini_pch.hxx" "$SCRIPT_DIR/mini_tu.cpp" "$WORK/"
cd "$WORK"

echo "== A1. PCH compiled with the forced shim include =="
g++ -std=gnu++20 -msse4.1 -include "$SHIM" -x c++-header mini_pch.hxx -o mini_pch.hxx.gch

echo "== A2. TU: shim first, then PCH (immintrin parsed after the shim) =="
g++ -std=gnu++20 -msse4.1 -I/root/sotc/neon-shim -include "$SHIM" -include "$WORK/mini_pch.hxx" mini_tu.cpp -o mini_tu_forced
./mini_tu_forced

echo "== B. unity-style order: immintrin first, then <arm_neon.h> via -I =="
cat > order_b.cpp <<'EOF'
#include <immintrin.h>
#include <arm_neon.h>
#include <cstdio>
static uint32x4_t mixed(__m128 d)
{
    const uint32x4_t b = vandq_u32(vreinterpretq_u32_f32(d), vdupq_n_u32(0x7FFFFFFFu));
    return b;
}
int main()
{
    alignas(16) const float in[4] = {1.0f, 2.0f, 3.0f, 4.0f};
    std::printf("orderB lane=%u\n", vgetq_lane_u32(mixed(_mm_load_ps(in)), 1));
    return 0;
}
EOF
g++ -std=gnu++20 -msse4.1 -I/root/sotc/neon-shim order_b.cpp -o mini_tu_orderb
./mini_tu_orderb
echo "ALL_ORDERS_PASSED"
