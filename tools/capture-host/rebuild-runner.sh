#!/usr/bin/env bash
# rebuild-runner: set the x86_64 capture-host build flags into the CMake cache,
# then build ps2EntryRunner (attempt log: fresh file per run, never shared).
#
# Flags (D13 as amended, see docs/agent/TECHNICAL_DECISIONS.md):
#   -msse4.1                        upstream Linux CI flag (build.yml:33);
#                                   ps2_runtime_macros.h needs SSE4.1
#   -I/root/sotc/neon-shim          resolves #include <arm_neon.h> (unguarded
#                                   includes) and #include <mach-o/dyld.h>
#                                   (decomp_lines67.cpp) to the host shims
#   -include .../arm_neon.h         forces the NEON API into every TU: the
#   -include .../arm_neon.h         forces the NEON API into every TU: the
#                                   overrides that guard the include with
#                                   __ARM_NEON/__arm__ (sotc_native.cpp,
#                                   decomp_char104.cpp, decomp_hot47j.cpp)
#                                   never include it on x86, but their
#                                   NEON-typed code still compiles
set -uo pipefail

cd /root/sotc/build/cmake

cmake -DCMAKE_CXX_FLAGS="-msse4.1 -I/root/sotc/neon-shim -include /root/sotc/neon-shim/arm_neon.h" . || exit 1

grep '^CMAKE_CXX_FLAGS:STRING' CMakeCache.txt

LOG=/root/sotc/logs/build-ps2EntryRunner-attempt5.log
if cmake --build . --target ps2EntryRunner -j 2 > "$LOG" 2>&1; then
    echo "BUILD_OK"
else
    echo "BUILD_FAILED"
fi
tail -6 "$LOG"
ls -la ps2xRuntime/ps2EntryRunner 2>/dev/null && echo "RUNNER_BINARY_PRESENT"
