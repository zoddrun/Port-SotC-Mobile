# Next Steps — Ordered Roadmap

> Agent memory file: the actionable queue. Work top-down; update as items complete.

## Milestone 3 — Real Recompilation Pipeline (IN PROGRESS in /root/sotc, WSL2)

Workspace adopted from the developer (D10): ROOT=`/root/celosia`, BUILD=`/root/sotc/build`, DISC=`/root/sotc/disc`, ISO=`/root/sotc/source.iso`.

1. [x] **WSL workspace ready** — all tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14); zlib1g-dev installed; FFmpeg/GL/X11 dev packages preinstalled.
2. [x] **Extract disc** — `/root/sotc/disc/` complete (SCUS_974.72, all XFF modules, NICO.DAT).
3. [x] **Configure + build ps2_recomp** — deps fetched; `CMAKE_POLICY_VERSION_MINIMUM=3.5` (D11); recompiler binary built.
4. [x] **stage_boot** — KERNEL.XFF symbols freed; funcmap_elf.py mapped the boot ELF; recompilation emitted 1429 C++ files into `build/runner/` ("Recompilation completed successfully").
5. [x] **Build ps2EntryRunner — BUILD_OK (attempt 6, 2026-11-11 07:05)** — attempts 1–5 resolved sequentially (NEON shim D13, `-msse4.1` D13, force-include + restricted aliases D13, mach-o shim D16, developer's 2 source fixes, unity OFF D17); attempt 6 = non-unity, 1567 targets at `-j2`, log `/root/sotc/logs/build-ps2EntryRunner-attempt6.log`. Binary `/root/sotc/build/cmake/ps2xRuntime/ps2EntryRunner`, 17,944,488 bytes. One benign GCC false-positive warning (`sotc_native.cpp:438` — KNOWN_ISSUES #13).
6. [ ] **Run the remaining pipeline — RUNNING** (launched 2026-11-11 07:01, background): `bash /root/sotc/pipeline-remaining.sh` (mirrored at `tools/capture-host/pipeline-remaining.sh`, D19). Progress 07:06: verify pass no-op, **KERNEL captured** (32 MiB), stage_game-after-KERNEL rebuilding. Remaining: MANAGER → GAMECORE captures (each followed by `celosia-env.sh recompile` = stage_game + full runner rebuild) and VU1 collection + codegen, unattended. **NOT `celosia setup`** — it re-runs stage_boot, whose recompile() delete+recopy would discard attempt 6's build (D19). A stalled capture gets one automatic retry with `PS2X_INPUT_SCRIPT=11:DOWN:0.3;12:CROSS:0.3` (see ps2_pad.cpp for the scripting mini-language). Master log `/root/sotc/logs/pipeline-remaining.log`; per-stage driver-log snapshots `stagegame-*.log`; failures abort loudly with the relevant log tail.
7. [ ] **stage_game**: handled inside the pipeline above (build_elf.py merge XFF@auto@dump → gen_config.py → recompile → runner rebuild). Do not run `cmd_recompile` manually outside the script (it requires `$WORK/game` to exist; `pipeline-remaining.sh` pre-creates it — identical to what `stage_game` itself does first).
8. [ ] **VU1 collection**: handled by the driver (`cmd_vu1`: autotest 40 s with `PS2X_VU1_DUMP_MISSING`, then `PS2X_VU1_GEN=dumps:outdir` codegen; runner rebuild).
9. [ ] Commit pipeline progress notes/patches (never the dumps/binaries; they are derived game content — keep under gitignored paths).

## Milestone 4 — ARM64 Cross-Compile

- [ ] **Apply both patch series to the celosia tree first** (never while a capture build runs): `tools/capture-host/patches/apply-patches.sh` (non-Vita fixes) then `tools/android-integration/patches/apply-patches.sh` (D7 English-only host menu + SCUS-97472 games_database entry; content round-trip verified, compile check happens with the first configure below — fix or drop rather than build around a failure).
- [ ] Cross-compile the recompiled core + `game/overrides/` for arm64-v8a. Researched configure recipe (2026-11-11, from the driver's own `cmd_configure` + live `ps2xRuntime/CMakeLists.txt`): the CMake source root is `/root/celosia/PS2Recomp` (celosia embeds PS2Recomp); use a fresh space-free build dir (e.g. `/root/sotc/build-android`) with `-G Ninja -DCMAKE_BUILD_TYPE=Release -DPS2X_BUILD_STUDIO=OFF -DPS2X_BUILD_TEST=OFF -DPS2X_BUILD_ANALYZER=OFF -DPS2X_ENABLE_AGRESSIVE_LOGS=OFF` and `-DPS2X_RUNNER_DIRS=/root/sotc/build/runner;/root/sotc/build/vu1rec;/root/celosia/game/overrides` — exactly how the x86 build wires the generated code (runner `main.cpp` comes from `PS2Recomp/ps2xRuntime/src/runner/main.cpp`, the file patch 0003 touches). Android branches confirmed live (all keyed on `if(ANDROID)`): raylib fetched with `PLATFORM=Android`, debug UI + FFmpeg off, `ps2_host_backend` links `log android`, `ps2EntryRunner` SHARED with `-Wl,--undefined=ANativeActivity_onCreate` + `ps2_android_runtime.cpp`; sse2neon is fetched for aarch64 targets (upstream audit). Add `-DPS2X_ENABLE_RUNNER_UNITY_BUILD=OFF` (the `NextRegs` ODR collision is source-level — D17 applies on Android too). NDK: WSL has NO Android SDK/NDK and the Windows-side NDK cannot drive a Linux build → install a Linux NDK into a space-free WSL path first. **Chosen (D20): 26.1.10909125** (parity with `app/build.gradle:9`; the NDK toolchain defaults to `c++_static`, so no shared-STL mixing either way), installed by `tools/capture-host/install-ndk.sh` into `/root/android-ndk-r26b`; fallback if the r26b toolchain trips CMake 4.2: the scaffold's 28.2.13676358 (`third_party/PS2Recomp/android/app/build.gradle:11`). Include-path trap: do NOT put `neon-shim/` on the Android include path (its SIMDe `arm_neon.h` would shadow the NDK's real one) — copy only `mach-o/dyld.h` into a separate Android shim dir for `decomp_lines67.cpp` (D16; bionic provides `dl_iterate_phdr` — pre-created at `/root/sotc/android-shim/`). `CMAKE_POLICY_VERSION_MINIMUM=3.5` is the proven escape if raylib 5.5 trips CMake 4.2 (D11). `PS2X_DEFAULT_BOOT_ELF` is a cache STRING ("guest ELF path passed directly to main() when argv is unavailable") — bake it at M5 to the extracted boot-tree path.
- [ ] Produce the runner shared library for Android — upstream already builds `ps2EntryRunner` as a SHARED lib with `-Wl,--undefined=ANativeActivity_onCreate` for raylib's NativeActivity glue. **Architecture settled (D18): raylib-owned NativeActivity, not a host-driven ABI-v1 port** (see `tools/android-integration/README.md` and `TECHNICAL_DECISIONS.md` D18). Optional at configure: set the target `OUTPUT_NAME` to `sotc_game` so the shipped artifact is `libsotc_game.so`, matching the documented name.
- [x] Translate Portuguese runtime strings to English (D7 — patch prepared); add SCUS-97472 to games database (patch prepared). Both land with the first bullet above.

## Milestone 5 — Runtime Integration (D18: raylib-owned NativeActivity)

- [ ] Package the runner library into the app APK (`jniLibs/arm64-v8a`).
- [ ] Add `GameNativeActivity` — a Kotlin subclass of `android.app.NativeActivity` with `android.app.lib_name` pointing at the runner library — so our existing touch overlay, gamepad mapping and settings views ride on the window the glue drives (upstream has no touch→pad mapping at all: `ps2_android_runtime.cpp` is a 3-line TODO).
- [ ] Pre-launch extraction: extract the boot tree (boot ELF + XFF modules + `NICO.DAT` + remaining files) from the imported `disc.iso` into a fixed private path using the existing `Iso9660Image` reader (one-time, cached), and bake `PS2X_DEFAULT_BOOT_ELF` to that stable `/data/data/<pkg>/files/...` path at configure time (fixed applicationId).
- [ ] Quit handling: apply prepared `tools/android-integration/patches/0003-android-quit-graceful.patch` (`main()` returns on `__ANDROID__` so raylib's `android_main` finishes the NativeActivity cleanly — verified against the fetched raylib 5.5 source) and declare the game activity with `android:process=":game"` (fresh process per game session; the launcher process survives any hard exit).
- [ ] Re-scope `docs/CORE_ABI.md` wording at implementation time: ABI-v1 remains the diagnostics / future-headless contract; gameplay integrates via D18 (no behavior change to `game_core.cpp`).

## Milestone 6 — First Boot

- [ ] Boot on x86_64 emulator first (faster iteration), then ARM64 device when available (B4).
- [ ] Verify: title/loading sequence, no emulator, native ARM64 code executing (verify via maps/ABI checks).

## Milestone 7+ — Gameplay, Optimization, Polish, Release

- [ ] Gameplay verification checklist (menu → control → first area).
- [ ] Profile before optimizing (GS/VU1 hot paths), document measurements.
- [ ] Touch overlay tuning, gamepad support, lifecycle/resume, save persistence.
- [ ] Release validation per AGENTS.md Definition of Success.

## Standing Rules (apply at every step)

- English only. No fake functionality. Commit at meaningful milestones. Never touch the ISO. Never push without authorization.
- Update SESSION_LOG.md at the end of every work session; update PROJECT_STATE.md whenever milestone status changes.
