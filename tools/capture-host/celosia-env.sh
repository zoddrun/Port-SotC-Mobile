#!/usr/bin/env bash
# celosia-env: wrapper for the celosia pipeline driver in the adopted
# /root/sotc workspace, carrying every x86_64-host workaround (D10-D15).
#
# Usage:
#   ./celosia-env.sh recompile     # stage_game: relink + recompile + rebuild
#   ./celosia-env.sh build         # rebuild the runner only
#   ./celosia-env.sh vu1           # VU1 microcode collection + codegen
#   ./celosia-env.sh autotest 40   # scripted run with screenshots
#
# Captures use /root/sotc/capture-module.sh instead (it sets DISPLAY for Xvfb).
set -euo pipefail

export CELOSIA_DISC=/root/sotc/disc
export CELOSIA_BUILD=/root/sotc/build
export CELOSIA_JOBS=2
# Keep the CMake cache entries this workspace needs:
# - CMAKE_POLICY_VERSION_MINIMUM=3.5: raylib 5.5 under CMake 4.2 (D11)
# - -msse4.1: upstream Linux CI flag, ps2_runtime intrinsics (D13)
# - -I/root/sotc/neon-shim: SIMDe arm_neon.h for the game overrides (D13)
# - -include .../arm_neon.h: NEON API in every TU for the guarded includes (D13)
export CELOSIA_CMAKE_ARGS="-DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_CXX_FLAGS=-msse4.1 -I/root/sotc/neon-shim -include /root/sotc/neon-shim/arm_neon.h"

# Xvfb display for anything that opens a window (autotest/vu1).
export DISPLAY=${DISPLAY:-:99}
export LIBGL_ALWAYS_SOFTWARE=1

exec /root/celosia/celosia "$@"
