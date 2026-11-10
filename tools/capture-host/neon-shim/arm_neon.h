// x86_64 host build of the ARM-targeted game overrides (celosia pipeline).
//
// The celosia overrides (game/overrides/*.cpp) were written for ARM hosts
// (PS Vita / Apple Silicon) and use the NEON API. On an x86_64 capture host
// there is no arm_neon.h; this shim provides the NEON API through SIMDe
// (libsimde-dev 0.8.2, SSE backend).
//
// Alias selection: ONLY SIMDE_ARM_NEON_A32V7_ENABLE_NATIVE_ALIASES is defined,
// never the global SIMDE_ENABLE_NATIVE_ALIASES. The global switch would also
// alias the SSE/AVX intrinsic names (simde/arm/neon.h transitively includes
// simde/x86/*.h, e.g. neon/qrshl.h -> x86/avx.h) and the ARM CRC names
// (__crc32b via neon/crc32.h -> GCC's ia32intrin.h), and those macros then
// corrupt GCC's own immintrin.h/ia32intrin.h whenever they are parsed after
// this shim (macro replacement inside GCC's headers). Restricting to the
// A32V7 aliases leaves every x86 header untouched, which makes this shim
// order-independent with respect to #include <immintrin.h>.
//
// Audit (2026-11-10): every NEON intrinsic the overrides call (108 distinct
// names) is emitted under the A32V7 alias guard; nothing needs A32V8 or
// A64V8, so the CRC aliases stay off.
//
// This header is picked up two ways:
//   1. -I this directory: overrides that include <arm_neon.h> unconditionally
//      resolve to this file.
//   2. -include this file: overrides that guard the include with
//      __ARM_NEON/__arm__ (sotc_native.cpp, decomp_char104.cpp,
//      decomp_hot47j.cpp) never include it on x86, but their NEON-typed code
//      still compiles, so the NEON API must be present in every translation
//      unit regardless.
// ARM64 builds (Android NDK) use the toolchain's real arm_neon.h and must
// never see this directory.
#ifndef PS2X_ARM_NEON_SIMDE_SHIM_H
#define PS2X_ARM_NEON_SIMDE_SHIM_H

#define SIMDE_ARM_NEON_A32V7_ENABLE_NATIVE_ALIASES
#include <simde/arm/neon.h>

#endif // PS2X_ARM_NEON_SIMDE_SHIM_H
