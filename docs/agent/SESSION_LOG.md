# Session Log

> Agent memory file: append-only record of work sessions. Newest last.

## Session 1 - 2026-11-09 (recovery + validation)

**Goal:** Full project recovery per the master prompt (Milestones 0-1), then set up for recompilation.

**Done:**
- Milestone 0 complete: full read-through of all first-party native C++ (13 files) and Kotlin (~30 files); docs cross-checked against code; repository state verified (branch main, HEAD 194804f, 12 commits, remote never pushed).
- Background audit A: `third_party/celosia` (bash driver, tools, 161 override files, platform matrix, GPL-3.0, Windows blockers identified).
- Background audit B: `third_party/PS2Recomp` (Android scaffold, runtime internals, gaps: android runtime TODO, no SCUS-97472 entry, FFmpeg off).
- Milestone 1 complete: wrote `tools/inspect_original_iso.py`; validated the developer's USA retail ISO (SCUS-97472; ELF + 3 XFF modules OK; SHA-256 d945516e...; report at `build/reports/original_iso.json`).
- Environment probed: WSL2 Ubuntu 26.04 (cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, 953 GB ext4, passwordless sudo); Windows side (Python 3.12.10, JDK 17 temp + Java 25, Android SDK w/ NDK 26.1).
- Fixed serial derivation bug (11-char boot filename, D3). `.gitignore` hardened for `/original iso/`.
- Created this `docs/agent/` memory system.

**State at session end:** Milestones 0-2 complete (audit + validation + upstream research). Nothing committed yet from this session (`.gitignore` modified, `tools/` untracked). Next: first milestone commit, then WSL workspace setup (NEXT_STEPS.md Milestone 3 item 1).

**Carry-overs:** B1 space-in-path, B2 WSL RAM 1.7 GiB, B3 missing xvfb/libarchive-tools, B4 no ARM64 device.

## Session 2 - 2026-11-09 (memory system + doc sync + commit)

**Done:**
- Created `docs/agent/` memory system: PROJECT_STATE, CODEBASE_MAP, FILE_AUDIT, ARCHITECTURE, TECHNICAL_DECISIONS, COMPLETED_WORK, KNOWN_ISSUES, BLOCKERS, NEXT_STEPS, SESSION_LOG.
- AGENTS.md: added Agent Session Recovery Procedure section.
- Doc sync for Milestone 1: README.md (verified-state row + new "Original disc image validation" section), docs/COMPATIBILITY.md (summary row + section 5 note), docs/ANDROID_PORTING_PLAN.md (phase summary row + Phase 3 status/exit criteria).
- `.gitignore`: added `__pycache__/`; re-verified ISO and pycache both ignored via `git check-ignore`.
- Committed: `docs: establish agent memory and record retail ISO validation`.

**Carry-overs:** unchanged (B1-B4).

## Session 3 - 2026-11-09/10 (Milestone 3: WSL pipeline setup -> adoption)

**Done:**
- Probed WSL: all pipeline tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, git; FFmpeg/GL/X11 dev packages). Installed only `zlib1g-dev`. Network OK.
- Learned: `/mnt/c` copies of the 2.5 GiB ISO fail in-WSL with "Cannot allocate memory" (D12 - use `\\wsl.localhost` from the Windows side).
- Set up a duplicate workspace in `~/sotc-port` (clone + ISO copy) - then discovered the developer's own live pipeline at `/root/celosia` + `/root/sotc` (disc extracted, configure running with the CMake-4 policy fix). Asked the developer; **confirmed adoption** - deleted my duplicate, took over `/root/sotc` (D10).
- Adopted workspace state verified: configure complete (all deps fetched), `ps2_recomp` built, **stage_boot complete** (1429 recompiled functions in `build/runner/`, "Recompilation completed successfully" in recomp.log, build.ninja regenerated post-copy).
- Started the `ps2EntryRunner` build (`-j2`, log `/root/sotc/logs/build-ps2EntryRunner.log`).
- Memory docs updated: decisions D10-D12; blockers B1/B3 -> resolved (R4-R7); PROJECT_STATE and NEXT_STEPS reflect Milestone 3 in progress.

## Session 3 - 2026-11-09/10 (Milestone 3: WSL pipeline setup -> adoption)

**Done:**
- Probed WSL: all pipeline tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, git; FFmpeg/GL/X11 dev packages). Installed only `zlib1g-dev`. Network OK.
- Learned: `/mnt/c` copies of the 2.5 GiB ISO fail in-WSL with "Cannot allocate memory" (D12 - use `\\wsl.localhost` from the Windows side).
- Set up a duplicate workspace in `~/sotc-port` (clone + ISO copy) - then discovered the developer's own live pipeline at `/root/celosia` + `/root/sotc` (disc extracted, configure running with the CMake-4 policy fix). Asked the developer; **confirmed adoption** - deleted my duplicate, took over `/root/sotc` (D10).
- Adopted workspace state verified: configure complete (all deps fetched), `ps2_recomp` built, **stage_boot complete** (1429 recompiled functions in `build/runner/`, "Recompilation completed successfully" in recomp.log, build.ninja regenerated post-copy).
- **ISO hash verified in the adopted workspace**: `/root/sotc/source.iso` SHA-256 `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37` - byte-identical to the validated original.
- First `ps2EntryRunner` build attempt failed: game overrides include `<arm_neon.h>` unconditionally (x86_64 has none). Fixed with a SIMDe shim (`/root/sotc/neon-shim/arm_neon.h`, D13; `libsimde-dev` 0.8.2 installed; `-DCMAKE_CXX_FLAGS=-I/root/sotc/neon-shim` added to the cache). Build restarted (`-j2`).
- Prepared headless display for captures: found WSLg's `/tmp/.X11-unix` mounted read-only (blocked Xvfb); unmounted it, started persistent `Xvfb :99` (D14), confirmed Mesa llvmpipe (`swrast_dri.so`) for `LIBGL_ALWAYS_SOFTWARE=1`; wrote `/root/sotc/capture-module.sh` (celosia capture() replica with display env baked in).
- Added 8 GB swap (`/root/swapfile`) ahead of the LTO link (D15) - 1.7 GiB RAM + 1 GB stock swap would likely OOM linking ~1600 TUs.
- **Second build failure:** `ps2_runtime_macros.h` uses SSE4.1 `_mm_extract_epi64`; upstream's own Linux CI builds with `-msse4.1` - applied via `CMAKE_CXX_FLAGS='-msse4.1 -I/root/sotc/neon-shim'` (D13 amended). Build 3 launched (configure ~150 s then `-j2` build, still running at session note).
- Wrote `/root/sotc/celosia-env.sh` - wraps the celosia driver with all workspace env (CELOSIA_DISC/BUILD/JOBS=2, CELOSIA_CMAKE_ARGS with policy fix + flags, DISPLAY=:99 + software GL) so `recompile`/`build`/`vu1`/`autotest` run correctly; captures use `/root/sotc/capture-module.sh`.
- Recorded the PowerShell->WSL quoting traps (KNOWN_ISSUES #11): bash `$?` never survives `\$?` inline (PowerShell eats it); non-trivial commands go through script files.
- Memory docs updated: decisions D10-D15; blockers R4-R10 resolved; KNOWN_ISSUES/PROJECT_STATE/NEXT_STEPS current. Committed as 416ee0a.

**State at session end:** ps2EntryRunner rebuild running with the NEON shim (Xvfb :99 + swap ready). Next: verify build exit; run `/root/sotc/capture-module.sh KERNEL` (triggers right after boot); then MANAGER/GAMECORE (may need `PS2X_INPUT_SCRIPT` progression), then stage_game, then VU1.

**Carry-overs:** B2 (RAM 1.7 GiB - build at `-j2`), B4 (no ARM64 device).

## Session 4 - 2026-11-10 (Milestone 3: NEON shim root-cause -> build attempt 4)

**Done:**
- Attempt 3 result: 107/169 targets, failed in unity_48 (`sotc_native.cpp:8454: 'uint32x4_t' does not name a type`, then `vrev64_u32`/`vand_u32`/`vorr_u32` in `mdsClipFlags4`). unity_47 - which holds the overrides with unguarded `#include <arm_neon.h>` - compiled clean, proving the SIMDe alias mechanism works whenever the include actually executes.
- Root cause: `sotc_native.cpp`, `decomp_char104.cpp`, `decomp_hot47j.cpp` guard `#include <arm_neon.h>` behind `__ARM_NEON`/`__arm__` (skipped on x86) while their NEON-typed code compiles unconditionally; the other five override files include unguarded and work through `-I/root/sotc/neon-shim`.
- Fix part 1 - force-include: `-include /root/sotc/neon-shim/arm_neon.h` added to `CMAKE_CXX_FLAGS` so every TU gets the NEON API regardless of guards. Census: the 1429 generated runner files contain zero NEON names -> no collision with recomp output.
- Fix part 2 - restricted aliases: the original shim defined the global `SIMDE_ENABLE_NATIVE_ALIASES`; a PCH-order test proved that fatal - `simde/arm/neon.h` transitively pulls `simde/x86/avx.h` (via `neon/qrshl.h`) and `neon/crc32.h` pulls GCC's `ia32intrin.h`, and the resulting x86/CRC alias macros corrupt GCC's own `immintrin.h` when parsed after the shim (`_mm256_undefined_pd` argument-count cascades). Rewrote the shim to define only `SIMDE_ARM_NEON_A32V7_ENABLE_NATIVE_ALIASES`. Audit (`neontest/audit-aliases.sh`): all 108 distinct NEON intrinsics the overrides call are gated on A32V7 alone; nothing needs A32V8/A64V8. Validated both include orders (`neontest/run-test2.sh`: shim-then-PCH and immintrin-then-shim) with correct numeric results.
- Updated `/root/sotc/rebuild-runner.sh` (attempt 4: fresh log `build-ps2EntryRunner-attempt4.log`, prints `BUILD_OK`/`BUILD_FAILED` + `RUNNER_BINARY_PRESENT`) and `/root/sotc/celosia-env.sh` (`CELOSIA_CMAKE_ARGS` gains the `-include` flag); attempt 4 launched (`-j2`, background).
- Environment re-checked at launch: Xvfb :99 alive (PID 1814), 8 GB swap active, ~1.2 GiB RAM available.
- **Attempt 4: NEON fix confirmed working** - unity_48 (holds `sotc_native.cpp` and the guarded-include overrides) compiled clean; 106/169 targets. New failure: unity_46, `decomp_lines67.cpp:10 fatal error: mach-o/dyld.h` - the file guards that include only with `#if !defined(__vita__)`, so Linux demands a macOS-only header. Census of all platform includes across the overrides: `<psp2/...>` (13 files) and the other two mach users (`decomp_readcensus.cpp`, `sotc_prof.cpp`) are properly `__vita__`/`__APPLE__`-guarded; `decomp_lines67.cpp` is the sole unguarded case, and its dyld use is one call (`base()`, main image address for the env-gated SITES diagnostic).
- **Fix (D16):** shim `/root/sotc/neon-shim/mach-o/dyld.h` implementing `_dyld_get_image_header(0)` via `dl_iterate_phdr` (first entry = main program; image header = `dlpi_addr + p_vaddr - p_offset` of the PT_LOAD at offset 0). Validated in PIE, `-no-pie`, and the unity layout (NEON shim force-included): all return a pointer to ELF magic; nonzero index returns null. No new build flags needed (`-I/root/sotc/neon-shim` already covers it); no vendored edits (and `third_party/celosia` is gitignored in this repo - no in-repo copy exists to patch).
- Attempt 5 launched with the shim installed (fresh log `build-ps2EntryRunner-attempt5.log`, `-j2`, background; build resumable at unity_46).
- **Durability fix:** mirrored all capture-host infrastructure into the repo at `tools/capture-host/` (scripts `celosia-env.sh`/`capture-module.sh`/`rebuild-runner.sh`/`fix-x11.sh`/`diag-x11.sh`, `neon-shim/` with both shims, `tests/` with the shim validation suites and the 108-intrinsic alias audit, plus a README with install/recovery/Milestone-4 notes). Byte-verified against the live WSL copies (`verify-mirror.sh`: all MATCH) - previously this infrastructure existed only in the WSL instance and the Windows temp.

**State at session note:** attempt 5 build running in background (log `/root/sotc/logs/build-ps2EntryRunner-attempt5.log`).

**Carry-overs:** B2 (RAM 1.7 GiB - build at `-j2`), B4 (no ARM64 device).
## Session 5 - 2026-11-11 (D17 unity OFF; attempt 6; icon integration; docs)

**Done:**
- Attempt 5 post-mortem: failed at unity_45 with three errors. Two were x86-host source bugs in the overrides - discovered the developer had ALREADY fixed them directly in `/root/celosia` (2026-11-10 10:04, uncommitted local edits on top of f8c269e): `coll125.h` renames the second `vf0c` to `vf0u` (redeclaration in `clip()`'s `#else` branch), and `decomp_collision.cpp` guards `keepFastOk()`'s Vita-only `fast::check49()` behind `#if PS2X_VU_NEON_A32`. These must be preserved (never reset that checkout); an unlogged continuation captured them as `tools/capture-host/patches/0001-non-vita-build-fixes.patch` + `apply-patches.sh` (pinned to f8c269e, content-verified).
- Third error was a unity-build-only ODR collision: `decomp_group::NextRegs` is defined in three override files (`decomp_group.cpp:59`, `decomp_hot44c.cpp:68`, `decomp_ai106.cpp:49` - the latter two re-declare it locally to call `decomp_group::nextAt`). Legal as separate TUs, redefinition inside one unity batch; batch composition changes at every pipeline stage (file count grows), so per-file exclusions are whack-a-mole. **D17: `PS2X_ENABLE_RUNNER_UNITY_BUILD=OFF` for the capture host** - applied in `rebuild-runner.sh` + `celosia-env.sh` (CELOSIA_CMAKE_ARGS); also lowers per-TU memory under the 1.7 GiB limit. Trade-off: full rebuild per pipeline stage (driver deletes + recopies `build/runner/*.cpp`), slower wall-clock.
- WSL had rebooted (per-boot resets, KNOWN_ISSUES #10): re-activated the 8 GB swap; WSLg stacked the read-only `/tmp/.X11-unix` mount TWICE - `fix-x11.sh` rewritten to loop umount; Xvfb :99 restarted.
- Attempt 6 launched (non-unity: 1567 targets vs 169 unity batches, `-j2`, background; log `/root/sotc/logs/build-ps2EntryRunner-attempt6.log`; mirror of both updated scripts verified into WSL via sha256).
- **Launcher icon integrated from the developer's artwork** (`icon/icon.png`, supplied 2026-11-10 19:16, 1254x1254 RGB, centered subject on pure black): `tools/analyze_icon.py` (programmatic probing) + `tools/generate_launcher_icons.py` (deterministic, re-runnable; fixed a monochrome double-alpha paste bug). Adaptive-only (minSdk 28): black background layer + foreground scaled so the dim-content bbox (71.9% x 70.3% of image) fits the 66dp safe zone (artwork 91.8dp on the 108dp canvas) + themed-icon monochrome layer; all five densities. Manifest switched to `@mipmap/ic_launcher` + `roundIcon`; placeholder `drawable/ic_launcher.xml` deleted.
- **Icon verified end-to-end**: programmatic checks (corners transparent, content 91.75dp, mono alpha 0-255); `assembleDebug` BUILD SUCCESSFUL; `aapt2 dump badging` shows `application-icon` at every density resolving to `mipmap-anydpi-v26/ic_launcher.xml`; APK entry listing confirms background + adaptive XMLs + foreground/monochrome at mdpi-xxxhdpi; **49/49 unit tests pass** (testDebugUnitTest BUILD SUCCESSFUL). NOTE: both Gradle runs "exited 1" in the harness - PowerShell `2>&1` turns Kotlin-daemon/JDK stderr warnings into error records (RMI fallback to no-daemon compile); the APK artifact timestamp + BUILD SUCCESSFUL + test XMLs are the ground truth. Pre-existing kotlinc "never used" warnings on `GameImporter.kt:195/234` are false (both variables are read; verified by grep) - not introduced by the icon change, not safety/concurrency class.
- Docs written: `docs/ICON_ASSET.md`, `docs/RECOMPILATION_PIPELINE.md` (all 9 pipeline stages + the five x86_64 host workarounds + legal notes), `docs/CORE_ABI.md`, `docs/ORIGINAL_ISO.md`, `docs/ISO_IMPORT.md`; README gains the icon bullet; agent memory updated (D17, R12/R13, KNOWN_ISSUES #10-#12, FILE_AUDIT Session 5, PROJECT_STATE icon + honest-status fixes, NEXT_STEPS attempt-6 flow).
- M4 prep knowledge gathered: ps2xRuntime Android = SHARED `ps2EntryRunner` + NativeActivity (raylib owns window/input; `-Wl,--undefined=ANativeActivity_onCreate`), `PS2X_DEFAULT_BOOT_ELF` via `ps2xBootElf` Gradle prop, sse2neon v1.9.1 fetched when `CMAKE_SYSTEM_PROCESSOR` is aarch64, FFmpeg OFF on Android, no touch-to-pad mapping (that is `ps2_android_runtime.cpp`'s TODO) - the ABI-v1 host-driven vs raylib-owned-loop decision is the open M5 architecture question. D7 scope confirmed: Portuguese strings live in celosia's `host_menu.cpp`/`native_renderer.cpp` debug UI (not in our app; our resources are English-only).

- Android-integration patch series prepared (`tools/android-integration/`, applied at M4 start): `patches/0001-english-only-host-menu.patch` (D7 implementation - `portuguese()` becomes constant false and the Language item is removed entirely: enum case, value text, change handler, `language=` persistence, dead translation entries; every user-visible `ps2_runtime` string then renders English because `native_tuning.h:781` defaults the tuning-text flag to false and the only Portuguese-passing caller was that menu) and `patches/0002-games-database-scus-97472.patch` (game-name table entry; boot ELF `SCUS_974.72` normalizes to `SCUS-97472` in `main.cpp`). Generated byte-exactly from pristine f8c269e via `/root/sotc/patchprep-android.sh`, round-trip verified (`patch -p5` + `cmp` -> IDENTICAL both); NOT compile-verified until the first M4 configure. `apply-patches.sh` uses explicit per-patch markers because the live tree carries the developer's unrelated uncommitted edits (a whole-tree diff check would be ambiguous). Both series are applied together at M4 start and never mid-build.
- **D18 recorded - Android integration adopts the upstream raylib-owned NativeActivity.** Evidence (read-only research while attempt 6 builds): `PS2Runtime::run()` is raylib-coupled end to end - `InitWindow`/`InitAudioDevice` in `initialize()`, `PollInputEvents`/`BeginDrawing`/`WindowShouldClose`/`CloseWindow` in the loop, GS output uploaded to raylib textures, host menu drawn via raylib, internal game/GS threads with their own pacing - so a host-driven ABI-v1 port would mean writing a custom raylib platform backend (a large, permanent fork). Upstream's scaffold already builds `ps2EntryRunner` for Android exactly this way (SHARED lib, `-Wl,--undefined=ANativeActivity_onCreate`, `ps2_android_runtime.cpp` compiled in - which is a 3-line touch-input TODO, i.e. upstream has no touch mapping at all). Decision: our app gains a Kotlin `GameNativeActivity` (subclass of `android.app.NativeActivity`, `android.app.lib_name` -> the runner lib) so our existing touch overlay/gamepad/settings views ride on the glue's window; boot-tree hand-off needs no runtime patch (pre-launch extraction from the imported `disc.iso` into a fixed private path via our `Iso9660Image`; `PS2X_DEFAULT_BOOT_ELF` baked to that stable path). ABI-v1 re-scoped to the diagnostics/future-headless contract - `docs/CORE_ABI.md` status note + "Producing a core" section updated. Known gaps carried to M4/M5 (in D18): no touch->pad mapping upstream; `main.cpp` ends with `std::_Exit(0)` which would kill the whole app process on "Quit game" - **patch prepared as `0003-android-quit-graceful.patch`** (main returns on `__ANDROID__`; verified against the fetched raylib 5.5 `rcore_android.c`, whose `android_main` calls `ANativeActivity_finish` + polls to `destroyRequested` after main returns; `main.cpp` already has plain `return 1` failure paths); the runtime wants the extracted disc tree, not the ISO. Milestone 5 also gets an `android:process=":game"` recommendation (fresh process per game session; launcher survives any hard exit). NEXT_STEPS Milestone 5 rewritten to the D18 task list; PROJECT_STATE M4/M5 rows updated.

- **D19 recorded - continue the pipeline without `celosia setup`.** `cmd_setup` re-runs `stage_boot`, and the driver's `recompile()` always deletes + re-copies `build/runner/*.cpp|*.h` (fresh mtimes -> ninja rebuilds everything), so `setup` would discard attempt 6's multi-hour build. Wrote `tools/capture-host/pipeline-remaining.sh` (mirrored to `/root/sotc/pipeline-remaining.sh`, sha256-verified both sides, `bash -n` clean): per-boot host recovery (swap + Xvfb via `fix-x11.sh`), waits for the running ninja instead of racing it, an incremental verify pass that doubles as an automatic retry of a transiently failed link, then the `cmd_setup` sequence after `stage_boot` in driver order (capture KERNEL -> `celosia-env.sh recompile` -> MANAGER -> recompile -> GAMECORE -> recompile -> `vu1`), one `PS2X_INPUT_SCRIPT` progression retry per stalled capture, and per-stage driver-log snapshots (`stagegame-*.log`; the driver reuses one build log name across stages). Launched 2026-11-11 07:01 in background; master log `/root/sotc/logs/pipeline-remaining.log`. Also re-verified all three android-integration patches against the live `/root/celosia` tree with `git apply --check` (all OK on top of the developer's uncommitted edits in other files).

- **Attempt 6 BUILD_OK (2026-11-11 07:05)** - non-unity, 1567 targets, `-j2`; binary `ps2xRuntime/ps2EntryRunner` (17,944,488 bytes). The single compiler warning triaged as a GCC false positive (`sotc_native.cpp:438` writes `bone[k]`, but `v.bones` is clamped to <= 3 by `std::min(m.word(q + 12u), 3u)` before the loop, so `k` <= 2 and `bone[3]`/`weight[3]` are never overrun - KNOWN_ISSUES #13; do not patch upstream for it).
- **M4 prep research (read-only, while the build ran)**: the cross-compile's CMake source root is `/root/celosia/PS2Recomp` (celosia embeds PS2Recomp); the driver's `cmd_configure` shows the exact flags incl. `-DPS2X_RUNNER_DIRS=build/runner;build/vu1rec;game/overrides` wiring; live `ps2xRuntime/CMakeLists.txt` Android branches confirmed (raylib `PLATFORM=Android`, debug UI + FFmpeg off, `log android` link, SHARED runner + `-Wl,--undefined=ANativeActivity_onCreate` + `ps2_android_runtime.cpp`); `PS2X_DEFAULT_BOOT_ELF` is a cache STRING for the M5 bake-in. WSL has NO Android SDK/NDK (Windows NDK cannot drive a Linux build) -> a Linux NDK must be installed (choice recorded in NEXT_STEPS M4: 26.1.10909125 app-parity vs 28.2.13676358 scaffold). Include-path trap recorded: never put `neon-shim/` on an Android include path (SIMDe `arm_neon.h` would shadow the NDK's real one) - separate dir for the `mach-o/dyld.h` shim only. Repo's `third_party/PS2Recomp` scaffold confirmed present (`android/app/build.gradle` pins NDK 28.2).
- **pipeline-remaining progress**: verify pass no-op (07:05), **KERNEL captured 07:06** (32 MiB dump), stage_game-after-KERNEL running (SOTC.elf linked from 1 captured module; full runner rebuild - hours at `-j2`).

- **D20 recorded - NDK r26b (26.1.10909125) chosen for M4**, matching `app/build.gradle` for toolchain parity (the NDK CMake toolchain defaults to `c++_static`, so no shared-STL mixing either way); WSL has no SDK and the Windows NDK cannot drive a Linux build. Wrote `tools/capture-host/install-ndk.sh` (idempotent; verifies zip integrity, exact `Pkg.Revision`, clang; log `/root/sotc/logs/install-ndk.log`) and pre-created `/root/sotc/android-shim/mach-o/dyld.h` - the D16 shim placed WITHOUT the SIMDe `arm_neon.h`, because the `neon-shim/` dir must never shadow the NDK's real header on an Android include path. Fallback if r26b trips CMake 4.2 at configure: the scaffold's 28.2.13676358.

- **NDK installed + M4 fully scripted.** `install-ndk.sh` completed: NDK r26b (26.1.10909125, `Pkg.Revision`-verified, clang 17.0.2) at `/root/android-ndk-r26b` (log `logs/install-ndk.log`). Both patch series mirrored to `/root/sotc/patches-capture-host/` + `/root/sotc/patches-android-integration/` (stale `patchverify/` removed); Android-only dyld shim confirmed byte-identical at `/root/sotc/android-shim/mach-o/dyld.h`. Wrote + mirrored (sha256-verified) `configure-android.sh` - pipeline-done guards (process + `PIPELINE_DONE` marker, `FORCE=1` override), verify-or-skip the capture-host fixes on the live tree (they are the developer's edits, KNOWN_ISSUES #12; markers `vf0u`/`PS2X_VU_NEON_A32` confirmed present), marker-based android-series apply, then the NDK configure (android-28 = app minSdk, `ANDROID_STL=c++_static`, unity OFF, `-I/root/sotc/android-shim`, D11 policy flag, `PS2X_RUNNER_DIRS` wiring; `PS2X_DEFAULT_BOOT_ELF` deliberately unset until M5 fixes the extracted boot-tree path - applicationId `com.portsotc.mobile`) - and `build-android-runner.sh` (per-attempt log, `BUILD_OK`/`RUNNER_SO_PRESENT` markers, expects `libps2EntryRunner.so`). App facts pinned: minSdk 28; app native code uses `ANDROID_STL=c++_shared` while the runner uses `c++_static` - isolated STLs, no C++ objects cross the NativeActivity C ABI.

- **NDK toolchain smoke test - NDK_SMOKE_OK (2026-11-11 07:22)**. `tools/capture-host/tests/ndk-smoke.sh` (mirrored to `/root/sotc/ndk-smoke.sh`, log `logs/ndk-smoke.log`): NDK r26b `android.toolchain.cmake` + CMake 4.2.3 + Ninja + arm64-v8a + android-28 + the D11 policy flag -> `ELF 64-bit LSB, ARM aarch64, for Android 28, built by NDK r26b (10909125)` for both a SHARED library and a PIE executable. D20's NDK 28.2 fallback retired unless the real project configure hits something project-specific. Trivial CPU + disk load - safe (and run) while the pipeline builds.
- **M5 I/O model fully pinned (read-only research while the pipeline builds; recorded in NEXT_STEPS M5)**: `main()` takes an absolute `PS2X_DEFAULT_BOOT_ELF` as-is; `configureIoPathsFromElf` sets cdRoot = hostRoot = the boot ELF's directory and mcRoot = `<elfDir>/mc0`. File-content reads resolve ONLY against directory mounts (`PS2Vfs::resolveHostPath` over `PS2VfsMounts{hostRoot, cdRoot, mcRoot}`) - the ISO is never a file-content fallback, so M5 requires the FULL disc tree extracted to `<files>/disc/`. Guest-path resolution is robust (exact -> case-insensitive walk -> leaf-name index -> loose numeric key with `;1` stripped and leading zeros collapsed; `registerCdFile` in Support.h), so a plain faithful extraction with original filenames works. Memory cards: `mc0` beside the boot ELF (auto-created), port 1 -> sibling `mc1`. Raw `sceCdRead`: pseudo-LBN map over registered files first (synthetic LBNs assigned at registration), then direct reads from `cdImage` (the ISO) - which CANNOT be set on Android without a patch (no env var; `setIoPaths` has exactly one caller, `configureIoPathsFromElf`; a `:game` process would not inherit launcher-set env anyway). M6 must watch logcat for `sceCdRead unresolved LBN`; remedy if seen = small patch baking the ISO path (mirroring the `PS2X_DEFAULT_BOOT_ELF` pattern) + keeping the imported ISO at a fixed private path.

**State at session note:** pipeline RUNNING unattended (KERNEL done; stage_game-after-KERNEL rebuilding, 2334 targets). Commits this session: `643e6bb` icon, `2c5eddf` D17, `517a48f` patches, `6eca848` D18, `ccda8ad` 0003, `370e632` D19, `a213ede` attempt-6, `11fcfe4` D20, `76884bb` M4 scripts, plus the ndk-smoke + M5-research docs commit. On PIPELINE_DONE: run `configure-android.sh` then `build-android-runner.sh` (Milestone 4).

**Carry-overs:** B2 (RAM 1.7 GiB - build at `-j2`), B4 (no ARM64 device).

## Session 6 - 2026-11-18 (recovery audit, integration/icon docs, M3 stall evidence)

**Goal:** Resume from checkpoint, perform session recovery procedure, confirm M3 pipeline status, and record current project state honestly.

**Done:**
- Session recovery performed per `AGENTS.md`: read `docs/agent/PROJECT_STATE.md`, `NEXT_STEPS.md`, `BLOCKERS.md`, `KNOWN_ISSUES.md`, `CODEBASE_MAP.md`, `TECHNICAL_DECISIONS.md`; inspected git state (`main`, clean before this session except prior-session artifacts noted below) and recent history; audited developer assets (`original iso/SCUS_974.72.Shadow of the Colossus.iso`, 2,693,955,584 bytes, gitignored/protected; `icon/icon.png`, 908,646 bytes, tracked).
- Reviewed prior uncommitted recovery-session artifacts present in the working tree: `.gitignore` corrected to remove `/icon/` so ignore rules match tracked developer artwork policy (ISO protection preserved), `docs/THIRD_PARTY_INTEGRATIONS.md` (third-party evaluation/decision record: Celosia/PS2Recomp pinned in-repo components, RetroTouch candidate-not-yet-integrated, Paddleboat/AGDK samples/GameActivity/Oboe/SDL3/Ghidra evaluated with constraints and no new runtime integrations added), and `docs/ICON_INTEGRATION.md` (developer icon integration record linked to `docs/ICON_ASSET.md` and commit `643e6bb`).
- M3 WSL workspace audit (`\\wsl.localhost\Ubuntu\root\sotc`, filesystem evidence): no `PIPELINE_DONE`; only `build/captures/kernel.bin` (33,554,432 bytes, 2026-11-11 07:06:01) present; no `manager.bin`/`gamecore.bin`, no `logs/stagegame-*.log`, no VU1 completion artifacts found. `logs/pipeline-remaining.log` (733 bytes, last write 2026-11-11 07:08:24) shows `pipeline-remaining.sh` reached `stage_game after KERNEL` and started rebuilding `ps2EntryRunner`; `build/cmake/.ninja_log`, `.ninja_deps`, and `.ninja_lock` were last modified 2026-11-11 14:52:35 and `.ninja_log` tail records `ps2EntryRunner` runner-object compilation at that point, while `ps2xRuntime/ps2EntryRunner` remains the 2026-11-11 07:05:25 attempt-6 binary. `wsl -e`/process interrogation timed out in this environment, so live process state could not be confirmed; pipeline treated as stalled/pending manual verification rather than actively progressing.
- Agent memory updated for honest continuity: `docs/agent/PROJECT_STATE.md` and `docs/agent/NEXT_STEPS.md` now record M3 stall evidence and require manual WSL process verification before recovery/restart; this session entry appended.

**State at session end:** Working tree has prior-session doc/ignore changes plus this session's agent-memory updates (uncommitted). M3 remains incomplete and requires manual verification/recovery in WSL before M4 can proceed. No WSL pipeline commands were executed, no source/runtime integration changes were made, no builds/tests/releases were run, and no push/release was performed.

**Carry-overs:** M3 stalled/pending manual verification (D14/D15/D19 recovery path once idle), M4/M5/M6+ incomplete, B2 (WSL RAM/swap constraints), B4 (no ARM64 physical device), no authorization to push/publish releases.

## Session 7 - 2026-11-18 (professional repository presentation and documentation pass)

**Goal:** Improve public GitHub/repository presentation and documentation per the professional README/game-version/branding requirements, while keeping all claims honest and consistent with verified project state.

**Done:**
- Rewrote root `README.md` as a professional project entry point with centered header using the developer icon (`icon/icon.png`), clear project description, native/static-recompilation positioning, SCUS-97472 requirement visibility, honest status table (not playable, no verified public APK release, ARM64/gameplay unverified), releases/compatibility/build links, and structured sections (overview, status, features, requirements, compatibility, installation, controls, screenshots/media policy, build, architecture, roadmap, known issues, licensing, downloads, contributing, legal notice).
- Rewrote `docs/COMPATIBILITY.md` to explicitly document the initial target (PS2 USA retail `SCUS-97472`, boot `SCUS_974.72`, required modules/files), validation behavior, status definitions (identified/importable/core compatible/boot verified/gameplay verified), compatibility table, unsupported/unverified cases, integrity/BIOS-emulator clarifications, platform requirements, and honest verified/unverified limitations.
- Created public documentation set: `docs/INSTALLATION.md`, `docs/BUILDING.md`, `docs/CONTROLS.md`, `docs/PERFORMANCE.md`, `docs/TROUBLESHOOTING.md`, `docs/FAQ.md`, `docs/ROADMAP.md`, `docs/CONTRIBUTING.md`, `docs/releases/RELEASE_HISTORY.md`, and `docs/media/README.md` (with `docs/releases/` and `docs/media/` directories).
- Improved `docs/ARCHITECTURE.md` (updated overview/diagram, corrected stale verified-state claims, added D18 planned integration context and current reality notes) and rewrote `docs/THIRD_PARTY_LICENSES.md` for clearer current dependency/license/obligation tracking.
- Added cross-reference note to `docs/BUILDING_ANDROID.md` pointing to `docs/BUILDING.md`; updated `docs/ANDROID_PORTING_PLAN.md` metadata/phase summary for consistency with current roadmap/state.

**State at session end:** Documentation/presentation changes are in the working tree (uncommitted), along with prior uncommitted `.gitignore`/`docs/ICON_INTEGRATION.md`/`docs/THIRD_PARTY_INTEGRATIONS.md` artifacts. No source/runtime behavior changes, no builds/tests executed for this pass, no WSL pipeline recovery performed, no commits amended beyond local edits, and no push/release performed.

**Carry-overs:** Unchanged from Session 6 (M3 manual verification/recovery required, M4/M5/M6+ incomplete, B2/B4, no authorization to push/publish releases). README/repo presentation should be re-verified on GitHub after any push (relative links/icon rendering, release links).

## Session 8 - 2026-11-18 (documentation publication to GitHub)

**Goal:** Publish the completed documentation and repository presentation work to the official GitHub repository under explicit developer authorization, without publishing a fake release.

**Done:**
- Recovered Git state: `main` was clean at `aa3871a` (`docs: establish professional SotC Mobile project documentation`); `origin` already pointed to `https://github.com/zoddrun/Port-SotC-Mobile.git`.
- Fetched `origin/main` and found an unrelated single-commit history (`8371cbe Initial commit`, README only, no merge base). Amended the documentation commit message to `docs: publish professional SotC Mobile project documentation` (`42cf6c3`) and merged `origin/main` with `--allow-unrelated-histories`, keeping the project README; merge commit `50bd46c`. No force-push, no history rewrite of the remote.
- Pre-push protection audit: `original iso/` remains ignored via `.gitignore` line `/original iso/`; no ISO/BIOS/keystore/`local.properties`/secret files are tracked; `icon/icon.png` (908,646 bytes) remains tracked and is referenced by the README.
- Pushed `main` (`8371cbe..50bd46c`) to the official repository and verified the remote ref, commit, README blob, icon blob, and documentation files via the GitHub API.
- Verified unauthenticated public rendering: repository page, commit history, commit URL, Releases page, `docs/COMPATIBILITY.md`, `docs/agent/`, and raw `icon/icon.png` all return HTTP 200; README contains SCUS-97472, the releases link, the icon reference, and explicit "not playable" status.
- Repository visibility changed to **public** on developer instruction.
- No GitHub Release was created: the only available APK is the non-playable, debug-signed host shell (`app-debug.apk`, built 2026-11-11), and Section 9 of the publication task prohibits publishing an untested/non-playable APK. Playable Beta remains blocked on the native game core.

**State at session end:** Documentation published and verified on GitHub; working tree contains only this session's agent-memory update. No source/runtime changes, no builds/tests executed, no release published.

**Carry-overs:** M3 manual verification/recovery in WSL still required; M4/M5/M6+ incomplete; B2/B4 unchanged; first public release remains blocked on a verified playable core and APK.

## Session 9 - 2026-11-18 (repository audit, APK build, first platform preview release)

**Goal:** Audit the repository, polish public presentation, build and validate the real Android host APK, and publish the first honestly classified development preview (`v0.1.0-alpha.1`).

**Done:**
- Audited repository state, Gradle/AGP configuration, manifest, Kotlin/C++ sources, CMake, tests, docs, and release status. Confirmed no native game core is packaged and no gameplay exists; release classified as Alpha / Platform Preview.
- Toolchain: used JDK 17 (`C:\Users\maria\jdk-17`) because the default JDK 25 is not supported by Gradle 8.7/AGP 8.5.2.
- Ran `gradlew testDebugUnitTest` (49 tests, 0 failures, 0 skipped), `gradlew lintDebug` (pass), and `gradlew clean assembleDebug` (pass). APK: `app/build/outputs/apk/debug/app-debug.apk`, 5,887,019 bytes, SHA-256 `22CB48D8DB4A8F60ED191113B57DA0A3AECC26A12E5908BF9E0AAAA43CD2312F`.
- APK validation: package `com.portsotc.mobile`, version `0.1.0` (code 1), minSdk 28, targetSdk 34, native `arm64-v8a` + `x86_64`, adaptive launcher icon resources present, APK Signature Scheme v2 valid (debug certificate), no dangerous permissions declared.
- Emulator validation (Android 9 / API 28 `x86_64`, SwiftShader): install/launch OK; main screen, Settings, Controller settings, ISO import screen and SAF picker verified; diagnostics load the native host, start AAudio at 48 kHz stereo, and initialize EGL 1.4. GLES 3 config selection fails on this image because it reports OpenGL ES 2.0; the app handles it without crashing. API 37 AVD could not start (host has 3,790 MiB available vs 4,096 MiB required).
- Documentation updated: new `docs/releases/v0.1.0-alpha.1.md`; README release status, downloads, installation, and a genuine host-shell screenshot (`docs/media/host-shell-main.png`); `docs/INSTALLATION.md`, `docs/FAQ.md`, `docs/releases/RELEASE_HISTORY.md`, `docs/COMPATIBILITY.md` re-verification notes, `docs/media/README.md`, and stale remote-publication blocker.
- Repository presentation verified: icon tracked and referenced, README links checked, repo public.

**State at session end:** Source committed/pushed and GitHub Release `v0.1.0-alpha.1` published with the APK asset. No gameplay, ARM64 device validation, or native core claims were made.

**Carry-overs:** M3 pipeline recovery, M4 ARM64 cross-compile, M5 runtime integration, first boot/gameplay verification, and ARM64 hardware testing remain open. GLES 3 rendering still needs verification on a capable device/emulator.

## Session 10 - 2026-11-18 (product UI, settings system, control presets, pipeline recovery)

**Goal:** Turn the Android host into a credible product shell and recover the native recompilation pipeline, without faking gameplay.

**Done:**
- Recovered the WSL pipeline: activated the 8 GB swapfile, repaired Xvfb, restarted `pipeline-remaining.sh`. Confirmed live `pipeline-remaining.sh` + `ninja`/`cmake` processes; runner verification passed; KERNEL re-captured; currently rebuilding `ps2EntryRunner` for `stage_game after KERNEL`.
- Renamed the installed application label to `SOTC` (application ID unchanged).
- Rebuilt the launcher: wordmark, release subtitle, compact status card, single primary Play action (disabled unless game data and native runtime are present), contextual hint, details dialog, import/settings/controls actions, legal notice, build summary. Diagnostics removed from the launcher and reachable only via Settings -> Advanced.
- Rebuilt settings as an index plus per-category screens: General, Graphics, Performance, Audio, Controls, Storage, Advanced. Added SHA-256 game verification, remove-game-data confirmation, diagnostic report copy, capability readouts, and honest "not implemented" notes for frame limiting, refresh-rate control, Vulkan, and separate music/SFX volumes.
- Added touch presets (Classic/Modern/Compact/Custom). Presets only change geometry; button masks and stick sides are invariant. Editing a layout records Custom; reset records Classic.
- Added camera sensitivity, horizontal/vertical look inversion, radial touch deadzone, touch/gamepad input release on pause and focus loss, and automatic touch-control hiding when a gamepad is hot-plugged (`InputManager.InputDeviceListener`).
- Relabeled the in-session overlay as `HOST FPS` so host render statistics are not confused with game frame rate.
- Added 8 unit tests (TouchLayout presets, Settings defaults/sanitization). Total now 57 tests.
- Verified: `testDebugUnitTest` 57/57 pass, `lintDebug` pass, `assembleDebug` pass; APK badging shows label `SOTC`, package `com.portsotc.mobile`, native `arm64-v8a` + `x86_64`.

**State at session end:** UI/settings/control work is built and tested but not yet committed. Pipeline is running; no MANAGER/GAMECORE/VU1/`PIPELINE_DONE` yet. No emulator install of this build yet (host free RAM was ~250 MiB while the WSL build ran). No gameplay claim.

**Carry-overs:** monitor pipeline through MANAGER/GAMECORE/VU1; then M4 ARM64 cross-compile, M5 runtime integration, first boot, gameplay verification, ARM64 device testing; verify new UI on emulator when memory allows; commit/push only after review.

## Session 11 - 2026-11-18 (runtime host integration: disc tree, boot path, launcher gating)

**Goal:** Advance Milestone 5 host integration while the WSL recompilation pipeline rebuilds the runner, without claiming gameplay.

**Done:**
- Added `DiscTree` (marker format 1, bound to the image SHA-256, written atomically) and `DiscTreeExtractor`: staged extraction of the full ISO into `<files>/games/SCUS-97472/disc/`, per-file `.part` writes, unsafe-path rejection, free-space check, atomic publish, `mc0` creation, progress and cancellation. A ready tree is not re-extracted; a different image digest forces extraction. 5 new unit tests.
- Extended `GameLibrary`/`InstalledGame` with `discDirectory`, `bootElfFile`, `memoryCardDirectory`, and `isDiscTreeReady()`.
- Settings -> Storage now shows disc-tree status and the runtime paths, and exposes `PREPARE GAME DATA` / `RE-EXTRACT DISC DATA` as a cancellable progress dialog.
- Added `CoreProbe.isRuntimeInstalled` (`libps2EntryRunner.so`) alongside the ABI-v1 `libsotc_game.so` check.
- Added `GameNativeActivity` (NativeActivity, `android.app.lib_name=ps2EntryRunner`, `sensorLandscape`) and registered it in the manifest. It refuses to start without an installed game and a ready disc tree.
- Added JNI `NativeCore_nativeSetEnvironment` (`setenv`/`unsetenv`) and `NativeCore.setEnvironment`; `GameNativeActivity` publishes `SOTC_BOOT_ELF` before `super.onCreate` loads the runner.
- Generated and byte-verified `tools/android-integration/patches/0004-android-boot-path.patch` (works in both orders with 0003) and added its marker to `apply-patches.sh`.
- Rewrote launcher gating: no game -> disabled Play; runtime missing -> disabled Play + details dialog; runtime present but tree missing -> Play opens Storage; ready -> Play opens `GameNativeActivity`. Details dialog reports runtime and disc-tree state.
- `CoreProbe` now detects a native library both in the extracted `nativeLibraryDir` and inside the APK for the device's ABIs, because the app uses uncompressed native libraries (`useLegacyPackaging=false`). Added `CoreProbeTest` (5 tests).
- Added `tools/android-integration/package-runner.sh`: verifies the AArch64 machine type and the `ANativeActivity_onCreate` export, copies the runner to `app/src/main/jniLibs/arm64-v8a/`, and prints size, SHA-256, and `NEEDED` libraries. The `.so` remains gitignored. Added `jniLibs/README.md` and documented the packaging step.
- Verified: `testDebugUnitTest` 67/67 pass, `lintDebug` pass, `assembleDebug` pass. APK label `SOTC`, package `com.portsotc.mobile`, native `arm64-v8a` + `x86_64`, SHA-256 `F8F152F13DAD402F125C81CD2FBA8D2E6D1BFD1168B9DC522D036B9D48F9B388`.
- Updated `CORE_ABI.md`, `ARCHITECTURE.md`, `RECOMPILATION_PIPELINE.md`, `NEXT_STEPS.md`, `PROJECT_STATE.md`.

**State at session end:** Host integration for D18 is implemented and tested at the unit/build level. The pipeline is still rebuilding the runner for `stage_game after KERNEL`; no `libps2EntryRunner.so` exists, so Play stays disabled in a default build. No gameplay claim. Work is committed locally, not pushed.

**Carry-overs:** monitor the pipeline through MANAGER/GAMECORE/VU1/`PIPELINE_DONE`; then M4 cross-compile, package the runner, bridge touch/gamepad input, first boot and gameplay verification; verify the new screens on the API 28 emulator when host RAM allows.

## Session 12 - 2026-11-18 (gameplay touch overlay, runtime settings handoff)

**Goal:** Close the D18 input gap inside the runner (Android views cannot be layered over a NativeActivity window) and deliver the launcher's control settings to the runtime, while the WSL pipeline rebuilds the runner.

**Done:**
- Added `tools/android-integration/patches/0005-android-touch-input.patch` (743 lines): new `include/runtime/ps2_android_input.h`; the full implementation replaces the 3-line `ps2_android_runtime.cpp` stub; a merge block in `ps2_pad.cpp`; `update()` / `drawOverlay()` calls in `ps2_runtime.cpp`'s host frame.
  - `ps2_android_input` samples raylib's `CORE.Input.Touch` (which `native_app_glue` fills from every motion event) once per host frame on the render thread, publishes a mutex-guarded pad state, and draws the overlay with raylib in the same GL frame.
  - `PSPadBackend::readState` merges the state into the PS2 wire format at each VBlank (`0x73` marker, active-low bits, `0x80`-centred sticks) next to the gamepad/keyboard paths; a `static_assert` keeps one wire bit per logical button.
  - Layout is sized from the screen's shorter side (phones, tablets, 16:9 and 21:9 share proportions): sticks bottom-left/right, D-pad above the left stick, face buttons above the right, L1/L2 and R1/R2 top corners, Select/Start top centre. Classic/Modern/Compact presets; L3/R3 have no on-screen control; the overlay is suppressed while the runtime's own menu has input.
  - Knobs: `SOTC_TOUCH_OVERLAY`, `SOTC_TOUCH_PRESET`, `SOTC_TOUCH_HIDE_WITH_GAMEPAD`, `SOTC_TOUCH_SCALE`, `SOTC_TOUCH_ALPHA`, `SOTC_TOUCH_DEADZONE`, `SOTC_LOOK_SENSITIVITY`, `SOTC_INVERT_LOOK_X`.
- Verified patch 0005: `apply-patches.sh` applies 0001-0005 to a clean `f8c269e` worktree (`/root/sotc/patchcheck`) and a second run reports every patch already applied. Compile-verified with NDK r26b for `aarch64-linux-android28`: `ps2_pad.cpp` (64,736 B) and `ps2_android_runtime.cpp` (21,856 B) at `-O2 -Wall -Wextra`; `ps2_runtime.cpp` at `-O0` with only its pre-existing warnings; `llvm-nm` shows `ps2_android_input::update/state/drawOverlay` defined and referenced.
- Added `core/RuntimeEnvironment.kt`: owns the environment variable names, publishes the boot path plus the control knobs, formats decimals with `Locale.US` (`std::atof` stops at a comma), and returns the names the process rejected. `GameNativeActivity` now publishes all of them before `super.onCreate` and logs exactly what failed.
- Added the runtime stdio redirect: Android discards a framework-started app's stdio, so `GameNativeActivity` calls the new JNI `NativeCore.nativeRedirectStdio` (`freopen` + line buffering) before the runner library loads and writes every launch to `<files>/logs/game-<yyyyMMdd-HHmmss>.log`. This is the only trace the first boot will leave if the runtime fails; `llvm-nm` confirms the symbol in `libsotc_core.so`.
- Added `RuntimeEnvironmentTest` (7 tests): boot path included/omitted, overlay off, comma-locale decimals, custom preset and inverted look pass-through, unknown preset -> classic, publish failure reporting.
- Updated docs: `CONTROLS.md` (two overlays, runner layout, preset table, knob table, limitations), `RECOMPILATION_PIPELINE.md` (patch 0005 + verification), `ARCHITECTURE.md` (D18 input path, diagram), `tools/android-integration/README.md` (patch table, host side, remaining gaps), `README.md`, `THIRD_PARTY_INTEGRATIONS.md` (RetroTouch no longer a candidate for the gameplay window), `TECHNICAL_DECISIONS.md` (D18 amendments), `KNOWN_ISSUES.md`, `NEXT_STEPS.md`, `CODEBASE_MAP.md`, `PROJECT_STATE.md`.
- Verified: `testDebugUnitTest` 74/74 pass, `lintDebug` pass, `assembleDebug` pass. APK label `SOTC`, package `com.portsotc.mobile`, native `arm64-v8a` + `x86_64`, 6,236,524 bytes, SHA-256 `1E6D5D01CDA9F299C9C951115D6D7BE497CEA25DD413CAF46CCF9AF3F024B3F4`.
- Pipeline (unchanged): still rebuilding `ps2EntryRunner` for `stage_game after KERNEL` (609 objects at 11:05, 749 at 11:12); no MANAGER/GAMECORE/VU1/`PIPELINE_DONE` yet.

**State at session end:** The gameplay input path exists and compiles for ARM64, and the launcher's controls reach the runner through the environment. Nothing has been linked into a runner or run on a device: overlay geometry, multitouch ownership and the pad merge remain unverified, the Custom preset falls back to Classic geometry, and L3/R3 are gamepad-only. No gameplay claim. Work is committed locally, not pushed.

**Carry-overs:** monitor the pipeline through MANAGER/GAMECORE/VU1/`PIPELINE_DONE`; then M4 cross-compile (patches 0001-0005), package the runner, first boot, then verify rendering/audio/lifecycle and finally the touch overlay.

## Session 13 - 2026-11-18 (runtime log capture, audio handoff, offline Android configure)

**Goal:** Make the first boot diagnosable and make the launcher's Audio settings real, without disturbing the running capture pipeline.

**Done:**
- Kept the runtime's console: `NativeCore.redirectStdio` / JNI `nativeRedirectStdio` (`freopen` + line buffering) runs before the runner library loads and writes every launch to `<files>/logs/game-<yyyyMMdd-HHmmss>.log`. Verified the symbol is exported by `libsotc_core.so` for `arm64-v8a`.
- Added `tools/android-integration/patches/0006-android-audio-volume.patch` (1,162 B, LF, ASCII only): one hunk in `ps2_runtime.cpp` applying `SOTC_AUDIO_VOLUME` (0..1, rounded to the runtime's 0..10 scale) right after `hostmenu::load()`. Added its marker to `apply-patches.sh`; `apply-patches.sh` now applies 0001-0006 to a clean `f8c269e` worktree and a second run is a no-op. The inserted statements pass a standalone `-Wall -Wextra` syntax check; the full TU was deliberately not recompiled during the pipeline build (1.7 GiB WSL host).
- `core/RuntimeEnvironment.kt` publishes `SOTC_AUDIO_VOLUME` (`0.000` when audio is disabled, `Locale.US` decimals); `RuntimeEnvironmentTest` grew to 9 tests (mute, comma locale).
- Settings -> Audio retitled: "Game audio" toggle and a caption stating that the volume is applied when a session starts and that the runtime's own menu can change it for that session. Settings -> Graphics caption now states that resolution/linear filtering apply to the engine diagnostics view, not the runner.
- `tools/capture-host/configure-android.sh` now also reuses `/root/sotc/patchdev/sse2neon` (v1.9.1) via `-DFETCHCONTENT_SOURCE_DIR_SSE2NEON`, so the ARM64 configure needs no network and the version is pinned by content. `bash -n` passes; the copy in `/root/sotc` is byte-identical (SHA-256 `71891962807CF0F4095945630F183618D410DB409865C8F10D958A3F8C11EDA6`).
- Updated docs: `ARCHITECTURE.md` (stdio log, audio routing), `BUILDING_ANDROID.md` (game log path and how to read it), `TROUBLESHOOTING.md` (gameplay-window diagnosis), `RECOMPILATION_PIPELINE.md` (patch 0006, local source reuse, verification honesty), `tools/android-integration/README.md` (0006 row, six patches, verification state), `TECHNICAL_DECISIONS.md` (D18 amendments), `NEXT_STEPS.md`, `PROJECT_STATE.md`.
- Normalized `PROJECT_STATE.md` and this log from double-encoded UTF-8 (dashes, arrows and quotes stored as mojibake) to plain ASCII punctuation, so the agent memory renders correctly in any editor and every byte is English ASCII.
- Verified: `testDebugUnitTest` 76/76 pass, `lintDebug` pass, `assembleDebug` pass.
- Pipeline: still rebuilding `ps2EntryRunner` for `stage_game after KERNEL` (1,140/2,743 objects at 11:32, 1,296 at 11:40); no MANAGER/GAMECORE/VU1/`PIPELINE_DONE` yet.

**State at session end:** The launcher's audio setting and the runtime's console are wired for the first boot, and the Android configure is offline-safe. Nothing has been linked into a runner or run on a device, so the volume mapping and the log contents remain unverified. No gameplay claim. Work is committed locally, not pushed.

**Carry-overs:** monitor the pipeline through MANAGER/GAMECORE/VU1/`PIPELINE_DONE`; then M4 cross-compile (patches 0001-0006), package the runner, first boot, verify rendering/audio/lifecycle from the new log, then the touch overlay and the volume handoff.

## Session 14 - 2026-11-18 (in-app runtime log viewer, codebase map rewrite)

**Goal:** Make the first boot's runtime log readable on the device itself, and correct the agent codebase map so it describes the code that actually exists.

**Done:**
- Added `core/GameLogs.kt`: finds the newest `files/logs/game-*.log` by modification time, reads the last 64 KiB of it, drops a partial first line when the file had to be truncated, and formats sizes with `Locale.US` units.
- Added the Settings -> Advanced `GAME LOG` card: it names the newest session file and its size, `VIEW LATEST GAME LOG` opens the tail in a selectable monospace dialog, and `COPY LATEST GAME LOG` puts it on the clipboard. Both actions are disabled when no session has run.
- Added `GameLogsTest` (8 tests): newest-file selection with decoy files, no-session cases, whole-file tail, line-boundary truncation, empty and missing files, locale-independent sizes, description text.
- Rewrote the `app/` section of `CODEBASE_MAP.md` against the real tree: package `com/portsotc/mobile`, `core/`, `input/`, `nativebridge/`, `ui/`, the current cpp layout and the runner packaging step. The previous section still described `libport-native`, `engine/EngineHandle.kt` and a `disc/` directory under the wrong package.
- Updated `BUILDING_ANDROID.md` (Settings -> Advanced viewer) and `TROUBLESHOOTING.md` (read the log on the device first).
- Aligned both native libraries for 16 KB page-size devices: `app/src/main/cpp/CMakeLists.txt` and the runner configure now pass `-Wl,-z,max-page-size=16384` (NDK r26b still defaults to 4 KB, and Android 15 can run a 16 KB kernel). Verified on the rebuilt `libsotc_core.so` for `arm64-v8a`: every LOAD segment reports align `0x4000`.
- Added `tools/android-integration/verify-runner.sh`: reports size and SHA-256, then checks ELF64, AArch64, DYN, `ANativeActivity_onCreate`, the patch-0005 touch symbols, `libc++_shared.so` dependency and 16 KB LOAD alignment, ending in `RUNNER_VERIFY_OK` or `RUNNER_VERIFY_FAILED`. Exercised against the app's `libsotc_core.so`, where it correctly fails the two runner-only checks and passes the rest. Documented in `BUILDING_ANDROID.md`, `tools/android-integration/README.md`, `CODEBASE_MAP.md` and `NEXT_STEPS.md` M5.
- Verified: `testDebugUnitTest` 84/84 pass, `lintDebug` pass, `assembleDebug` pass. Debug APK 6,237,436 bytes, SHA-256 `6018C6B380F6918BB3E843621F64AE926D179E72576F103A043DECF56EB46260`.
- Pipeline: `stage_game after GAMECORE` finished at 19:16 (runner 68,673,344 bytes). The VU1 stage ran the autotest, which rendered a real frame (`shots/run/frame_8.png`, 512x448, 2,032 distinct colours, mean luminance 39.9; frames 0-7 blank), and emitted 12 `vu1rec_*`/`vu1fast_*` sources. The final rebuild finished at 20:34 and `PIPELINE_DONE` was logged: x86 runner 72,237,696 bytes, 9,024 functions.
- Milestone 4 started on its own: `android-after-pipeline.sh` saw the marker, but the configure aborted in one second because its guard required the mirrored `apply-patches.sh` files to be executable and a Windows copy carries no exec bit. The guard now tests `-f` (the appliers are always invoked as `bash <file>`) and the WSL mirrors were chmodded. The retry reached `CONFIGURE_OK` at 20:35:36 for `arm64-v8a` (NDK r26b, raylib and sse2neon reused locally, `-Wl,-z,max-page-size=16384`, unity OFF); the ARM64 `ps2EntryRunner` build is running 9,334 targets at `-j2`.

**State at session end:** The runtime's log is reachable from the app without a PC, which is the first thing the first boot will need. The viewer itself has not run on a device, so its dialog layout and clipboard behavior remain unverified. No gameplay claim. Work is committed locally, not pushed.

**Carry-overs:** monitor the pipeline through the LTO link, VU1 and `PIPELINE_DONE`; then M4 cross-compile (patches 0001-0006), package the runner, first boot, verify rendering/audio/lifecycle from the new log, then the touch overlay and the volume handoff.
## Session 15 - 2026-11-18 (first complete ARM64 build of the runner)

**Goal:** Finish the ARM64 cross-compile of the recompiled game core and get a verified, shippable `libps2EntryRunner.so` into the APK.

**Done:**
- Patch 0007 (`0007-android-no-execinfo.patch`): the first ARM64 compile stopped at 67 of 9,334 objects because `EeScheduler.cpp` calls `backtrace()` behind a `!defined(__vita__)` guard while bionic declares it only from API 33 and the app targets 28. Both guards now also exclude `__ANDROID__`. Reverse-checked, marker added to `apply-patches.sh`, committed as `59c5a3d`.
- Patch 0008 (`0008-android-format-security.patch`): the build then stopped at 9,264 because the NDK compiles with `-Werror=format-security` and the variadic `sfmt105` forward in `game/overrides/sotc_native.cpp` passes its format string as a pointer. A scoped `#pragma clang diagnostic` waives only those two forwards; every call site already passes a literal. Reverse-checked, marker added, mirrored.
- The ARM64 `ps2EntryRunner` build finished at 22:22 after 9,333 objects and a 55-minute thin-LTO link: `libps2EntryRunner.so` = 777,574,008 bytes with debug info, SHA-256 `289f4bab35fb84456be9d9bb618bcede3cd62d8d934857bc0f2848775a0d2fb8`, ELF64 AArch64, Android 28, BuildID `8c5f7557f979db6ad9bcc3b64785c64f6d83413c`. WSL dropped the build twice (`WSAETIMEDOUT`, then SIGTERM during the link); ninja resumed both times. `setsid` and the 8 GB swapfile are required.
- Fixed two `set -o pipefail` + `grep -q` bugs in `verify-runner.sh` and `package-runner.sh`: the symbol list is 1.5 MB, grep exits on the first match, and printf's SIGPIPE turned present symbols into false failures. Both now capture the list and use here-strings. `verify-runner.sh` reports `RUNNER_VERIFY_OK`: ELF64, AArch64, DYN, 16 KB LOAD alignment, `ANativeActivity_onCreate` and `ps2_android_input::update/state/drawOverlay` exported, no `libc++_shared.so`, links `libandroid.so`.
- `package-runner.sh` now strips its copy with `llvm-strip --strip-unneeded` (777,574,008 -> 107,384,168 bytes, SHA-256 `134f2fbf3b0a5f260fff8a01b5f36221aaeec377242b602b7f6952b18b9aeb8c`) and re-runs the verifier on the packaged file. The unstripped library stays in the build tree for symbolisation; `SOTC_STRIP=0` packages it verbatim. The remaining size is the recompiled code (`.text` 58 MB) plus the 40.7 MB `g_ps2RecompiledFunctionTable` in `.data`.
- Packaged the stripped library into `app/src/main/jniLibs/arm64-v8a/` and rebuilt the debug APK: 114,562,620 bytes, SHA-256 `ACE1293446E7B90F98342D2EDD9797F5193B5A83451A7C0DA0EF7E7F61A4ED18`, containing `lib/arm64-v8a/libps2EntryRunner.so` (107,384,168 bytes, stored uncompressed because `useLegacyPackaging = false`).
- Updated `tools/android-integration/README.md`, `docs/RECOMPILATION_PIPELINE.md`, `BUILDING_ANDROID.md`, `CONTROLS.md`, `KNOWN_ISSUES.md`, `app/src/main/jniLibs/README.md`, `PROJECT_STATE.md` and `NEXT_STEPS.md` with the build result, the two new patches and the stripping step.

**State at session end:** The ARM64 game core exists, verifies and ships inside the APK. It has never been loaded: no ARM64 device is connected, and the emulators on this host cannot stand in (API 37 has insufficient RAM, API 28 reports OpenGL ES 2.0 only). No gameplay claim.

**Carry-overs:** first boot on a capable ARM64 device (M6) - import the disc, `PREPARE GAME DATA`, Play, then read `<files>/logs/game-*.log`; verify rendering, audio, lifecycle and the touch overlay (patch 0005) and the volume handoff (patch 0006). Publish only with device evidence and explicit authorization.

## Session 16 - 2026-11-19 (cross-vendor GPU compatibility, frame pacing, emulator evidence)

**Goal:** Remove the Qualcomm-agnostic-but-ES3-only assumption from the host renderer, add real performance controls, and get honest runtime evidence from the only environment available (a `x86_64` API 28 emulator).

**Done:**
- Graphics floor lowered to OpenGL ES 2.0. `AndroidManifest.xml` declares `android:glEsVersion="0x00020000"`; `CMakeLists.txt` links `GLESv2`; `gl_util.h`, `render_target.*` and `diagnostics_scene.*` use `GLES2/gl2.h`.
- `EglRenderer` now tries an ES 3 config, context, surface and `eglMakeCurrent`, and on any failure retries the whole sequence for ES 2 (`EGL_OPENGL_ES2_BIT`, client version 2). It reports the backend it actually obtained through an atomic enum, so `backendName()` is safe from other threads. `Force OpenGL ES 2.0` (persisted as `force_opengl_es2`) skips the ES 3 attempt for a session.
- Both shader programs rewritten to GLSL ES 1.00 (`#version 100`, `attribute`/`varying`/`gl_FragColor`, `texture2D`) with a VBO fullscreen triangle instead of `gl_VertexID` and VAOs. `diagnostics_scene` guards `highp` with `GL_FRAGMENT_PRECISION_HIGH`. `render_target` uses `GL_RGBA` as the internal format so ES 2 accepts it.
- New `engine/egl_display_lock.h`: a process-wide mutex around EGL display lifecycle calls, because `eglInitialize`/`eglTerminate` are display-scoped and the render thread and the capability query both touch them.
- New `engine/gpu_info.h/.cpp`: creates a short-lived offscreen pbuffer context (ES 3 first, then ES 2; progressively simpler configs) and reports backend, vendor, renderer, version, GLSL version, max texture size, max vertex attribs, extension count and error as `key=value` lines. It leaves the display initialized on purpose so it cannot tear down the render thread's context.
- New `core/GpuInfo.kt` (parser + summary, 5 tests) and `core/GraphicsPreset.kt` (Battery/Balanced/Performance over real settings, 4 tests). Settings -> Graphics now shows the reported Android ES level, the real driver strings and the force-ES2 toggle; Settings -> Advanced shows the same report; the copied diagnostic report includes it.
- `Engine` gained adaptive resolution (5% steps every 0.75 s against the frame budget, 25% floor, ceiling = configured scale capped by thermal status), a frame limit (0/60/30 via `sleep_until`, simulation delta stays wall-clock based), a thermal render-scale ceiling (100/100/85/70/60/50% for status 0-6), and a low-memory mode that releases the offscreen target and renders directly to the window. `statusJson()` and the debug `Frame stats:` line expose all of it.
- `Settings` gained `adaptiveResolution`, `frameLimitFps`, `forceEs2` with persistence and sanitization (6 `SettingsTest` cases). `GameActivity` polls `PowerManager.currentThermalStatus` with the statistics loop (API 29+, no listener leak) and forwards `onTrimMemory`/`onLowMemory`.
- Verified: `testDebugUnitTest` 95/95 pass, `lintDebug` pass, `assembleDebug` pass. Debug APK 114,562,620 bytes, SHA-256 `D733DD5F95F50DA39F5BA7233E7DF72687B88BE8713CD90A2C4E2BE7921CA6ED`.
- Emulator run (`Sotc_API_28`, `x86_64`, GLES 2.0 / `ro.opengles.version=131072`, SwiftShader, `-memory 1024`): install OK; Main/Settings/Graphics/Performance/Advanced launched and navigated; the capability query returned `OpenGL ES 2.0`, vendor `Google (Google Inc.)`, renderer `Android Emulator OpenGL ES Translator (Google SwiftShader)`, GLSL `OpenGL ES GLSL ES 1.0.17`, texture 8192, 16 attributes, 28 extensions.
- ES 2 renderer verified end to end: `eglChooseConfig failed: 0x3000` -> `no EGL config for OpenGL ES 3.0; trying the next API` -> ES 2 config/context/surface -> `Renderer backend: OpenGL ES 2.0`; shaders compiled; frames rendered at 1280x720 with a 100% non-black screenshot (top 39,41,43 / middle 59,57,50 / bottom 18,21,23, max 232,225,210) at 25% internal scale, ~13-15 fps under SwiftShader.
- Adaptive resolution observed: `26% -> 25%` at 50.6 ms smoothed against a 16.7 ms budget, then clamped at the floor.
- Low-memory path observed with `am send-trim-memory com.portsotc.mobile RUNNING_LOW`: `Low-memory notification received` -> `Offscreen render target released; rendering directly to the window`; the session kept rendering (at full surface size, ~2-3 fps, which is the expected trade).
- Preset persistence observed: tapping BATTERY SAVER wrote `resolution_scale_percent=50`, `adaptive_resolution=true`, `frame_limit_fps=30`; the UI showed `CURRENT - BATTERY SAVER - 50% - 30 fps`; the next session started with low-memory mode cleared.

**State at session end:** The host renderer now runs on the widest Android graphics floor and has real, persisted performance controls. The ES 2 path, the GPU query, adaptive resolution, the low-memory fallback and preset persistence are runtime-verified on an emulator. The ES 3 path, the frame limiter and the thermal ceiling are compiled but have no runtime evidence (B5), and none of the controls reach the raylib-owned runtime loop. Gameplay is still unverified. Work is committed locally, not pushed.

**Carry-overs:** verify the ES 3 path, the frame limit and the thermal ceiling on a device that accepts ES 3 and can exceed 30 fps; extend the pacing controls into the runner; then the first boot and gameplay verification (M6/M7).

## Session 17 - 2026-11-19 (upstream platform research C-H)

**Goal:** Research upstream repositories C-H and produce a detailed English report without modifying source code.

**Done:**
- Resolved immutable commit SHAs, default branches, licenses, recursive trees, and exact raw source paths for PS2Recomp, android/games-samples, Google Oboe, sotc-vibe-pc, PCSX2, and ANGLE.
- Compared each repository against the baseline Gradle 8.7 / AGP 8.5.2 / Kotlin 1.9.20 / NDK 26.1.10909125 / minSdk 28 / compileSdk+targetSdk 34 / CMake 3.22.1 / 16 KB page-size flag.
- Wrote `build/research/upstream-platform.md` with provenance, exact file lists, version-conflict matrix, architecture fit, and explicit ADOPT / EVALUATE LATER / REJECT decisions.
- Key decisions: keep the current toolchain and app shell; use only low-risk patterns (logcat bridge, API guards, page-size awareness); evaluate ADPF/Game Mode/Oboe/golden-run ideas later; reject emulator backends, bundled game ELFs, Oboe as a second audio engine, and ANGLE/PCSX2 as runtime components.

**State at session end:** Research deliverable complete. No production source files were modified. No commit was created.

**Carry-overs:** apply the report's decisions only when a measured need and a separate implementation task exist; do not upgrade Gradle/AGP/NDK or add upstream frameworks from research alone.

## Session 18 - 2026-11-19 (runner integration research)

**Goal:** Research the minimal integration between Android settings and the real raylib-owned game loop, with one owner for simulation, rendering, presentation, pacing, and dynamic resolution.

**Done:**
- Read all eight Android integration patches, `apply-patches.sh`, `README.md`, `verify-runner.sh`, `package-runner.sh`, the Kotlin environment/settings/activity/log sources, and the host `Engine` / `EglRenderer` reference code.
- Verified upstream paths and functions at celosia `f8c269e` (`ps2_runtime.cpp`, `native_renderer.cpp`, `native_tuning.h`, `host_menu.cpp`, `ps2_stats.h`, `CMakeLists.txt`) and raylib 5.5 (`rcore_android.c`, `rcore.c`).
- Wrote `build/research/runner-integration.md` with the single-owner architecture, environment-only interface, settings feasibility matrix, instrumentation design, no-phone regression plan, and rebuild/revert/reproducibility risks.
- Key decisions: keep raylib NativeActivity as the only gameplay loop; do not add JNI; do not use `SOTC_CAP20`; add one proposed celosia patch for `SOTC_FRAME_LIMIT`, `SOTC_ADAPTIVE_RESOLUTION`, and a 5-second `[pace]` log; leave the host `Engine` as diagnostics/ABI-v1 only.
- Recorded the packaged baseline as 107,384,168 bytes, SHA-256 `134F2FBF3B0A5F260FFF8A01B5F36221AAEEC377242B602B7F6952B18B9AEB8C`.

**State at session end:** Research deliverable complete. No production source files were modified and no commit was created. Device verification remains outstanding.

**Carry-overs:** implement the report only as a separate, patch-gated task; preserve the packaged runner until a rebuilt artifact passes the full no-phone regression gate; perform first-boot verification on ARM64 hardware before any gameplay claim.

## Session 19 - 2026-11-19 (upstream Celosia and R36S fork research)

**Goal:** Inspect upstream `HenryKun55/celosia` and the `amarqs182/celosia` R36S fork, then write a detailed English report to `build/research/upstream-celosia.md` without modifying production source files.

**Done:**
- Resolved default branches, commits, recursive trees, and GPL-3.0 license metadata before fetching source files.
- Recorded the deleted `r36s` branch limitation with exact 404/422 API errors and used fork `main` plus recorded merged PR head SHAs instead.
- Documented upstream native static-recompilation architecture, minimal Android support, R36S platform constraints, and post-pin correctness/reliability commits.
- Classified portable ideas separately from Linux/KMSDRM/SDL2/Mali-specific code and from runner-rebuild versus Android-host work.
- Quoted short, line-referenced snippets for measurement tooling, release gating, PGO/LTO/hot ordering, GLES batching/sorting, frame pacing, fastmem, and native decompilation.
- Wrote the report in English with measured R36S results, exact inspected file lists, limitations, and explicit no-verbatim-copy guidance.

**State at session end:** Research deliverable complete. No production source files were modified. No commit was created.

**Carry-overs:** treat R36S numbers as device-specific evidence only; reproduce any adopted optimization on Android hardware after a controlled `/root/celosia` runner rebuild.

## Session 20 - 2026-11-19 (runtime integration, audit expansion, release v0.2.0-alpha.1)

**Goal:** Complete the host/runtime audit expansion, connect real settings to the raylib-owned loop, fix 16 KB packaging, rebuild and verify the runner without a phone, then publish an honestly classified new preview.

**Done:**
- Added four audit docs: `UPSTREAM_REPO_AUDIT.md`, `GPU_COMPATIBILITY_MATRIX.md`, `PERFORMANCE_ARCHITECTURE.md`, `ANDROID_RUNTIME_INTEGRATION.md`, plus three research reports copied under `docs/research/`.
- Extended `RuntimeEnvironment.kt` with frame limit, adaptive resolution, render scale, perf overlay, stats and graphics() contract; 105 JVM tests pass. Corrected settings captions for next-session-only behaviour, runner clamps and host-only scope.
- Generated patch `0009-android-runtime-pacing.patch` (SHA-256 `bd9c5c7a...de74e`), verified forward/reverse apply and byte-for-byte restore at celosia `f8c269e`, then rebuilt the ARM64 runner: `BUILD_OK`, unstripped 777,586,120 bytes, stripped 107,386,928 bytes, SHA-256 `1DD555D4...DBD2`, new BuildID `6a70d316...e4ce`, `RUNNER_VERIFY_OK (0 warnings)`. Packaged into `jniLibs/arm64-v8a`.
- Extended the native GPU detector with EGL identity/extensions, GL limits, advertised timer/fence flags and shader/FBO/colour/depth/render-to-texture self-tests; `GpuInfo.Tier` now derives from passing probes, never vendor strings.
- Switched `app/build.gradle` to `c++_static`, removing the 4 KB-aligned `libc++_shared.so`. Added `verify-apk.py` as an APK-wide native gate: `APK_NATIVE_VERIFY_OK`, 0 failures/0 warnings. Added `smoke-runtime-pacing.sh` for host x86 patch-0009 assertions.
- `lintDebug` and `assembleDebug` pass. Final APK: 112,640,655 bytes, SHA-256 `F7A70D12...367D8`, version 0.2.0/code 2.
- Published `v0.2.0-alpha.1` (Runtime Integration Preview) with honest notes: host + packaged runtime, not playable, no device evidence. Updated README, FAQ, INSTALLATION, RELEASE_HISTORY and all agent state docs. See `NO_PHONE_VERIFICATION_REPORT.md` for the evidence ladder.

**Verification status at session end:** ARM64 runner compiled, packaged, JVM-tested, lint-clean, APK-verified; API 28 emulator launcher check deferred under host memory pressure. No ARM64 device test: first boot, gameplay, rendering, audio, touch overlay, lifecycle, saves, thermals, ES 3 path and on-device frame limit remain explicitly unverified. No gameplay claim made.

**Carry-overs:** capture the host x86 `[pace]` smoke result; re-check the launcher on the API 28 emulator when host memory frees; first-boot verification on real ARM64 hardware remains the only path to a playable claim.

## Session 21 - 2026-11-19 (host smoke completion and adaptive-resolution finding)

**Goal:** Finish the outstanding host x86 patch-0009 smoke gate, diagnose any failure honestly, and leave the published release documentation accurate.

**Done:**
- The host x86 thin-LTO link completed (~72 min) and the first smoke run returned `HOST_SMOKE_0009_FAILED` (7/9): `[pace] limit=0` missing and `[scale-adaptive] on:` missing.
- Diagnosed both failures from `/root/sotc/logs/smoke-0009/*.log` and the pinned source WITHOUT touching the shipped ARM64 runner:
  - `[pace] limit=0`: the 14 s timeout was too short for the uncapped case under llvmpipe; a 45 s isolated re-run produced eight `[pace] limit=0` records. Raised the harness timeout to 30 s.
  - `[scale-adaptive] on:`: the host desktop-GL build never populates `perfSnapshot().frameMs` (stays `0.000` even with `SOTC_PERF_OVERLAY=1` + `SOTC_FRAME_LOG=1`), so `adapt()`'s `frameMs > 0` guard suppresses the activation line. Replaced that assertion with the host-verifiable `adaptive=1`/`adaptive=0` field in the `[pace]` record; actual scale changes remain device-only.
- Re-ran `tools/android-integration/smoke-runtime-pacing.sh`: `HOST_SMOKE_0009_OK`, 9/9 assertions. Timer logs confirmed 33.333 ms (limit 30), 16.667 ms (limit 60) and 0.000 ms (uncapped).
- Recorded a genuine finding in `KNOWN_ISSUES.md` item 8: adaptive resolution depends on the perf-statistics path (`SOTC_PERF_OVERLAY` = "Show statistics", default off) while `adaptiveResolution` defaults on, so it is a no-op with default settings. Documented three options; did not rebuild the APK for a device-unverified performance feature in a prerelease. Added the caveat to the release notes (repository + GitHub release body).
- Updated `NO_PHONE_VERIFICATION_REPORT.md` (HOST-RUNTIME-TESTED = Yes; full failure/root-cause record), `PROJECT_STATE.md`, `NEXT_STEPS.md` (items 5-6 done) and the release verification table.

**Verification status at session end:** all no-device gates green — COMPILED/PACKAGED/UNIT-TESTED/LINT/APK-VERIFIED/HOST-RUNTIME-TESTED. DEVICE-VERIFIED still false: first boot, gameplay, rendering, audio, touch overlay, lifecycle, saves, thermals, ES 3 path, on-device frame limit and adaptive scale remain unverified. APK hash, version and published release asset are unchanged from Session 20; only the harness and documentation changed.

**Carry-overs:** re-check the launcher on the API 28 emulator when host memory frees (still blocked: 3,791 MB total, 0 MB free physical/virtual, memory compression active; emulator + `Sotc_API_28` AVD are installed; diagnostics only); decide whether to feed `adapt()` from raylib frame time and re-release (option c in KNOWN_ISSUES); first-boot verification on real ARM64 hardware remains the only path to a playable claim.

## Session 22 - 2026-11-20 (patch 0010 gate chain and honest production-spec scope)

**Goal:** Finish the adaptive-resolution defect fix (patch 0010), complete the entire no-phone gate chain with exact artifacts, and record the second production specification as deliberately scoped rather than faked.

**Done:**
- Confirmed patch `0010-android-adaptive-frame-time.patch` (SHA-256 `de23a8b679bc9b059082842adab2de847c90191bc4d1d7aafdb39c5f3a7a8f72`) is applied at celosia `f8c269e`; its round-trip contract had been verified earlier and its marker is registered in `apply-patches.sh`.
- The host x86 LTO link had thrashed for over an hour (~1.3 GB RSS inside a 1.7 GiB WSL limit, host RAM down to ~133 MB). Terminated the stalled Ubuntu distro, re-enabled `/root/swapfile`, replaced `-flto=auto` with `-fno-lto` in a backed-up local `build.ninja`, and rebuilt only the host smoke runner: `HOST_BUILD_OK`, 76,213,160 bytes. No game source or ARM64 flag changed (D30).
- Extended `smoke-runtime-pacing.sh` from 9 to 13 assertions and fixed two harness artifacts: a startup `frameMs=0.000` is valid (now requires at least one positive frame time), and the uncapped software-GL case needs 60 s rather than 30 s. Final run: `HOST_SMOKE_0009_OK`, 13/13, including `[scale-adaptive] on:` and `[scale-adaptive] frame 391: render scale 0.950` with `SOTC_PERF_OVERLAY=0`.
- Rebuilt the ARM64 runner with patches 0001-0010: `BUILD_OK`, unstripped 777,589,832 bytes / SHA-256 `D3AE4C528EE2537CD7DA64CC08AE9FED87E8BC2D6E499EDF46512B92332693C4`, BuildID `44df4606af1e0e4bdc32baabee0ed29111230706`. Stripped copy 107,386,992 bytes / SHA-256 `FBB1D7888747A7E7C86B88508039F54A1F960F8F45258270823C92FA77619FF9`, `RUNNER_VERIFY_OK (0 warnings)`, packaged into `jniLibs/arm64-v8a`.
- Full Gradle gate with JDK 17: `BUILD SUCCESSFUL`; 17 suites / 106 tests / 0 failures / 0 errors / 0 skipped; `lintDebug` and `assembleDebug` pass. `verify-apk.py`: `APK_NATIVE_VERIFY_OK`, 0 failures/0 warnings. Local debug APK 220,051,031 bytes / SHA-256 `F2761B4A9BD1BE305FB87DD9DCBBBA5B13C097CC08A447A66BF5DFD8A40B9308`.
- Added `KnownReleases` recognition for all nine spec serials while keeping `SupportedDiscs` to `SCUS-97472`; unverified regions fail with `KNOWN_UNVERIFIED_SERIAL`. Honest UI captions and diagnostics-only sigil styling were retained. D29 records the rejected production-spec items and D30 records the host-only no-LTO decision.
- Updated `PROJECT_STATE.md`, `NEXT_STEPS.md`, `KNOWN_ISSUES.md` (item 8 now describes the fix), `TECHNICAL_DECISIONS.md`, `CODEBASE_MAP.md`, `ARCHITECTURE.md`, `BUILDING_ANDROID.md`, the integration README and `NO_PHONE_VERIFICATION_REPORT.md` with exact hashes and the 13/13 result.

**Verification status at session end:** COMPILED, PACKAGED, UNIT-TESTED (106), LINT-CLEAN, APK-VERIFIED and HOST-RUNTIME-TESTED (13/13) are green. DEVICE-VERIFIED remains false: first boot, gameplay, rendering, audio, touch overlay, lifecycle, saves, thermals, 16 KB device loading, ES 3 path and on-device `[pace]`/`[scale-adaptive]` capture are unverified. The local APK was not published; `v0.2.0-alpha.1` remains the latest release with its original hash. No commit was created in this session and nothing was pushed.

**Carry-overs:** commit the verified patch-0010 gate chain at the next meaningful milestone with an English message; keep the official trophy catalog, rcheevos data, host-owned boot stages, vendor GPU tiers and Portuguese Compose text rejected under D29; obtain a real ARM64 device before any playable claim; never push or publish without explicit authorization.

## Session 23 - 2026-11-20 (v0.3.0-alpha.1 no-device gate chain and release)

**Goal:** Finish the original HD shader pack and refined touch subsets, complete every no-device gate with patches 0001-0011, commit/push `main`, and publish the debug APK with an honest "not playable" label.

**Done:**
- Finalized patch `0011-android-hd-shader-and-touch.patch` at SHA-256 `b6eec5cff3913840f89b79a08cec86ad0f3c35ba940012d9cdf307c5b4e73782`. Forward apply, reverse apply and byte-for-byte restore were verified against celosia `f8c269e`; its `apply-patches.sh` marker is `sotc_hd_shader::initialize();`. Preset defaults are `sharpness=0.35` and `bloom=0.26`.
- Completed the host x86 smoke after restoring a small `.ninja_log`: compiled the two changed objects and relinked with the exact `ninja -t commands` commands, then ran `SOTC_SKIP_HOST_REBUILD=1` (`HOST_BUILD_SKIPPED_MANUAL_LINK`). Final result `HOST_SMOKE_0009_OK`: the original 13 pacing assertions are unchanged and pass, plus 7 independent `[hd-shader]` assertions pass with 18 telemetry records. Full-scale windows were extended to 75 s and 90 s under llvmpipe; no assertion value changed (D32).
- Rebuilt the ARM64 runner with patches 0001-0011: `BUILD_OK`, unstripped 777,928,592 bytes / SHA-256 `08C541F06B5E651B733016C6D86C0F2025A987B4A35DB94545EB237D45180045`, BuildID `47975ca2bbe4958a5776031c7d8c7215709c3087`. Stripped copy 107,409,464 bytes / SHA-256 `E7BD58EFFCCB461A2404EC1600077CA245C40590D23E662B4798019587F365766`, `RUNNER_VERIFY_OK (0 warnings)`, packaged into `jniLibs/arm64-v8a`.
- Final Gradle gate with JDK 17: `testDebugUnitTest` 21 suites / 139 tests / 0 failures / 0 errors / 0 skipped; `lintDebug` and `assembleDebug` BUILD SUCCESSFUL. `verify-apk.py`: `APK_NATIVE_VERIFY_OK`, 0 failures / 0 warnings; all three packaged libraries have 16 KB LOAD and ZIP alignment.
- Final debug APK `SotC-Mobile-v0.3.0-alpha.1-debug.apk`: 238,697,332 bytes, SHA-256 `8D83BD57FC6858DD41F95256CC4F1C4BEB1A4E8B348E401F02464E2FD18BDEE8`. `aapt2 dump badging` confirms `com.portsotc.mobile`, version `0.3.0` (code 3), label `SOTC`, ABIs `arm64-v8a` and `x86_64`.
- Updated release notes, release history, README/FAQ/INSTALLATION/COMPATIBILITY/BUILDING_ANDROID, agent state, next steps, known issues and technical decisions. The eight extra recognized serials still fail with `KNOWN_UNVERIFIED_SERIAL`; no trophy/RetroAchievements data, emulator backend, ES3-only path, game asset or Portuguese Compose text was introduced.
- Committed the verified chain with an English message, pushed `main`, and published the APK on GitHub Releases as `v0.3.0-alpha.1` with the explicit statement that physical ARM64 gameplay remains unverified.

**Verification status at session end:** COMPILED, PACKAGED, UNIT-TESTED (139), LINT-CLEAN, APK-VERIFIED and HOST-RUNTIME-TESTED (13/13 + 7/7) are green. DEVICE-VERIFIED remains false: first boot, gameplay, rendering, audio, touch ergonomics, lifecycle, saves, thermals, 16 KB device loading, game ES 3 output and on-device telemetry remain unverified.

**Carry-overs:** obtain a real ARM64 device before any playable claim; capture `[pace]` and `[hd-shader]` from a real session; revisit the deferred API 28 launcher check only when host memory frees; never promote a host shader pass or diagnostics UI result to game rendering evidence.

## Session 24 - 2026-11-20 (v0.3.1-alpha.1 UI overhaul, edge-to-edge fullscreen and vector touch controls)

**Goal:** Implement the full v0.3.1 specification: launcher UI declutter, 3-tab segmented HD preset bar, edge-to-edge cutout presentation, PlayStation vector geometric symbols on touch controls, gamepad noise filtering, and honest documentation of the 5–7 FPS runner bottleneck.

**Done:**
- `SotcRefinedLauncherScreen.kt`: fixed bottom button wrapping (`maxLines=1`, `softWrap=false`, `overflow=Clip`), added status indicator dot (`#6BBF8A` Sage / `#D99B52` Amber) with clean readiness headline (`READY TO PLAY` / `IMPORT DISC TO PLAY`), added 3-tab segmented HD preset bar (`BATTERY` | `BALANCED` | `ULTRA`), removed raw debug dump `buildSummary`, updated PLAY button to 54.dp height and 10.dp radius.
- `MainActivity.kt`: wired `onPresetChange` callback to `app.selectHdShaderPreset(presetId)`.
- `GameActivity.kt`: migrated from deprecated `systemUiVisibility` to `WindowCompat.setDecorFitsSystemWindows(window, false)` and `WindowInsetsControllerCompat` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`; set `layoutInDisplayCutoutMode = SHORT_EDGES` for full cutout edge-to-edge spanning; verified performance overlay is hidden by default.
- `TouchControllerView.kt`: implemented true PlayStation geometric vector shapes (`△`, `○`, `✕`, `□`), double-ringed R1/L1 triggers, pill-shaped L2/R2, and `HapticFeedbackConstants.KEYBOARD_TAP`.
- `GamepadManager.kt` & `ControllerSettingsActivity.kt`: filtered virtual/kernel noise input devices, updated empty controller notice to `"No external controller connected · Touch HUD active"`, and renamed layout editor button to `"CUSTOMIZE TOUCH LAYOUT"`.
- Documented 5–7 FPS runner bottleneck as Active Blocker B6 and Known Issue #13, gated on ARM64 device profiling (M6).
- Version bumped in `app/build.gradle` to `versionCode 4`, `versionName '0.3.1'`.
- Created release documentation `docs/releases/v0.3.1-alpha.1.md`.

## Session 25 - 2026-11-21 (v0.3.2-alpha.1 presentation, pacing and fullscreen fixes)

**Goal:** Fix the `BATTERY`/`BALANCED` black screen, the `ULTRA` frozen `0 FPS` counter and true `UNLIMITED` pacing; add safe fullscreen presentation, the vector touch HUD and launcher/log polish; rebuild and publish the ARM64 runner with an honest no-device status.

**Done:**
- Applied patch 0012 to both the Android host and the celosia runner: `GL_FRAMEBUFFER_COMPLETE` checks with direct-blit fallback, framebuffer-0 rebinding, full `glViewport`, scissor/depth disable, post-presentation FPS sampling, `eglSwapInterval(0/1/2)`, strict 1.0x EE clock with `SOTC_SIM_MAX_DT_MS` clamping, FNV-1a + CLUT texture-cache reuse, default-framebuffer invalidation before swap, fullscreen widescreen FOV correction, vector touch HUD, gamepad auto-hide and launcher/log polish.
- Kept the Apple/Vita GL renderer cache explicit and separate from the active Android GS backend; recorded the distinction in D33 and the release notes. No haptics JNI bridge was invented; haptics remain host-only.
- Regenerated patch 0012 from the authoritative baseline: 640 lines, `REVERSE_APPLY_CHECK_OK`, mirrored byte-identically (SHA-256 `047BE0C53358588E300F68AC64F9ED1999351004E68A17613E40ABE160593246`) and registered in `apply-patches.sh`.
- Fixed two `ShaderPackInstallerTest` assertions to the real locale-independent `formatJsonNumber` output `1.0`, and corrected the `g_aspectClipScale` scope plus an explicit `<atomic>` include.
- Gradle gate with JDK 17: 21 suites / 139 unit tests, `lintDebug` and `assembleDebug` pass. A second full gate after the runner repackage also returned `BUILD SUCCESSFUL in 2m 28s` (59 tasks).
- Host smoke: `HOST_SMOKE_0009_OK` with 20/20 assertions (13 pacing + 7 HD shader), using the manual host relink path from D32 after a stale `sotc_native.cpp.o` with the old five-argument `uploadTexture` signature was recompiled.
- ARM64 runner: thin-LTO link `NINJA_EXIT=0`; unstripped 777,950,416 bytes / SHA-256 `284BA134A22E2EAA68107981240730C4779627437916F470DD555266449FCEBC` / BuildID `17586524c45de6ccd971c6a5b4a7d6aa0d1d27a1`. Stripped copy 107,410,752 bytes / SHA-256 `E616E6F6BE7B946330456B0C89476BD3FFEFE1A2F66E120E72018122DF582315`; `RUNNER_VERIFY_OK (0 warnings)` with 16 KB `PT_LOAD` alignment, packaged into `jniLibs/arm64-v8a`.
- Final debug APK `SotC-Mobile-v0.3.2-alpha.1-debug.apk`: 238,534,780 bytes, SHA-256 `DE348F4236BAFD13E9BEDE076A823929243F9E5598B7375DE8E1CC6D35D641CC`. `verify-apk.py`: `APK_NATIVE_VERIFY_OK` (0 failures, 0 warnings). `aapt2 dump badging` confirms `com.portsotc.mobile`, version `0.3.2` (code 5), label `SOTC`.
- Updated release notes, release history, agent state, blockers, known issues and technical decisions; version bumped to `versionCode 5` / `versionName '0.3.2'`.

**Not verified:** no physical ARM64 device run; no non-black-frame, viewport-dimension, touch-ergonomics, HD-shader or FPS measurement from gameplay. The host smoke covers pacing and HD-shader telemetry only.

**Carry-overs:** obtain a real ARM64 device before any playable claim; capture `[pace]` and `[hd-shader]` from a real session; profile the 5-7 FPS guest bottleneck; never promote a host shader pass or diagnostics UI result to game rendering evidence.


## Session 26 - 2026-11-21 (v0.3.3-alpha.1 presentation hardening and UI polish)

**Goal:** Apply the safe subset of the master v0.3.3 specification: true cutout fullscreen, black-screen hardening, console-grade UI/HUD polish, all no-device gates, and publication of a debug-signed preview with an honest hardware status.

**Done:**
- Audited the active native, Kotlin/UI and release-infrastructure paths in parallel; confirmed the active gameplay renderer is the raylib-owned `GameNativeActivity`/`libps2EntryRunner.so` (D18) and that `SotcSafePresenter`, non-black smoke assertions and the 33.3 ms clamp were absent.
- Added declarative `shortEdges` cutout mode and transparent bars to `Theme.PortSotC.Fullscreen`, re-applied immersive mode on `GameNativeActivity` focus gain, and added `smallestScreenSize` to both gameplay activities' `configChanges`.
- Reordered import actions to `SELECT ISO IMAGE` → `START IMPORT` → `CLOSE` with a new `import_close` string; moved launcher navigation icons above non-wrapping labels with 64.dp tiles; replaced the raw key dump in controller settings with keycap rows and added a mapped Latch/Hold/Hybrid grip selector.
- Hardened the host diagnostics blit: `GL_CULL_FACE` reset before every fullscreen present and NPOT/anisotropy-mapped mipmap gating in `HdShaderPipeline::configureSampler`; kept the GLES2 floor and the unchanged patch-0012 runner (D34).
- Gradle gate with JDK 17: `BUILD SUCCESSFUL` (59 tasks), 21 suites / 139 unit tests / 0 failures, `lintDebug` and `assembleDebug` pass. Pre-existing warnings only.
- Host smoke reused the linked v0.3.2 host runner with `SOTC_SKIP_HOST_REBUILD=1` (D32): `HOST_SMOKE_0009_OK`, 20/20 assertions (13 pacing + 7 HD shader). The stale `.ninja_log` would otherwise have recompiled all 9,271 host objects.
- Runner unchanged and re-verified: unstripped 777,950,416 bytes / SHA-256 `284BA134A22E2EAA68107981240730C4779627437916F470DD555266449FCEBC`; packaged 107,410,752 bytes / SHA-256 `E616E6F6BE7B946330456B0C89476BD3FFEFE1A2F66E120E72018122DF582315`; `RUNNER_VERIFY_OK (0 warnings)`, BuildID `17586524c45de6ccd971c6a5b4a7d6aa0d1d27a1`, 16 KB LOAD alignment.
- Final debug APK: `SotC-Mobile-v0.3.3-alpha.1-debug.apk`, 132,300,796 bytes, SHA-256 `73A1D9BC5A8940BA166804AFFC0E8888FC55829F2ADA18ADBB66077C4BC2FFA6`. `verify-apk.py`: `APK_NATIVE_VERIFY_OK` (0 failures, 0 warnings). `aapt2 dump badging` confirms `com.portsotc.mobile`, version `0.3.3` (code 6), label `SOTC`.
- Bumped `app/build.gradle` to `versionCode 6` / `versionName '0.3.3'`. Created release notes and updated release history, agent state, blockers, known issues, technical decisions (D34) and public version references.

**Not verified:** no physical ARM64 device run; no non-black-frame, viewport-dimension, touch-ergonomics, HD-shader or FPS measurement from gameplay. The host smoke covers pacing and HD-shader telemetry only.

**Carry-overs:** obtain a real ARM64 device before any playable claim; add non-black pixel and fullscreen viewport assertions to the host smoke; capture `[pace]`/`[hd-shader]` from a real session; profile the 5-7 FPS guest bottleneck; never promote a host shader pass or diagnostics UI result to game rendering evidence.

