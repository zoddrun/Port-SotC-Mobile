# Next Steps — Ordered Roadmap

> Agent memory file: the actionable queue. Work top-down; update as items complete.

## Milestone 3 — Real Recompilation Pipeline (IN PROGRESS in /root/sotc, WSL2)

Workspace adopted from the developer (D10): ROOT=`/root/celosia`, BUILD=`/root/sotc/build`, DISC=`/root/sotc/disc`, ISO=`/root/sotc/source.iso`.

1. [x] **WSL workspace ready** — all tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14); zlib1g-dev installed; FFmpeg/GL/X11 dev packages preinstalled.
2. [x] **Extract disc** — `/root/sotc/disc/` complete (SCUS_974.72, all XFF modules, NICO.DAT).
3. [x] **Configure + build ps2_recomp** — deps fetched; `CMAKE_POLICY_VERSION_MINIMUM=3.5` (D11); recompiler binary built.
4. [x] **stage_boot** — KERNEL.XFF symbols freed; funcmap_elf.py mapped the boot ELF; recompilation emitted 1429 C++ files into `build/runner/` ("Recompilation completed successfully").
5. [ ] **Build ps2EntryRunner** — attempts 1–5 resolved sequentially (NEON shim D13, `-msse4.1` D13, force-include + restricted aliases D13, mach-o shim D16, developer's 2 source fixes, unity OFF D17). **Attempt 6 RUNNING** (non-unity, 1567 targets, `-j2`; log `/root/sotc/logs/build-ps2EntryRunner-attempt6.log`; 8 GB swap + Xvfb :99 re-applied after the WSL reboot).
6. [ ] **Run the remaining pipeline**: after BUILD_OK, `cd /root/sotc && ./celosia-env.sh setup` — the driver runs captures ×3 (KERNEL → MANAGER → GAMECORE, each followed by stage_game recompile + runner rebuild) and VU1 collection + codegen, with every host workaround inherited (env + CMake cache). If MANAGER/GAMECORE captures stall (not reached in 120 s), add `PS2X_INPUT_SCRIPT` progression (autotest default is `11:DOWN:0.3;12:CROSS:0.3`; see ps2_pad.cpp for the scripting mini-language).
7. [ ] **stage_game**: handled inside the driver flow above (build_elf.py merge XFF@auto@dump → gen_config.py → recompile → runner rebuild). Do not run `cmd_recompile` manually before the first capture (it requires `$WORK/game` to exist; the driver's setup creates it).
8. [ ] **VU1 collection**: handled by the driver (`cmd_vu1`: autotest 40 s with `PS2X_VU1_DUMP_MISSING`, then `PS2X_VU1_GEN=dumps:outdir` codegen; runner rebuild).
9. [ ] Commit pipeline progress notes/patches (never the dumps/binaries; they are derived game content — keep under gitignored paths).

## Milestone 4 — ARM64 Cross-Compile

- [ ] **Apply both patch series to the celosia tree first** (never while a capture build runs): `tools/capture-host/patches/apply-patches.sh` (non-Vita fixes) then `tools/android-integration/patches/apply-patches.sh` (D7 English-only host menu + SCUS-97472 games_database entry; content round-trip verified, compile check happens with the first configure below — fix or drop rather than build around a failure).
- [ ] Cross-compile the recompiled core + `game/overrides/` with Android NDK (26.1 on Windows; consider NDK 28.2 from upstream scaffold) for arm64-v8a: sse2neon path (`PS2X_IS_AARCH64_TARGET`), `PS2X_DEFAULT_BOOT_ELF` baked. Use the NDK's real `arm_neon.h` (never the x86 neon-shim), and put `tools/capture-host/neon-shim/mach-o/dyld.h` (or equivalent) on the include path for `decomp_lines67.cpp` (D16; bionic provides `dl_iterate_phdr`). NDK note: build in a space-free path (D6); the repo checkout lives under a path with a space.
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
