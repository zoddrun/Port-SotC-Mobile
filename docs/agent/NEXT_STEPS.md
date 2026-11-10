# Next Steps — Ordered Roadmap

> Agent memory file: the actionable queue. Work top-down; update as items complete.

## Milestone 3 — Real Recompilation Pipeline (IN PROGRESS in /root/sotc, WSL2)

Workspace adopted from the developer (D10): ROOT=`/root/celosia`, BUILD=`/root/sotc/build`, DISC=`/root/sotc/disc`, ISO=`/root/sotc/source.iso`.

1. [x] **WSL workspace ready** — all tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14); zlib1g-dev installed; FFmpeg/GL/X11 dev packages preinstalled.
2. [x] **Extract disc** — `/root/sotc/disc/` complete (SCUS_974.72, all XFF modules, NICO.DAT).
3. [x] **Configure + build ps2_recomp** — deps fetched; `CMAKE_POLICY_VERSION_MINIMUM=3.5` (D11); recompiler binary built.
4. [x] **stage_boot** — KERNEL.XFF symbols freed; funcmap_elf.py mapped the boot ELF; recompilation emitted 1429 C++ files into `build/runner/` ("Recompilation completed successfully").
5. [ ] **Build ps2EntryRunner** — attempt 5 RUNNING (`-j 2`, D13 + D16 shims; log `/root/sotc/logs/build-ps2EntryRunner-attempt5.log`). If OOM → retry `-j1`.
6. [ ] **Captures ×3** (KERNEL → MANAGER → GAMECORE): run the runner with `PS2X_DUMP_RAM_ON_MISSING=<build/captures/xxx.bin>` under `xvfb-run`; poll until the 32 MB dump appears. KERNEL should trigger immediately after boot; MANAGER/GAMECORE need progression (use `PS2X_INPUT_SCRIPT` if the flow requires input; see ps2_pad.cpp for the scripting mini-language).
7. [ ] **stage_game**: after each capture — `build_elf.py` (merge XFF@auto@dump) → `gen_config.py` → recompile → runner rebuild.
8. [ ] **VU1 collection**: `autotest` 40 s with `PS2X_VU1_DUMP_MISSING`, then `PS2X_VU1_GEN=dumps:outdir` codegen; runner rebuild.
9. [ ] Commit pipeline progress notes/patches (never the dumps/binaries; they are derived game content — keep under gitignored paths).

## Milestone 4 — ARM64 Cross-Compile

- [ ] Cross-compile the recompiled core + `game/overrides/` with Android NDK (26.1 on Windows; consider NDK 28.2 from upstream scaffold) for arm64-v8a: sse2neon path (`PS2X_IS_AARCH64_TARGET`), `PS2X_DEFAULT_BOOT_ELF` baked. Use the NDK's real `arm_neon.h` (never the x86 neon-shim), and put `tools/capture-host/neon-shim/mach-o/dyld.h` (or equivalent) on the include path for `decomp_lines67.cpp` (D16; bionic provides `dl_iterate_phdr`).
- [ ] Produce `libsotc_game.so` satisfying `sotc_game_api.h` ABI v1 (write the shim around PS2X runner entry).
- [ ] Translate Portuguese runtime strings to English (D7); add SCUS-97472 to games database.

## Milestone 5 — ABI Integration

- [ ] Package `libsotc_game.so` into the app; wire `game_core.cpp` dlopen path.
- [ ] Implement Android input → PS2X pad mapping (fix ps2_android_runtime.cpp TODO equivalent).
- [ ] Audio route through our `audio_engine.cpp` sink.

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
