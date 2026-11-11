# Technical Decisions Log

> Agent memory file: rationale for every significant decision. Append new decisions; never delete old ones.

## D1 — WSL2 Ubuntu as pipeline execution host (2026-11-09)

**Decision:** Run the Celosia recompilation pipeline inside WSL2 Ubuntu 26.04, not Windows-native.

**Rationale:**
- Celosia is bash-orchestrated and macOS-oriented; Windows native lacks cmake/ninja on PATH anyway.
- WSL2 has CMake 4.2.3, Ninja 1.13.2, g++ 15.2, Python 3.14 installed and 953 GB free on its ext4 filesystem.
- Building inside ext4 avoids the C: drive constraint (only ~11.6 GB free) and the space-in-path problem.

**Trade-offs:** capture stage opens a real window → need `xvfb-run` (install required); file access from Windows via `/mnt/c` is slower (build in ext4, not on /mnt/c).

## D2 — Python-stdlib ISO inspector (2026-11-09)

**Decision:** `tools/inspect_original_iso.py` uses only Python stdlib, mirroring the app's Kotlin DiscValidator logic.

**Rationale:** zero dependencies; validates independently of the Android build; produces the JSON report `build/reports/original_iso.json`.

## D3 — Serial derivation fix for SCUS-974.72 (2026-11-09)

**Decision:** Boot filename `SCUS_974.72` is 11 characters, not 12. Correct check: `len(base) == 11` with pattern `AAAA_NNN.NN` → serial `SCUS-97472`.

**Rationale:** Live testing against the real disc; the initial 12-char assumption failed and was corrected immediately.

## D4 — Prefer celosia's vendored PS2Recomp for game core generation (2026-11-09)

**Decision:** Use `third_party/celosia/`'s internal PS2Recomp fork (game-tuned, with SotC patches) to generate the game core; use `third_party/PS2Recomp/android/` scaffold as reference for the Android build integration.

**Rationale:** The celosia fork contains 161 SotC-specific override files and pipeline glue the upstream lacks; the upstream checkout has the newer Android packaging knowledge. Each is used where it is strongest.

## D5 — ISO stays at repo root under `original iso/` (2026-11-09)

**Decision:** Developer-supplied ISO lives in `original iso/` (with space), gitignored via `/original iso/`; never modified, moved, or committed. Copies for pipeline use are made read-only into WSL ext4.

**Rationale:** Preserves the developer's asset location; gitignore verified with `git check-ignore -v`.

## D6 — Space-in-path workaround via WSL ext4 workspace (2026-11-09)

**Decision:** Never run Ninja/CMake builds under a path containing a space (repo dir "Port SotC Mobile" does). Clone or copy the pipeline sources into a space-free WSL path (e.g. `~/sotc-port/`) and run there.

**Rationale:** Ninja emits non-portable path warnings for spaces; CMake policy + toolchain edge cases. ext4 also sidesteps C: free-space limits.

## D7 — English-only translation of celosia runtime UI (pending)

**Decision:** When integrating the PS2X runtime + overrides into our port, all Portuguese UI strings must be translated to English per the project mandate.

**Rationale:** AGENTS.md mandates 100% English in all project components, including UI text carried in from third-party code we build on.

## D8 — Ghidra not required for symbol mapping (2026-11-09)

**Decision:** Rely on celosia's `funcmap_elf.py` heuristics for boot-ELF symbol recovery; no Ghidra install.

**Rationale:** Celosia was designed to work without Ghidra; revisit only if heuristics prove insufficient during Milestone 3 captures.

## D9 — Commit cadence (2026-11-09)

**Decision:** Commit at meaningful milestones only (docs/memory, pipeline results, ARM64 core, ABI integration...); never push to `origin` without explicit developer authorization; never commit the ISO, build artifacts, or `build/reports/original_iso.json` (contains local path but no secrets — still build output, keep out of git).

**Rationale:** Atomic, reviewable history per AGENTS.md Git Practices.

## D10 — Adopt the developer's /root/sotc WSL pipeline workspace (2026-11-10)

**Decision:** The developer runs their own Celosia pipeline instance in WSL at `/root/celosia` (clean clone of our third_party/celosia @ f8c269e) with build dir `/root/sotc/build` and disc dir `/root/sotc/disc`. We adopt and continue that workspace instead of maintaining a duplicate. My duplicate in `~/sotc-port` was deleted.

**Rationale:** Same commit, same pipeline; duplicate builds waste the 1.7 GiB WSL RAM budget. Developer confirmed adoption.

**Trade-offs:** The workspace layout differs from the celosia script defaults — set `CELOSIA_BUILD=/root/sotc/build CELOSIA_DISC=/root/sotc/disc` when invoking the script, or drive cmake directly as the developer did.

## D11 — CMake 4.x policy floor for raylib 5.5 FetchContent (2026-11-10)

**Decision:** The vendored PS2Recomp's FetchContent of raylib 5.5 requires `-DCMAKE_POLICY_VERSION_MINIMUM=3.5` under CMake 4.2. The developer's configure already applied it.

**Rationale:** CMake 4 rejects projects declaring cmake_minimum_required < 3.5; raylib 5.5's CMakeLists declares an older minimum. Reproduce in any fresh configure via `CELOSIA_CMAKE_ARGS="-DCMAKE_POLICY_VERSION_MINIMUM=3.5"`.

## D12 — Large file copies into WSL go through \\wsl.localhost, not /mnt/c (2026-11-10)

**Decision:** Copy multi-GB files (the ISO) from Windows into WSL ext4 via `Copy-Item ... \\wsl.localhost\Ubuntu\...`, never `cp` from `/mnt/c` inside WSL.

**Rationale:** WSL-side `cp` of the 2.5 GiB ISO over the 9p filesystem failed with "Cannot allocate memory" (WSL sees only 1.7 GiB RAM). The Windows-side path streams without WSL RAM pressure.

## D13 — x86_64 capture host: SIMDe NEON shim + upstream CI flags (2026-11-10)

**Decision:** Building the celosia pipeline for x86_64 (the capture host) needs three things; the first two are ours, the third matches upstream's own Linux CI:
1. **NEON shim (restricted aliases)** — the game overrides (`game/overrides/*.cpp`, 8 files incl. sotc_native.cpp, decomp_pose82.cpp) use the NEON API (celosia targets ARM hosts: Vita / Apple Silicon). Shim at `/root/sotc/neon-shim/arm_neon.h` includes `<simde/arm/neon.h>` (`libsimde-dev` 0.8.2) and defines **only** `SIMDE_ARM_NEON_A32V7_ENABLE_NATIVE_ALIASES` — never the global `SIMDE_ENABLE_NATIVE_ALIASES`. The global switch propagates through `simde-features.h:693` into every architecture, aliasing the **x86** intrinsic names (simde/arm/neon.h transitively pulls `simde/x86/avx.h` via `neon/qrshl.h`) and the ARM CRC names (`__crc32b` via `neon/crc32.h` → GCC's `ia32intrin.h`); those macros then corrupt GCC's own `immintrin.h` whenever it is parsed after the shim (observed as `_mm256_undefined_pd` "passed 1 arguments, but takes just 0" cascades in avxintrin.h). Audit of all 108 distinct NEON intrinsics the overrides call: every one is emitted under the A32V7 alias guard alone — nothing needs A32V8/A64V8 — so the restricted shim covers everything and is order-independent with respect to GCC's x86 headers (validated in both orders: shim-then-immintrin-PCH and immintrin-then-shim, correct numeric results).
2. **Force-include** — three of the eight overrides (`sotc_native.cpp`, `decomp_char104.cpp`, `decomp_hot47j.cpp`) guard `#include <arm_neon.h>` with `__ARM_NEON`/`__arm__`, so on x86 the shim is never loaded there while their NEON-typed code still compiles — exactly what failed build attempt 3 (unity_48: `sotc_native.cpp:8454: 'uint32x4_t' does not name a type`; unity_47, whose override includes are unguarded, compiled clean). `-include /root/sotc/neon-shim/arm_neon.h` injects the NEON API into every TU regardless of guards. The generated runner code (`build/runner/*.cpp`) contains zero NEON names (census), so the force-include cannot collide with recomp output.
3. **SSE4.1 flags** — `ps2_runtime_macros.h` uses `_mm_extract_epi64` (SSE4.1); GCC enforces `always_inline` target checks, and the `ps2_runtime` static lib lacks the flag the runner target has. Upstream's own CI (`.github/workflows/build.yml:33`) builds with `-DCMAKE_CXX_FLAGS=-msse4.1` on Linux.

All applied to the CMake cache: `-DCMAKE_CXX_FLAGS='-msse4.1 -I/root/sotc/neon-shim -include /root/sotc/neon-shim/arm_neon.h'`. No vendored sources modified. Host CPU verified SSE4.1/SSE4.2 capable.

**Rationale:** Least invasive fix; exactly replicates the upstream-supported Linux configuration plus the shim for the ARM-only overrides. The runtime's own NEON uses (ps2xIOP sound_mixer.cpp) are correctly `__ARM_NEON`-guarded and unaffected. The Android ARM64 build uses the NDK's real `arm_neon.h` (with `USE_SSE2NEON` handling x86-intrinsics→NEON in the other direction) and must never include this shim directory.

**Trade-offs:** NEON→SSE translation (SIMDe) is not bit-identical to real NEON (last-bit float differences) and slightly slower — acceptable for capture runs; the ARM64 target build uses real NEON. If an override uses an intrinsic SIMDe 0.8.2 lacks, we'll see a compile error and handle it explicitly.

## D14 — Capture display on WSL: unmount WSLg ro socket dir + Xvfb :99 + llvmpipe (2026-11-10)

**Decision:** WSLg leaves `/tmp/.X11-unix` mounted **read-only** (its own X0 socket is unusable from root when the systemd user session fails), which blocks Xvfb from creating a socket. Fix: `umount /tmp/.X11-unix` (as root, distro /tmp is then a normal rw tmpfs), run `Xvfb :99 -screen 0 1024x768x24 -nolisten tcp`, and give the runner `LIBGL_ALWAYS_SOFTWARE=1` (Mesa llvmpipe; `swrast_dri.so` present). `/root/sotc/capture-module.sh` replicates celosia's `capture()` with these env vars baked in.

**Rationale:** raylib/GLFW needs an X display; Xvfb + software GL is the standard headless recipe. The WSLg socket mount is per-boot state — if a future WSL restart restores it, re-run the umount (script: diag/fix-x11.sh in the session temp; reproduce with `umount /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix`).

**Trade-offs:** llvmpipe rendering is slow — fine for captures (the runner is only run until the first not-yet-recompiled branch). If capture runs turn out frame-starved, raise Xvfb screen size down (640x480) or run with `PS2X_GS_GPU=0`.

## D15 — 8 GB swap file for the LTO link (2026-11-10)

**Decision:** `/root/swapfile` (8 GiB, mkswap+swapon) added to WSL. The ps2EntryRunner link unifies ~1600 translation units with `-flto=auto` under a 1.7 GiB RAM ceiling; stock swap was 1 GB.

**Rationale:** LTO link memory scales with TU count; OOM at link was the predictable next failure. Disk has 951 GB free; swap costs nothing when unused.

**Trade-offs:** Swapping is slow, but a slow link beats a failed one. If it proves too slow, rebuild the runner with `-DPS2X_ENABLE_RUNNER_UNITY_BUILD=ON` already on (49 batches) — worst case disable LTO for capture builds only.

## D16 — mach-o/dyld.h shim via dl_iterate_phdr for non-Apple hosts (2026-11-10)

**Decision:** The vendored `game/overrides/decomp_lines67.cpp` (an upstream cache-miss diagnostic, "SOTC_LINES67=1, Mac only" per its own header comment) guards `#include <mach-o/dyld.h>` only with `#if !defined(__vita__)` — unlike its sibling diagnostics `decomp_readcensus.cpp` and `sotc_prof.cpp`, which use the proper `#if defined(__APPLE__)` guard — so on Linux the build demands a macOS-only header (build attempt 4 failure: unity_46, `fatal error: mach-o/dyld.h`). Bridge it with `mach-o/dyld.h` in the existing shim directory `/root/sotc/neon-shim/` (picked up by the already-threaded `-I` flag; no new build flags), implementing the one API the file uses:

```c
const struct mach_header *_dyld_get_image_header(uint32_t image_index);
```

The Linux analogue is `dl_iterate_phdr`, whose first entry is the main program (glibc documents this): the PT_LOAD that maps file offset 0 starts at `p_offset == 0`, so the image header address is `dlpi_addr + p_vaddr - p_offset`. Validated (`tests/run-mach-test.sh`): PIE, `-no-pie`, and the unity layout (arm_neon.h force-included too) all return a pointer to ELF magic; `image_index != 0` returns null rather than pretending to an analogue it cannot compute.

**Rationale:** Same policy as D13 — no vendored source edits (and `third_party/celosia` is gitignored in this repo, so there is no in-repo vendored copy to patch); the shim is additive declarations, so include-order-safe; the implementation is the genuine platform analogue, not a stub. The vendored code calls it exactly once — `_dyld_get_image_header(0)` in `base()` — and only uses the result as an integer anchor to turn `__builtin_return_address` values into stable image-relative offsets (inside the `SOTC_LINES67_SITES` diagnostic, env-gated off by default).

**Trade-offs:** Only `image_index == 0` is supported (the only index the vendored code passes; other indices have no defined Linux ordering). The ARM64 Android build (Milestone 4) cannot use the x86 `arm_neon.h` shim but DOES need this file — bionic provides `dl_iterate_phdr` via `<link.h>`; mirror it onto that build's include path (tracked in NEXT_STEPS Milestone 4). Both shims and all pipeline scripts are mirrored in-repo at `tools/capture-host/` (byte-verified against the live WSL copies) so the workspace can be rebuilt from the repo alone.

## D17 — Unity build OFF for the x86_64 capture host (2026-11-11)

**Decision:** Configure the capture-host build with `-DPS2X_ENABLE_RUNNER_UNITY_BUILD=OFF` (CMake option default is ON upstream). Applied in `rebuild-runner.sh` (attempt 6) and `celosia-env.sh`'s `CELOSIA_CMAKE_ARGS`.

**Rationale:**
- Build attempt 5 (unity batch size 32) failed in unity_45 with a *class* of error that only exists in unity builds: the overrides are authored as self-contained TUs, and several redeclare the same namespace-scope types independently — `decomp_group::NextRegs` is defined in `decomp_group.cpp:59`, `decomp_hot44c.cpp:68` and `decomp_ai106.cpp:49` (the latter two re-declare it locally so they can call `decomp_group::nextAt`, with an explicit comment saying so). Three identical definitions across three TUs link fine (POD struct, no emitted symbols; the runner even links `-Wl,--allow-multiple-definition`); two definitions in one unity TU = redefinition error. Upstream sidesteps this where they know about it by excluding files from unity one by one (`lean_*.cpp` "each is a translation unit of its own", `ps2_vita_runtime.cpp`), but the `NextRegs` trio collides in *our* batch composition.
- Unity batch composition depends on the generated runner file count (1429 at stage_boot; grows at every later pipeline stage: captures → stage_game recompile). Fixing collisions by per-file exclusions or batch-size shuffling would be whack-a-mole re-appearing at each stage. Disabling unity removes the entire error class deterministically for this host.
- Also lowers peak per-compile memory under the 1.7 GiB WSL limit (one file per TU instead of 32 files' code in one TU). Wall-clock cost: more, smaller compile jobs at `-j2` — acceptable for a background build.

**Trade-offs:** Full rebuild required (unity objects are useless individually); slower compile overall; LTO link memory profile unchanged (8 GB swap stays active). Real ARM64/Android builds (Milestone 4) may keep unity where the file set is stable and known — decide there based on actual errors, not this host decision.

**Context note:** Attempt 5's other two unity_45 errors were fixed by the developer directly in `/root/celosia` (uncommitted local edits, 2026-11-10 10:04): `coll125.h` renames the second `vf0c` to `vf0u` (redeclaration within `clip()`'s `#else` branch), and `decomp_collision.cpp` guards `keepFastOk()`'s `fast::check49()` call with `#if PS2X_VU_NEON_A32` (the NEON fast-path check is A32/Vita-only; x86 returns false). Both fixes are correct for the x86 capture host; preserve them — do not revert the developer's working-tree edits.
