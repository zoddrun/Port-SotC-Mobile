# Project State

> Agent memory file. Read this first when resuming work.
> Last updated: 2026-11-09

## Project Identity

- **Project:** Port-SotC-Mobile — native Android port of Shadow of the Colossus (PS2, SCUS-97472 USA)
- **Method:** Static recompilation of PS2 EE code to native ARM64 (Celosia / PS2Recomp pipeline). NOT emulation.
- **Repository:** https://github.com/zoddrun/Port-SotC-Mobile.git (branch `main`; remote `origin` never pushed to)
- **Hard rules:** 100% English everywhere; no emulator backend ever; no game assets committed; never fake functionality.

## Milestone Status

| Milestone | Description | Status |
|---|---|---|
| 0 | Technical / repository recovery audit | **COMPLETE** (2026-11-09) |
| 1 | Original ISO validation | **COMPLETE** (2026-11-09) |
| 2 | Celosia upstream audit | **COMPLETE** (2026-11-09, background research) |
| 3 | Real recompilation pipeline run (extract → capture → recompile) | **IN PROGRESS** — extract, configure, ps2_recomp build, and boot-ELF recompilation (stage_boot, 1429 functions) done in the adopted WSL workspace; `ps2EntryRunner` build running; captures + stage_game + VU1 pending |
| 4 | ARM64 cross-compile of game core | NOT STARTED |
| 5 | ABI-v1 integration (`libsotc_game.so` into app) | NOT STARTED |
| 6 | First boot on device/emulator | NOT STARTED |
| 7 | Gameplay verification | NOT STARTED |
| 8 | Performance optimization | NOT STARTED |
| 9 | Polish (controls, lifecycle, saves) | NOT STARTED |
| 10 | Release validation | NOT STARTED |

## Pipeline Workspace (Milestone 3) — /root/sotc in WSL2 Ubuntu 26.04

Adopted from the developer (D10): `/root/celosia` = clean clone of our `third_party/celosia` @ f8c269e; build in `/root/sotc/build`; disc extracted in `/root/sotc/disc`; ISO at `/root/sotc/source.iso`.

Verified state (2026-11-10):
- `configure` done — deps fetched (raylib 5.5, imgui, rlimgui, elfio, fmt, libdwarf, rabbitizer, toml11); `CMAKE_POLICY_VERSION_MINIMUM=3.5` applied (D11).
- `ps2_recomp` built (binary + libps2_recomp_lib.a).
- `stage_boot` done — KERNEL.XFF symbols → `scus_symbols.txt`; `funcmap_elf.py` → `functions.csv`; recompiler emitted 1429 C++ files to `build/runner/` (recomp.log ends "Recompilation completed successfully"); build.ninja regenerated after the copy.
- `ps2EntryRunner` build **in progress** (`-j2`, log: `/root/sotc/logs/build-ps2EntryRunner.log`).
- Remaining: captures ×3 (KERNEL/MANAGER/GAMECORE) under xvfb → `stage_game` (build_elf.py + gen_config.py + recompile) → VU1 collection (autotest 40 s) + `PS2X_VU1_GEN` codegen → runner rebuild.

## What Works Today (verified)

- Android host app builds (Gradle wrapper, arm64-v8a + x86_64) and has 49 passing unit tests.
- Host foundation: EGL renderer, audio engine (AAudio/OpenSL), touch controls + gamepad input,
  ISO import + validation (ISO9660 / SYSTEM.CNF / PS2 ELF / XFF magic), diagnostics scene.
- `game_core.cpp` `dlopen` loader with ABI-v1 checks awaits a real `libsotc_game.so`.
- **Original disc image is present and validated** (`original iso/SCUS_974.72.Shadow of the Colossus.iso`):
  SCUS-97472, SHA-256 `d945516e...f015eb37`, report at `build/reports/original_iso.json`.

## What Does NOT Work Yet (honest status)

- No game execution of any kind. No recompilation has been run. No `libsotc_game.so` exists.
- The pipeline (Celosia) has been audited in full but not executed.
- No ARM64 hardware verification has happened (no device connected).

## Immediate Next Actions

1. Set up WSL2 workspace for the Celosia pipeline (space-free path, dependencies: `libarchive-tools`, `pkg-config`, `xvfb`).
2. Run Celosia pipeline stage by stage: extract → configure → build `ps2_recomp` → `stage_boot`.
3. Module captures (KERNEL / MANAGER / GAMECORE) → `stage_game` → recompile → VU1 collection/codegen.
4. ARM64 cross-compile of generated core, then ABI-v1 integration into the Android app.

See `NEXT_STEPS.md` for the full ordered roadmap and `BLOCKERS.md` for known constraints.
