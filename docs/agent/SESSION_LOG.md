# Session Log

> Agent memory file: append-only record of work sessions. Newest last.

## Session 1 — 2026-11-09 (recovery + validation)

**Goal:** Full project recovery per the master prompt (Milestones 0–1), then set up for recompilation.

**Done:**
- Milestone 0 complete: full read-through of all first-party native C++ (13 files) and Kotlin (~30 files); docs cross-checked against code; repository state verified (branch main, HEAD 194804f, 12 commits, remote never pushed).
- Background audit A: `third_party/celosia` (bash driver, tools, 161 override files, platform matrix, GPL-3.0, Windows blockers identified).
- Background audit B: `third_party/PS2Recomp` (Android scaffold, runtime internals, gaps: android runtime TODO, no SCUS-97472 entry, FFmpeg off).
- Milestone 1 complete: wrote `tools/inspect_original_iso.py`; validated the developer's USA retail ISO (SCUS-97472; ELF + 3 XFF modules OK; SHA-256 d945516e...; report at `build/reports/original_iso.json`).
- Environment probed: WSL2 Ubuntu 26.04 (cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, 953 GB ext4, passwordless sudo); Windows side (Python 3.12.10, JDK 17 temp + Java 25, Android SDK w/ NDK 26.1).
- Fixed serial derivation bug (11-char boot filename, D3). `.gitignore` hardened for `/original iso/`.
- Created this `docs/agent/` memory system.

**State at session end:** Milestones 0–2 complete (audit + validation + upstream research). Nothing committed yet from this session (`.gitignore` modified, `tools/` untracked). Next: first milestone commit, then WSL workspace setup (NEXT_STEPS.md Milestone 3 item 1).

**Carry-overs:** B1 space-in-path, B2 WSL RAM 1.7 GiB, B3 missing xvfb/libarchive-tools, B4 no ARM64 device.

## Session 2 — 2026-11-09 (memory system + doc sync + commit)

**Done:**
- Created `docs/agent/` memory system: PROJECT_STATE, CODEBASE_MAP, FILE_AUDIT, ARCHITECTURE, TECHNICAL_DECISIONS, COMPLETED_WORK, KNOWN_ISSUES, BLOCKERS, NEXT_STEPS, SESSION_LOG.
- AGENTS.md: added Agent Session Recovery Procedure section.
- Doc sync for Milestone 1: README.md (verified-state row + new "Original disc image validation" section), docs/COMPATIBILITY.md (summary row + section 5 note), docs/ANDROID_PORTING_PLAN.md (phase summary row + Phase 3 status/exit criteria).
- `.gitignore`: added `__pycache__/`; re-verified ISO and pycache both ignored via `git check-ignore`.
- Committed: `docs: establish agent memory and record retail ISO validation`.

**Carry-overs:** unchanged (B1–B4).

## Session 3 — 2026-11-09/10 (Milestone 3: WSL pipeline setup → adoption)

**Done:**
- Probed WSL: all pipeline tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, git; FFmpeg/GL/X11 dev packages). Installed only `zlib1g-dev`. Network OK.
- Learned: `/mnt/c` copies of the 2.5 GiB ISO fail in-WSL with "Cannot allocate memory" (D12 — use `\\wsl.localhost` from the Windows side).
- Set up a duplicate workspace in `~/sotc-port` (clone + ISO copy) — then discovered the developer's own live pipeline at `/root/celosia` + `/root/sotc` (disc extracted, configure running with the CMake-4 policy fix). Asked the developer; **confirmed adoption** — deleted my duplicate, took over `/root/sotc` (D10).
- Adopted workspace state verified: configure complete (all deps fetched), `ps2_recomp` built, **stage_boot complete** (1429 recompiled functions in `build/runner/`, "Recompilation completed successfully" in recomp.log, build.ninja regenerated post-copy).
- Started the `ps2EntryRunner` build (`-j2`, log `/root/sotc/logs/build-ps2EntryRunner.log`).
- Memory docs updated: decisions D10–D12; blockers B1/B3 → resolved (R4–R7); PROJECT_STATE and NEXT_STEPS reflect Milestone 3 in progress.

## Session 3 — 2026-11-09/10 (Milestone 3: WSL pipeline setup → adoption)

**Done:**
- Probed WSL: all pipeline tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, git; FFmpeg/GL/X11 dev packages). Installed only `zlib1g-dev`. Network OK.
- Learned: `/mnt/c` copies of the 2.5 GiB ISO fail in-WSL with "Cannot allocate memory" (D12 — use `\\wsl.localhost` from the Windows side).
- Set up a duplicate workspace in `~/sotc-port` (clone + ISO copy) — then discovered the developer's own live pipeline at `/root/celosia` + `/root/sotc` (disc extracted, configure running with the CMake-4 policy fix). Asked the developer; **confirmed adoption** — deleted my duplicate, took over `/root/sotc` (D10).
- Adopted workspace state verified: configure complete (all deps fetched), `ps2_recomp` built, **stage_boot complete** (1429 recompiled functions in `build/runner/`, "Recompilation completed successfully" in recomp.log, build.ninja regenerated post-copy).
- **ISO hash verified in the adopted workspace**: `/root/sotc/source.iso` SHA-256 `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37` — byte-identical to the validated original.
- First `ps2EntryRunner` build attempt failed: game overrides include `<arm_neon.h>` unconditionally (x86_64 has none). Fixed with a SIMDe shim (`/root/sotc/neon-shim/arm_neon.h`, D13; `libsimde-dev` 0.8.2 installed; `-DCMAKE_CXX_FLAGS=-I/root/sotc/neon-shim` added to the cache). Build restarted (`-j2`).
- Prepared headless display for captures: found WSLg's `/tmp/.X11-unix` mounted read-only (blocked Xvfb); unmounted it, started persistent `Xvfb :99` (D14), confirmed Mesa llvmpipe (`swrast_dri.so`) for `LIBGL_ALWAYS_SOFTWARE=1`; wrote `/root/sotc/capture-module.sh` (celosia capture() replica with display env baked in).
- Added 8 GB swap (`/root/swapfile`) ahead of the LTO link (D15) — 1.7 GiB RAM + 1 GB stock swap would likely OOM linking ~1600 TUs.
- **Second build failure:** `ps2_runtime_macros.h` uses SSE4.1 `_mm_extract_epi64`; upstream's own Linux CI builds with `-msse4.1` — applied via `CMAKE_CXX_FLAGS='-msse4.1 -I/root/sotc/neon-shim'` (D13 amended). Build 3 launched (configure ~150 s then `-j2` build, still running at session note).
- Wrote `/root/sotc/celosia-env.sh` — wraps the celosia driver with all workspace env (CELOSIA_DISC/BUILD/JOBS=2, CELOSIA_CMAKE_ARGS with policy fix + flags, DISPLAY=:99 + software GL) so `recompile`/`build`/`vu1`/`autotest` run correctly; captures use `/root/sotc/capture-module.sh`.
- Recorded the PowerShell→WSL quoting traps (KNOWN_ISSUES #11): bash `$?` never survives `\$?` inline (PowerShell eats it); non-trivial commands go through script files.
- Memory docs updated: decisions D10–D15; blockers R4–R10 resolved; KNOWN_ISSUES/PROJECT_STATE/NEXT_STEPS current. Committed as 416ee0a.

**State at session end:** ps2EntryRunner rebuild running with the NEON shim (Xvfb :99 + swap ready). Next: verify build exit; run `/root/sotc/capture-module.sh KERNEL` (triggers right after boot); then MANAGER/GAMECORE (may need `PS2X_INPUT_SCRIPT` progression), then stage_game, then VU1.

**Carry-overs:** B2 (RAM 1.7 GiB — build at `-j2`), B4 (no ARM64 device).

## Session 4 — 2026-11-10 (Milestone 3: NEON shim root-cause → build attempt 4)

**Done:**
- Attempt 3 result: 107/169 targets, failed in unity_48 (`sotc_native.cpp:8454: 'uint32x4_t' does not name a type`, then `vrev64_u32`/`vand_u32`/`vorr_u32` in `mdsClipFlags4`). unity_47 — which holds the overrides with unguarded `#include <arm_neon.h>` — compiled clean, proving the SIMDe alias mechanism works whenever the include actually executes.
- Root cause: `sotc_native.cpp`, `decomp_char104.cpp`, `decomp_hot47j.cpp` guard `#include <arm_neon.h>` behind `__ARM_NEON`/`__arm__` (skipped on x86) while their NEON-typed code compiles unconditionally; the other five override files include unguarded and work through `-I/root/sotc/neon-shim`.
- Fix part 1 — force-include: `-include /root/sotc/neon-shim/arm_neon.h` added to `CMAKE_CXX_FLAGS` so every TU gets the NEON API regardless of guards. Census: the 1429 generated runner files contain zero NEON names → no collision with recomp output.
- Fix part 2 — restricted aliases: the original shim defined the global `SIMDE_ENABLE_NATIVE_ALIASES`; a PCH-order test proved that fatal — `simde/arm/neon.h` transitively pulls `simde/x86/avx.h` (via `neon/qrshl.h`) and `neon/crc32.h` pulls GCC's `ia32intrin.h`, and the resulting x86/CRC alias macros corrupt GCC's own `immintrin.h` when parsed after the shim (`_mm256_undefined_pd` argument-count cascades). Rewrote the shim to define only `SIMDE_ARM_NEON_A32V7_ENABLE_NATIVE_ALIASES`. Audit (`neontest/audit-aliases.sh`): all 108 distinct NEON intrinsics the overrides call are gated on A32V7 alone; nothing needs A32V8/A64V8. Validated both include orders (`neontest/run-test2.sh`: shim-then-PCH and immintrin-then-shim) with correct numeric results.
- Updated `/root/sotc/rebuild-runner.sh` (attempt 4: fresh log `build-ps2EntryRunner-attempt4.log`, prints `BUILD_OK`/`BUILD_FAILED` + `RUNNER_BINARY_PRESENT`) and `/root/sotc/celosia-env.sh` (`CELOSIA_CMAKE_ARGS` gains the `-include` flag); attempt 4 launched (`-j2`, background).
- Environment re-checked at launch: Xvfb :99 alive (PID 1814), 8 GB swap active, ~1.2 GiB RAM available.
- **Attempt 4: NEON fix confirmed working** — unity_48 (holds `sotc_native.cpp` and the guarded-include overrides) compiled clean; 106/169 targets. New failure: unity_46, `decomp_lines67.cpp:10 fatal error: mach-o/dyld.h` — the file guards that include only with `#if !defined(__vita__)`, so Linux demands a macOS-only header. Census of all platform includes across the overrides: `<psp2/...>` (13 files) and the other two mach users (`decomp_readcensus.cpp`, `sotc_prof.cpp`) are properly `__vita__`/`__APPLE__`-guarded; `decomp_lines67.cpp` is the sole unguarded case, and its dyld use is one call (`base()`, main image address for the env-gated SITES diagnostic).
- **Fix (D16):** shim `/root/sotc/neon-shim/mach-o/dyld.h` implementing `_dyld_get_image_header(0)` via `dl_iterate_phdr` (first entry = main program; image header = `dlpi_addr + p_vaddr - p_offset` of the PT_LOAD at offset 0). Validated in PIE, `-no-pie`, and the unity layout (NEON shim force-included): all return a pointer to ELF magic; nonzero index returns null. No new build flags needed (`-I/root/sotc/neon-shim` already covers it); no vendored edits (and `third_party/celosia` is gitignored in this repo — no in-repo copy exists to patch).
- Attempt 5 launched with the shim installed (fresh log `build-ps2EntryRunner-attempt5.log`, `-j2`, background; build resumable at unity_46).
- **Durability fix:** mirrored all capture-host infrastructure into the repo at `tools/capture-host/` (scripts `celosia-env.sh`/`capture-module.sh`/`rebuild-runner.sh`/`fix-x11.sh`/`diag-x11.sh`, `neon-shim/` with both shims, `tests/` with the shim validation suites and the 108-intrinsic alias audit, plus a README with install/recovery/Milestone-4 notes). Byte-verified against the live WSL copies (`verify-mirror.sh`: all MATCH) — previously this infrastructure existed only in the WSL instance and the Windows temp.

**State at session note:** attempt 5 build running in background (log `/root/sotc/logs/build-ps2EntryRunner-attempt5.log`).

**Carry-overs:** B2 (RAM 1.7 GiB — build at `-j2`), B4 (no ARM64 device).
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

- Android-integration patch series prepared (`tools/android-integration/`, applied at M4 start): `patches/0001-english-only-host-menu.patch` (D7 implementation — `portuguese()` becomes constant false and the Language item is removed entirely: enum case, value text, change handler, `language=` persistence, dead translation entries; every user-visible `ps2_runtime` string then renders English because `native_tuning.h:781` defaults the tuning-text flag to false and the only Portuguese-passing caller was that menu) and `patches/0002-games-database-scus-97472.patch` (game-name table entry; boot ELF `SCUS_974.72` normalizes to `SCUS-97472` in `main.cpp`). Generated byte-exactly from pristine f8c269e via `/root/sotc/patchprep-android.sh`, round-trip verified (`patch -p5` + `cmp` → IDENTICAL both); NOT compile-verified until the first M4 configure. `apply-patches.sh` uses explicit per-patch markers because the live tree carries the developer's unrelated uncommitted edits (a whole-tree diff check would be ambiguous). Both series are applied together at M4 start and never mid-build.
- **D18 recorded — Android integration adopts the upstream raylib-owned NativeActivity.** Evidence (read-only research while attempt 6 builds): `PS2Runtime::run()` is raylib-coupled end to end — `InitWindow`/`InitAudioDevice` in `initialize()`, `PollInputEvents`/`BeginDrawing`/`WindowShouldClose`/`CloseWindow` in the loop, GS output uploaded to raylib textures, host menu drawn via raylib, internal game/GS threads with their own pacing — so a host-driven ABI-v1 port would mean writing a custom raylib platform backend (a large, permanent fork). Upstream's scaffold already builds `ps2EntryRunner` for Android exactly this way (SHARED lib, `-Wl,--undefined=ANativeActivity_onCreate`, `ps2_android_runtime.cpp` compiled in — which is a 3-line touch-input TODO, i.e. upstream has no touch mapping at all). Decision: our app gains a Kotlin `GameNativeActivity` (subclass of `android.app.NativeActivity`, `android.app.lib_name` -> the runner lib) so our existing touch overlay/gamepad/settings views ride on the glue's window; boot-tree hand-off needs no runtime patch (pre-launch extraction from the imported `disc.iso` into a fixed private path via our `Iso9660Image`; `PS2X_DEFAULT_BOOT_ELF` baked to that stable path). ABI-v1 re-scoped to the diagnostics/future-headless contract — `docs/CORE_ABI.md` status note + "Producing a core" section updated. Known gaps carried to M4/M5 (in D18): no touch→pad mapping upstream; `main.cpp` ends with `std::_Exit(0)` which would kill the whole app process on "Quit game" — **patch prepared as `0003-android-quit-graceful.patch`** (main returns on `__ANDROID__`; verified against the fetched raylib 5.5 `rcore_android.c`, whose `android_main` calls `ANativeActivity_finish` + polls to `destroyRequested` after main returns; `main.cpp` already has plain `return 1` failure paths); the runtime wants the extracted disc tree, not the ISO. Milestone 5 also gets an `android:process=":game"` recommendation (fresh process per game session; launcher survives any hard exit). NEXT_STEPS Milestone 5 rewritten to the D18 task list; PROJECT_STATE M4/M5 rows updated.

**State at session note:** attempt 6 build running in background (non-unity, 1567 targets, `-j2`). Icon milestone verified and committed; android-integration patches committed (`517a48f`); D18 recorded and committed (`6eca848`); quit patch 0003 prepared.

**Carry-overs:** B2 (RAM 1.7 GiB - build at `-j2`), B4 (no ARM64 device).
