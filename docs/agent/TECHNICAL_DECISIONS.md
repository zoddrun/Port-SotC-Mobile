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

**Amendments (2026-11-18, implementation findings — the decision stands, two details changed):**
- **The Kotlin overlay cannot ride on the runner's window.** A `NativeActivity` has no view hierarchy to add to: raylib's `native_app_glue` owns the `ANativeWindow` and the whole frame. The on-screen controls are therefore implemented in the runner itself (patch `0005-android-touch-input.patch`): `ps2_android_input` samples `CORE.Input.Touch` (which raylib already fills from every motion event) once per host frame, publishes a mutex-guarded pad state that `PSPadBackend::readState` merges at each VBlank, and draws the overlay with raylib in the same GL frame. The Kotlin `TouchControllerView` remains the diagnostics host's overlay.
- **`PS2X_DEFAULT_BOOT_ELF` cannot be baked** after all: the app's private path depends on the device user (`/data/user/<n>/...`), so a configure-time constant is wrong on a multi-user device. Patch `0004-android-boot-path.patch` reads `SOTC_BOOT_ELF` instead, published by `GameNativeActivity` before the library loads; the same channel carries the touch/look knobs (`core/RuntimeEnvironment.kt`).
- **The launcher's audio settings reach the runner through the environment too.** The runtime owns its audio device and its own volume (`hostmenu::settings().volume`, 0..10), so the app cannot mix it. Patch `0006-android-audio-volume.patch` applies `SOTC_AUDIO_VOLUME` (0..1) right after `hostmenu::load()`, which also makes the app's mute meaningful; a later change in the runtime's menu still wins for that session.
- **The runtime's console needs an explicit redirect.** Android discards the stdio of a framework-started app, and the runtime reports boot progress and failures with `std::cout`/`std::cerr`. `GameNativeActivity` calls the JNI `nativeRedirectStdio` (`freopen` + line buffering) before the library loads, so every launch leaves `<files>/logs/game-<yyyyMMdd-HHmmss>.log`; without it the first boot would leave no trace.

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

**Result (2026-11-11 07:22):** the toolchain smoke test passed (`tools/capture-host/tests/ndk-smoke.sh` → `NDK_SMOKE_OK`, log `/root/sotc/logs/ndk-smoke.log`): NDK r26b `android.toolchain.cmake` + CMake 4.2.3 + Ninja + `arm64-v8a` + `android-28`, with the D11 policy flag, produced valid AArch64 Android ELF artifacts (both a shared library and a PIE executable, `built by NDK r26b (10909125)`). The 28.2 fallback is retired unless the real project configure fails on something project-specific.

## D21 — OpenGL ES 2.0 is the graphics floor, ES 3 is an opportunistic fast path (2026-11-19)

**Decision:** Build the host engine against `GLES2/gl2.h` and `GLESv2`, write every shader in GLSL ES 1.00, draw with vertex buffers instead of VAOs, request an OpenGL ES 3 config/context first and fall back to ES 2 on any failure, and declare `android:glEsVersion="0x00020000"` in the manifest. Add a `Force OpenGL ES 2.0` setting that skips the ES 3 attempt for a session. Vulkan is not implemented.

**Rationale:** (1) The compatibility requirement is cross-vendor reach, not peak features. ES 2 is the widest floor on Android; ES 3 gives nothing the current renderer needs. (2) The only emulator available for verification reports `ro.opengles.version=131072` (ES 2.0) and rejected the ES 3 config, so an ES 3-only build could not be exercised at all. (3) GLSL ES 1.00 compiles on both context versions, which keeps one shader source and one draw path instead of two maintained variants. (4) `gl_VertexID` and VAOs are ES 3 only; a VBO fullscreen triangle works everywhere. (5) The raylib-owned runtime already uses raylib's `GRAPHICS_API_OPENGL_ES2` backend, so the host now matches the runtime's capability floor instead of exceeding it.

**Evidence:** on the API 28 emulator the ES 3 probe fails (`eglChooseConfig` 0x3000), the fallback logs `no EGL config for OpenGL ES 3.0; trying the next API`, the ES 2 config/context/surface succeeds, `Renderer backend: OpenGL ES 2.0` is reported, shaders compile, and frames render at 1280x720 (100% non-black screenshot, ~13-15 fps under SwiftShader). The capability query reports the driver's real ES 2 strings.

**Trade-offs:** an ES 3-only device pays one failed `eglChooseConfig` probe at startup (a few milliseconds, one error-level log line). ES 3-only features are unavailable by construction, which is acceptable because nothing in the renderer uses them. If a future renderer needs ES 3 features, it must add a capability-gated path rather than raise the floor.

**Not covered:** the ES 3 renderer path itself has not been runtime-verified (B5); only the fallback has.

## D22 — Adaptive resolution, frame limit and thermal handling stay in the host engine (2026-11-19)

**Decision:** Implement adaptive resolution, a 0/60/30 frame limit, a thermal render-scale ceiling and a low-memory direct-render fallback in `Engine` (the host engine), and keep them out of the raylib-owned runtime for now.

**Rationale:** (1) `Engine` owns its render loop, so these can be implemented and verified without touching the recompiled runner. (2) The runner owns its own frame loop and presentation; injecting pacing there would need a new patch and could change game timing, which the project forbids without evidence. (3) The frame limit paces presentation while the simulation delta stays wall-clock based, so game logic is neither sped up nor slowed down. (4) The thermal ceiling only lowers a render scale; it never rewrites saved settings or game state.

**Trade-offs:** the settings are honest about scope in the UI, but a player who expects them to affect gameplay will not see an effect until the runner is instrumented. That limitation is documented in `PERFORMANCE.md`, `COMPATIBILITY.md` and the settings captions.

**Evidence:** adaptive resolution stepped `26% -> 25%` at 50.6 ms against a 16.7 ms budget; `am send-trim-memory ... RUNNING_LOW` released the offscreen target and rendering continued; Battery Saver wrote `resolution_scale_percent=50`, `adaptive_resolution=true`, `frame_limit_fps=30`. The frame limiter and thermal ceiling could not be exercised on the emulator (B5).

**Superseded in part by D23:** D22 kept these knobs in the host engine only. With patch 0009 the presentation cap and adaptive resolution now also reach the runner, while thermal handling stays host-only. D22's single-owner reasoning remains the controlling rule.

## D23 — Runner-owned pacing through the environment channel (2026-11-19)

**Decision:** Publish `SOTC_FRAME_LIMIT`, `SOTC_ADAPTIVE_RESOLUTION`, `SOTC_RENDER_SCALE`, `SOTC_PERF_OVERLAY` and `PS2X_STATS` as process environment values before `GameNativeActivity.super.onCreate` loads the runner. Patch 0009 reads them once at startup, overrides `wantFps` before raylib's `SetTargetFPS`, adapts the runner's own `tuning().renderScale`, and emits a five-second `[pace]` record inside the existing `PS2X_STATS` gate. Do not add a JNI settings path and do not start the host `Engine` from the gameplay activity.

**Rationale:** (1) Android's `NativeActivity` has no argv and raylib supplies only `argv[0] = "raylib"`, so the environment is the only startup channel. (2) The raylib/Celosia runner is the sole owner of simulation, rendering, presentation and pacing; a second host loop would create competing owners. (3) `SetTargetFPS` changes only raylib's `EndDrawing` wait, not the PS2 guest time step. `SOTC_CAP20` / Steady 20 changes guest release cadence and is rejected as an app setting. (4) A five-second diagnostic line in the redirected game log gives no-phone evidence of the actually-accepted limit and scale. (5) All values are startup-cached, so the UI states that changes apply to the next game session.

**Trade-offs:** a settings change needs a game restart. The runner clamps render scale to `0.5..2`, so the app clamps its 25% setting to `0.500` before publishing rather than claiming a scale the runtime cannot honour. Thermal policy stays host-only until it is device-tested; no guess is made when thermal data is unavailable.

**Verification:** patch 0009 is forward/reverse applicable at the pinned celosia commit and byte-restores the source; the Kotlin environment contract has JVM tests; the rebuilt runner must pass `verify-runner.sh` and host x86 smoke assertions before it is packaged.

## D24 — Static C++ runtime for the host diagnostics library (2026-11-19)

**Decision:** Change `app/build.gradle` from `-DANDROID_STL=c++_shared` to `-DANDROID_STL=c++_static`, matching the runner.

**Rationale:** the NDK's packaged `libc++_shared.so` has 4 KB `PT_LOAD` alignment and cannot be loaded on Android 15 devices with a 16 KB page-size kernel. The runner already uses `c++_static`; `libsotc_core.so` is loaded only from our own JNI bridge, so no C++ object crosses a library boundary and a static runtime is safe. This removes both `libc++_shared.so` entries from the APK.

**Evidence:** `tools/android-integration/verify-apk.py` now reports `APK_NATIVE_VERIFY_OK` with zero failures and zero warnings: every packaged `.so` is ELF64, ABI-correct, `ET_DYN`, 16 KB LOAD-aligned, ZIP-4 KB/16 KB-aligned, and has only resolvable dependencies. The APK shrank from 114,562,620 to 113,273,679 bytes.

## D25 — Capability tiers from probes, never from GPU vendor strings (2026-11-19)

**Decision:** Extend the native GPU query with EGL identity/extensions, additional GL limits, advertised timer/fence flags, and shader/FBO/colour/depth/render-to-texture self-tests. Derive the Kotlin `GpuInfo.Tier` from context version plus passing probes, label extension-list support as `...Advertised`, and never select a tier from a renderer/vendor name.

**Rationale:** a renderer string such as `SwiftShader` or `Adreno` says nothing about whether the operations this renderer needs actually work. Advertised support is a claim by the driver; a self-test is evidence. Treating them separately prevents a false compatibility claim, which the project forbids.

**Trade-offs:** the self-test adds a few milliseconds at settings-screen open time. A failed optional probe disables only that feature and records the reason; a failed core probe yields `UNKNOWN`. Full-game GPU compatibility remains unverified until an ARM64 device runs the runner.

## D26 — APK-wide native library gate (2026-11-19)

**Decision:** Add `tools/android-integration/verify-apk.py` as a no-device gate over every native library in the APK, in addition to `verify-runner.sh` for the runner file.

**Rationale:** `verify-runner.sh` checks only `libps2EntryRunner.so`; the 4 KB-aligned `libc++_shared.so` defect (D24) would have escaped it. A release gate must inspect every shipped `.so` for ELF class, ABI, type, 16 KB LOAD alignment, NEEDED dependencies, ZIP storage and alignment, and record size/SHA-256/BuildID.

**Trade-offs:** the script parses ELF and ZIP headers with the Python standard library, so it cannot replace `llvm-readelf` for deep symbol checks; it complements rather than replaces the existing runner verifier.

## D27 — Apply the 2026-11-19 master specification without overriding logged architecture (2026-11-19)

**Decision:** The developer explicitly chose all four conservative paths when asked about the master spec's conflicts:
1. Keep the raylib-owned runner as the single execution and pacing owner (D18/D22/D23 stand). No host `BootController`, native ISO parser, `cdvdfsv` HLE, VBlank/INTC scheduler, Oboe, SwappyGL or ADPF rewrite.
2. Keep the OpenGL ES 2.0 floor with GLSL ES 1.00 shaders and probe-derived GPU tiers (D21/D25 stand). No vendor `GpuFamily` enum and no ES 3-only post-processing path.
3. Keep the no-device evidence gate. Device boot/logcat remains unverified and never blocks delivery.
4. Work first on real defects and honest UI polish inside the existing architecture, in small verifiable increments.

**Rationale:** the spec's proposed host-owned runtime would fabricate boot stages the host cannot observe, and its vendor-tier/ES3-only rendering would exclude drivers the current build supports. The developer confirmed that compatibility and honesty outrank literal compliance with those sections. No push or release was authorized.

**Consequence:** spec sections that still describe unimplemented subsystems are recorded as deliberately deferred, not as pending defects. The adaptive-resolution no-op (KNOWN_ISSUES item 8) is a real defect and is fixed separately in the runner. All UI strings remain English; the spec's Portuguese examples ("Importar ISO", "INICIAR JOGO", etc.) are not adopted.

## D29 — Production specification received; only verified subsets are adopted (2026-11-19)

**Decision:** The developer supplied a second "Production Release" specification after D27. Its literal implementation is not adopted. Work continues under D18/D21/D25/D27 and the AGENTS.md hard rules.

**Accepted scope:**
- Generic `SYSTEM.CNF` serial recognition, with `SCUS-97472` as the only verified profile. Every other listed serial is recorded as unverified and cannot enable gameplay, trophies or pacing profiles. Implemented 2026-11-19 as `KnownReleases` in `disc/DiscValidator.kt`: all nine named serials are recognized and reported with region/refresh metadata, but `SupportedDiscs` still admits only `SCUS-97472`; other recognized discs fail validation with `KNOWN_UNVERIFIED_SERIAL` instead of being mistaken for supported images.
- Capability-probed graphics paths with the OpenGL ES 2.0 floor. Vendor strings may be shown as diagnostics but must not select tiers or features (D21/D25 stand).
- Original, English, locally evaluated achievements only after their memory offsets are verified against the actual disc. No official trophy catalog, no RetroAchievements game IDs or hashes, and no copied copyrighted trophy text.
- Runner-owned touch and overlay improvements through patches, in English only.
- The existing 16 KB ELF gate is retained and applied to every packaged library.

**Rejected as written:** the host-owned seven-stage boot controller, `cdvdfsv` HLE, INTC/VBlank scheduler, Oboe/SwappyGL/ADPF rewrite, vendor-derived `GpuFamily` tiers, ES3-only post-processing, the official 31-trophy set and rcheevos game data with unverifiable offsets, the Portuguese Compose sample text, the `com.sotc.mobile.*` package rename, and any `adb logcat` or "production release" claim.

**Rationale:** the supplied memory addresses, region profiles and boot stages cannot be verified with the only disc available. Project rules forbid fake state, copyrighted game content and non-English text. The no-device gate stays in force unless a device is explicitly supplied and the rule is changed.

**Consequence:** patch 0010 verification and packaging were completed on 2026-11-20 (see D30). Any later subset must land as small, compile-tested increments with honest labels. No push or release without explicit authorization.

## D30 — Host-only no-LTO smoke binary and honest smoke assertions (2026-11-20)

**Decision:** The x86 host smoke runner is rebuilt with `-fno-lto` on this 4 GB development machine; the shipped ARM64 runner keeps its normal toolchain flags. The smoke harness asserts that at least one positive `frameMs` appears, not that every startup record is already populated, and allows 60 s for the uncapped software-GL case instead of 30 s.

**Rationale:** GCC LTO WPA used ~1.3 GB RSS for over an hour inside a 1.7 GiB WSL limit and left the host with almost no free RAM. Disabling LTO only for the throwaway host verification binary removes the thrash without touching game sources or the ARM64 artifact. Startup `[pace]` records can legitimately show `frameMs=0.000` before the first rendered frame, so requiring zero such records was a harness artifact. The uncapped case at scale 1.000 was intermittently too slow to reach its first five-second record in 30 s under llvmpipe; 60 s makes the check deterministic. All changes are in `smoke-runtime-pacing.sh` and a local `build.ninja` backup; no production source depends on them.

**Trade-offs:** The host smoke binary is larger and slower without LTO, but it still executes the same patched source and proves parsing/pacing/adaptive decisions. These are host-runtime facts, not gameplay or device evidence.

## D31 — Bundled HD pack stays separate from the raylib-owned runner (2026-11-20)

**Decision:** The original GLSL pack, `ShaderPackInstaller`, and the native `HdShaderPipeline` are implemented as a verified diagnostics/ABI-v1 presentation path. Any gameplay shader or touch behavior must be added to the celosia runner as a separate integration patch; the host renderer never owns the game frame, camera, input, or pacing (D18/D21/D25 stand).

**Rationale:** The recompiled runner is the only component that executes game logic and drives the real GLES context. Android raylib requests an OpenGL ES 2 context, so the ES 2 GLSL 1.00 assets are the compatibility floor. The ES 3 assets are capability-gated and are never required. Keeping the host pass isolated gives a real, testable installer and native pipeline without pretending that a launcher preview changes game rendering.

**Implementation constraints:**
- The manifest and shaders are original project assets with an MIT license file; no game textures, BIOS, ISO content, official trophies, or RetroAchievements data are bundled.
- Installation is staged, SHA-256 verified, and published atomically into app-private storage with a state file and rollback. Presets are selected from validated manifest values and materialized as explicit JNI strings.
- The host pipeline reads only a verified installed directory, validates the shader source version against the active context, caches compiled binaries by source/renderer hash when the driver exposes the extension, applies anisotropy only when `GL_EXT_texture_filter_anisotropic` is advertised, and uses framebuffer invalidation/discard only when the corresponding extension is advertised.
- Texture-upload dedup is limited to the diagnostics renderer's immutable offscreen target; it is not presented as a game-texture optimization.
- Preset changes take effect on the next engine session unless the render thread observes a reconfigure request; gameplay settings remain next-session values.

**Consequence:** The app can honestly report "installed", "verified", and "published to the runner". It cannot claim that a physical ARM64 device rendered the HD pass, applied anisotropic filtering, benefited from TBDR discard, or accepted new touch ergonomics until those behaviors are captured from a real session. Runner-side shader activation, sigil HUD, floating stick, right-half look, grip modes, and hotplug cleanup remain device-unverified even after host smoke and ARM64 link checks pass.

## D32 — Manual host relink when the Ninja log loses command hashes (2026-11-20)

**Decision:** For the v0.3.0 host smoke rebuild, compile only the changed patch-0011 objects and relink with the exact commands from `ninja -t commands`. `smoke-runtime-pacing.sh` supports `SOTC_SKIP_HOST_REBUILD=1` for this case; the normal incremental rebuild remains the default. The shipped ARM64 artifact is always built by the regular CMake/Ninja pipeline.

**Rationale:** Restoring a build.ninja backup after a no-LTO experiment left a very small `.ninja_log`, so Ninja considered all ~9,300 capture objects dirty even though their sources and flags were unchanged. A full host rebuild was progressing at roughly one object per several seconds under the 3 GB WSL limit and would have taken many hours for a throwaway verification binary. The exact compile and link commands for the two changed host sources (`ps2_runtime.cpp` and the new `sotc_hd_shader.cpp`), the static archive, and the executable are available from `ninja -t commands`; running those commands produces the same source-correct host runner without rebuilding unrelated objects.

**Trade-offs:** The manual path is only valid when the unchanged objects already exist and the build.ninja is the one used to generate the command list. It is recorded in the smoke log, never used for the ARM64 release artifact, and does not weaken any assertion: the 13 pacing checks and the separate `[hd-shader]` checks still run against the linked runner. The wall-clock windows for the two full-scale (`scale=1.000`) cases were extended to 75 s and 90 s because llvmpipe needed longer than the earlier 30 s/20 s windows to reach two five-second telemetry records while the ARM64 link was active. Only harness durations changed; the assertion patterns and expected values are unchanged.
