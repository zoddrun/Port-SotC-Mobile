# capture-host — x86_64 Linux host shims & scripts for the celosia pipeline

Everything the Port-SotC-Mobile pipeline needs on its **x86_64 Linux capture
host** (WSL2 Ubuntu, adopted workspace `D10`): the platform shims the vendored
game code expects but this host lacks, the pipeline wrapper scripts, and the
tests that validate each shim.

The vendored pipeline (`third_party/celosia`, cloned at `/root/celosia` in
WSL; the checkout is gitignored in this repo — it is upstream's repo, not
ours) was written for the author's platforms: macOS + ARM. Two of its
assumptions do not hold on Linux x86_64, and both are bridged **without
editing vendored sources** via the shim directory that the build flags put on
the include path:

| Shim | Replaces | Why | Decision |
|---|---|---|---|
| `neon-shim/arm_neon.h` | `<arm_neon.h>` | The game overrides use the NEON API; x86_64 has no NEON. Provides it through SIMDe with **only** `SIMDE_ARM_NEON_A32V7_ENABLE_NATIVE_ALIASES` (the global alias switch would also alias x86/CRC names and corrupt GCC's own `immintrin.h` in force-include order). | D13 |
| `neon-shim/mach-o/dyld.h` | `<mach-o/dyld.h>` | `decomp_lines67.cpp` guards the include only with `#if !defined(__vita__)` (its sibling diagnostics use the proper `__APPLE__` guard), so Linux demands the header. Implements `_dyld_get_image_header(0)` as the genuine Linux analogue via `dl_iterate_phdr`. | D16 |

Build flags (all in `CMAKE_CXX_FLAGS`, see `rebuild-runner.sh`):

- `-msse4.1` — upstream's own Linux CI flag; `ps2_runtime_macros.h` needs SSE4.1.
- `-I/root/sotc/neon-shim` — resolves the unguarded includes to the shims.
- `-include /root/sotc/neon-shim/arm_neon.h` — forces the NEON API into every
  TU: three overrides guard `#include <arm_neon.h>` with `__ARM_NEON`/`__arm__`
  (skipped on x86) while their NEON-typed code still compiles.

## Install into the WSL workspace

```bash
# scripts -> /root/sotc/          (shims -> /root/sotc/neon-shim/)
cp celosia-env.sh capture-module.sh rebuild-runner.sh fix-x11.sh diag-x11.sh /root/sotc/
cp -r neon-shim/. /root/sotc/neon-shim/
```

## Usage (in WSL, workspace of D10: `/root/celosia`, `/root/sotc`)

- `/root/sotc/rebuild-runner.sh` — set the D13/D16 flags into the CMake cache
  and build `ps2EntryRunner` (prints `BUILD_OK`/`BUILD_FAILED` +
  `RUNNER_BINARY_PRESENT`; per-attempt log under `/root/sotc/logs/`).
- `/root/sotc/capture-module.sh KERNEL|MANAGER|GAMECORE` — celosia `capture()`
  replica: runs the recompiled boot ELF until it jumps into unrecompiled code
  and dumps the 32 MiB guest RAM at that moment.
- `/root/sotc/celosia-env.sh recompile|build|vu1|autotest` — wraps the celosia
  driver with every host workaround (paths, `-j2`, CMake flags, `DISPLAY=:99`,
  software GL).
- `tests/run-test2.sh`, `tests/run-mach-test.sh` — shim validation (both
  include orders; PIE/no-PIE/unity layouts; ELF magic check).
- `tests/audit-aliases.sh` — the audit that proved all 108 NEON intrinsics the
  overrides call are gated on the A32V7 alias alone (audits `/root/celosia`).
- `tests/ndk-smoke.sh` — Milestone 4 toolchain smoke test: proves NDK r26b
  (`/root/android-ndk-r26b`) + CMake 4.2 + Ninja + arm64-v8a + android-28 (with
  the D11 policy flag) produce valid AArch64 Android ELF artifacts. Prints
  `NDK_SMOKE_OK`; log `/root/sotc/logs/ndk-smoke.log`; trivial CPU/disk load —
  safe to run while the pipeline builds (mirrored to `/root/sotc/ndk-smoke.sh`).
- `/root/sotc/configure-android.sh` + `/root/sotc/build-android-runner.sh` —
  the Milestone 4 pair: cross-configure `build-android` with NDK r26b (after
  `PIPELINE_DONE`, patches 0001-0006, raylib and sse2neon reused from local
  checkouts) and build `libps2EntryRunner.so`.
- `/root/sotc/android-after-pipeline.sh` — unattended handoff: polls until
  `pipeline-remaining.sh` exits and `PIPELINE_DONE` appears, then runs the two
  scripts above in order and prints `ANDROID_CONFIGURE_OK` /
  `ANDROID_BUILD_OK` / `ANDROID_PIPELINE_FAILED`. Start it detached with
  `launch-android-after.sh` (`nohup setsid`, survives the shell); it uses no
  CPU while waiting and refuses to start twice.

## After a WSL restart (per-boot recovery, D14/D15)

```bash
/root/sotc/fix-x11.sh          # umount WSLg's read-only /tmp/.X11-unix
Xvfb :99 -screen 0 1024x768x24 -nolisten tcp &
swapon /root/swapfile          # 8 GB swap for the LTO link
```

## Milestone 4 note (ARM64 Android)

The Android NDK build must use the toolchain's real `arm_neon.h` — **never**
`neon-shim/arm_neon.h` — but it still cannot provide `<mach-o/dyld.h>`, so
`neon-shim/mach-o/dyld.h` (or an equivalent) is needed on that include path
too; bionic provides `dl_iterate_phdr` via `<link.h>`.
