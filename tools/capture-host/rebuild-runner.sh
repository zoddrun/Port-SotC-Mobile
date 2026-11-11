#!/usr/bin/env bash
# rebuild-runner: set the x86_64 capture-host build flags into the CMake cache,
# then build ps2EntryRunner (attempt log: fresh file per run, never shared).
#
# Flags (D13 as amended, D17; see docs/agent/TECHNICAL_DECISIONS.md):
#   -msse4.1                        upstream Linux CI flag (build.yml:33);
#                                   ps2_runtime_macros.h needs SSE4.1
#   -I/root/sotc/neon-shim          resolves #include <arm_neon.h> (unguarded
#                                   includes) and #include <mach-o/dyld.h>
#                                   (decomp_lines67.cpp) to the host shims
#   -include .../arm_neon.h         forces the NEON API into every TU: the
#                                   overrides that guard the include with
#                                   __ARM_NEON/__arm__ (sotc_native.cpp,
#                                   decomp_char104.cpp, decomp_hot47j.cpp)
#                                   never include it on x86, but their
#                                   NEON-typed code still compiles
#   -DPS2X_ENABLE_RUNNER_UNITY_BUILD=OFF  the overrides are authored as
#                                   self-contained TUs: several redeclare the
#                                   same namespace-scope types (decomp_group::
#                                   NextRegs in decomp_group.cpp, decomp_hot44c.
#                                   cpp, decomp_ai106.cpp), which is fine as
#                                   separate TUs but a redefinition error inside
#                                   a unity batch (attempt 5, unity_45). Unity
#                                   batch composition depends on the generated
#                                   runner file count, which changes at every
#                                   pipeline stage, so per-file unity
#                                   exclusions would be whack-a-mole; disable
#                                   unity for the capture host (D17). Also
#                                   lowers peak per-compile memory (1 file per
#                                   TU instead of 32) under the 1.7 GiB limit.
set -uo pipefail

cd /root/sotc/build/cmake

cmake -DCMAKE_CXX_FLAGS="-msse4.1 -I/root/sotc/neon-shim -include /root/sotc/neon-shim/arm_neon.h" \
      -DPS2X_ENABLE_RUNNER_UNITY_BUILD=OFF . || exit 1

grep '^CMAKE_CXX_FLAGS:STRING' CMakeCache.txt
grep '^PS2X_ENABLE_RUNNER_UNITY_BUILD' CMakeCache.txt

LOG=/root/sotc/logs/build-ps2EntryRunner-attempt6.log
if cmake --build . --target ps2EntryRunner -j 2 > "$LOG" 2>&1; then
    echo "BUILD_OK"
else
    echo "BUILD_FAILED"
fi
tail -6 "$LOG"
ls -la ps2xRuntime/ps2EntryRunner 2>/dev/null && echo "RUNNER_BINARY_PRESENT"
