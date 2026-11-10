#!/usr/bin/env bash
# Audit: for every NEON identifier the celosia game overrides actually call,
# find the SIMDe native-alias macro that provides it. Output: per-name guard.
# Audits the WSL checkout /root/celosia (result 2026-11-10: all 108 distinct
# intrinsics are gated on SIMDE_ARM_NEON_A32V7_ENABLE_NATIVE_ALIASES alone).
set -uo pipefail
cd /root/celosia/game/overrides || exit 1

# All v-prefixed call names used by the overrides (includes local helpers;
# those simply produce no SIMDe hit and are dropped).
names=$(grep -ohE '\bv[A-Za-z0-9_]+\(' ./*.cpp | tr -d '(' | sort -u)

for n in $names; do
    hit=$(grep -rh -B6 "^[[:space:]]*#define ${n}(" /usr/include/simde/arm/neon/ 2>/dev/null \
        | grep -o 'SIMDE_ARM_NEON_[A-Z0-9_]*ENABLE_NATIVE_ALIASES' | head -1 || true)
    if [ -n "$hit" ]; then
        echo "$hit $n"
    fi
done | sort | uniq -c | sort -k2

echo "=== names with a SIMDe alias define but NO neon alias guard found ==="
for n in $names; do
    if grep -rq "^[[:space:]]*#define ${n}(" /usr/include/simde/arm/neon/ 2>/dev/null; then
        if ! grep -rh -B6 "^[[:space:]]*#define ${n}(" /usr/include/simde/arm/neon/2>/dev/null | grep -q 'ENABLE_NATIVE_ALIASES'; then
            echo "$n"
        fi
    fi
done
