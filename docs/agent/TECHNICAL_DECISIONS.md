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

## D7 — English-only translation of celosia runtime UI (implementation prepared 2026-11-11)

**Decision:** When integrating the PS2X runtime + overrides into our port, all Portuguese UI strings must be translated to English per the project mandate.

**Implementation (prepared as `tools/android-integration/patches/0001-english-only-host-menu.patch`, applied at Milestone 4):** the Portuguese strings live in exactly two `ps2_runtime` files — `host_menu.cpp` (in-game settings menu, compiled on every platform including Android) and `native/native_renderer.cpp` (tuning items). Every string already has an English original in the code; the selection is centralized:
- `host_menu.cpp`'s `portuguese()` (menu language setting, defaulting to the system locale) gates ALL menu text (`T()` map, `groupName`/`groupHelp`, item labels and help).
- `native_renderer.cpp`'s tuning texts take `bool portuguese` whose only non-default caller is that menu (`native_tuning.h:781` defaults it to false; the imgui debug panel uses the default and is not compiled on Android anyway).

So the patch makes `portuguese()` a constant `false` and removes the Language menu item entirely (enum case, value text, change handler, `language=` settings persistence, dead translation entries). Result: every user-visible string renders English on every platform this port builds.

**Trade-offs:** The Portuguese `T()` map and the `pt` fields of `TuningItem`s remain in the third-party source as unreachable data (compiled into the binary but no longer selectable). A hard strip (~250 lines of literals across the two files) was considered and deferred: it would be a large, brittle diff against a pinned upstream commit for zero behavioral gain. The developer may request the hard strip later; the selector patch is the faithful reading of "translated to English" since the English originals are what the code now uses. Old `settings.cfg` files with `language=1` are handled: the key is ignored on load.

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

## D18 — Android integration adopts the upstream raylib-owned NativeActivity (2026-11-11)

**Decision:** Integrate the recompiled game into the app through celosia's own Android path — the runner shared library driven by raylib's NativeActivity glue — instead of re-hosting the runtime behind our ABI-v1 step/pull contract. Concretely: the app gains a Kotlin activity extending `android.app.NativeActivity` (meta-data `android.app.lib_name` pointing at the runner library), through which raylib owns the window, main loop, input polling and audio; our existing Kotlin UX (touch overlay, gamepad mapping, settings persistence) is layered on top as ordinary Android views in that same activity, and our EGL/audio engines remain the diagnostics path. ABI-v1 (`sotc_game_api.h`, `engine/game_core.cpp`) stays in the tree but is re-scoped to the diagnostics / future-headless role; `docs/CORE_ABI.md` is updated to say so at implementation time.

**Rationale (evidence gathered 2026-11-11):**
- `PS2Runtime::run()` is inseparable from raylib on the host side: `initialize()` calls `InitWindow`/`InitAudioDevice`; the loop calls `PollInputEvents`/`BeginDrawing`/`WindowShouldClose`/`CloseWindow`; GS output is uploaded to raylib textures; the host menu draws through raylib; the game thread and GS async pipeline keep their own pacing. A host-driven ABI-v1 port would mean writing a custom raylib platform backend bridging to our EGL/audio/input stack — a large, permanent fork of third-party code, high regression risk, re-verified at every upstream pull, for no player-visible gain.
- The upstream scaffold (`PS2Recomp/android/`) already builds `ps2EntryRunner` for Android exactly this way: CMake makes it a SHARED lib with `-Wl,--undefined=ANativeActivity_onCreate`, compiles `ps2_android_runtime.cpp` into it and links `log`/`android`; the manifest is a NativeActivity with `android.app.lib_name`. This is the path the pipeline author designed; adopting it keeps Milestone 4 a plain configure of the pinned tree (plus the two prepared patch series) instead of a host-layer rewrite. AGENTS.md: pick the proven solution, not the rewrite.
- Our app's investment is preserved rather than discarded: import/validation/library/settings/touch/gamepad are all Kotlin-side and keep working. Using a NativeActivity *subclass* (instead of upstream's `hasCode="false"` raw activity) lets our existing overlay views ride on the same window the glue drives — which matters because upstream has no touch input at all.
- The game-directory hand-off needs no runtime patch: the app extracts the boot tree from the imported `disc.iso` (existing `Iso9660Image` reader) into a fixed private path before launch, and `PS2X_DEFAULT_BOOT_ELF` is baked to that stable path at configure time (fixed applicationId ⇒ deterministic `/data/data/<pkg>/files/...` path).

**Known integration gaps carried into Milestone 4/5 (from upstream source):**
- `ps2_android_runtime.cpp` is a 3-line TODO — upstream ships no touch→pad mapping; our overlay views fill the gap.
- `main.cpp` ends with `std::_Exit(0)`; on Android "Quit game" would kill the whole process (our launcher included). Prepared as `patches/0003-android-quit-graceful.patch`: on `__ANDROID__` `main()` returns instead, because raylib's `android_main` (rcore_android.c, verified in the fetched raylib 5.5 source) ignores main's return value and then calls `ANativeActivity_finish` and pumps events until `destroyRequested` — the designed teardown path (and `main.cpp` already has plain `return 1` failure paths, so returning is structurally supported). Milestone 5 hardening on the app side: declare the game activity with `android:process=":game"` so the runtime's process-lifetime statics are never warm-reused across game sessions (every launch is a fresh process) and a hard exit can never take the launcher down with it.
- The runtime needs the *extracted* disc tree (boot ELF + XFF modules + `NICO.DAT` and other files via `configureIoPathsFromElf`), not the ISO — the import flow gains a pre-launch extraction step (one-time, cached, reuses `Iso9660Image`).
- FFmpeg OFF on Android and the imgui debug panel not compiled there — already known, no action.

**Revisit triggers (flip to a host-driven ABI-v1 port only with new evidence):** raylib's Android backend fails verification under our constraints (e.g., the GS hardware backend is incompatible with raylib's GLES context on target devices), or the developer requires a single-activity, fully host-owned UX. Do not re-litigate without one of these.

## D19 — Continue the pipeline without re-running stage_boot (2026-11-11)

**Decision:** Run the remaining pipeline steps individually (capture → `recompile` per module, then `vu1`) via `tools/capture-host/pipeline-remaining.sh`, instead of `celosia setup`.

**Rationale:** `cmd_setup` always re-runs `stage_boot`, and the driver's `recompile()` deletes and re-copies every `build/runner/*.cpp|*.h` (fresh mtimes), so ninja rebuilds all ~1567 objects from scratch — `setup` would discard attempt 6's multi-hour build of exactly the runner the first capture needs. The script runs only what `cmd_setup` sequences after `stage_boot`, in the same order, through the same wrappers (`celosia-env.sh`, `capture-module.sh`), plus: per-boot host recovery (swap + Xvfb via `fix-x11.sh`), a wait-for-ninja guard so it never races the running build, an incremental verify pass that doubles as an automatic retry of a transiently failed link, one capture retry with `PS2X_INPUT_SCRIPT` progression (the documented stall remedy), and per-stage driver-log snapshots (`stagegame-*.log`) because the driver reuses one build log name across stages.

**Trade-offs:** The per-stage full rebuilds remain (D17's accepted cost — each `stage_game` grows the runner file set and rebuilds everything, hours at `-j2`). `cmd_recompile`'s ordering guard requires `build/recomp/game` to pre-exist; the script creates it, which is identical to what `stage_game` itself does first.

## D20 — NDK r26b (26.1.10909125) for the M4 ARM64 cross-compile (2026-11-11)

**Decision:** Install the Linux NDK r26b (26.1.10909125) into `/root/android-ndk-r26b` in the WSL workspace and use it for the Milestone 4 arm64-v8a cross-configure.

**Rationale:** (1) `app/build.gradle` pins `ndkVersion '26.1.10909125'` — one NDK version across the project keeps the runner `.so` and the app's own native libs toolchain-identical; the NDK's CMake toolchain defaults to `ANDROID_STL=c++_static`, so the runner carries no shared-STL dependency either way. (2) WSL has no Android SDK/NDK, and the Windows-side NDK cannot drive a Linux CMake build (Windows binaries). (3) The CMake-4.2 incompatibility that actually bites here is raylib 5.5's ancient `cmake_minimum_required`, already solved by the D11 flag (`CMAKE_POLICY_VERSION_MINIMUM=3.5`), which the Android configure passes too.

**Fallback:** if the r26b toolchain hits a CMake 4.2 incompatibility at configure time, switch to the scaffold's NDK 28.2.13676358 (`third_party/PS2Recomp/android/app/build.gradle:11`) and record the change here.

**Trade-offs:** 28.2 is what the upstream scaffold tests with; 26.1 is older but matches the app. Install is scripted and idempotent (`tools/capture-host/install-ndk.sh`, mirrored to `/root/sotc/`): it tests the zip, checks the exact `Pkg.Revision`, and smoke-tests clang. Network + disk only — safe to run while the x86 pipeline builds.
