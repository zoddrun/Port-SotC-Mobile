# Project State

> Agent memory file. Read this first when resuming work.
> Last updated: 2026-11-11

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
| 4 | ARM64 cross-compile of game core | NOT STARTED — prep done: Android-integration patch series prepared (`tools/android-integration/`, see NEXT_STEPS M4) |
| 5 | Runtime integration into the app (D18: raylib-owned NativeActivity; ABI-v1 re-scoped to diagnostics) | NOT STARTED |
| 6 | First boot on device/emulator | NOT STARTED |
| 7 | Gameplay verification | NOT STARTED |
| 8 | Performance optimization | NOT STARTED |
| 9 | Polish (controls, lifecycle, saves) | NOT STARTED |
| 10 | Release validation | NOT STARTED |

## Pipeline Workspace (Milestone 3) — /root/sotc in WSL2 Ubuntu 26.04

Adopted from the developer (D10): `/root/celosia` = clean clone of our `third_party/celosia` @ f8c269e; build in `/root/sotc/build`; disc extracted in `/root/sotc/disc`; ISO at `/root/sotc/source.iso` (hash-verified).

Helper scripts (in `/root/sotc/` — mirrored in-repo at `tools/capture-host/`, byte-verified):
- `celosia-env.sh` — wraps the celosia driver with all host workarounds (paths, `-j2`, CMake flags incl. SSE4.1 + NEON shim + policy fix, `DISPLAY=:99`, software GL). Use for `recompile` / `build` / `vu1` / `autotest`.
- `capture-module.sh KERNEL|MANAGER|GAMECORE` — celosia `capture()` replica with display env baked in.
- `pipeline-remaining.sh` — unattended continuation of the pipeline from the built boot runner through captures ×3 + `stage_game` ×3 + VU1 (D19: host recovery, wait-for-ninja guard, verify/retry pass, per-stage log snapshots). NOT `celosia setup` — that re-runs `stage_boot` and would discard the current build.
- `neon-shim/arm_neon.h` — SIMDe shim (D13); `neon-shim/mach-o/dyld.h` — dl_iterate_phdr shim (D16).
- `fix-x11.sh` / `diag-x11.sh` — per-boot WSLg/Xvfb recovery (D14; fix-x11 now loops for stacked mounts).
- `swapfile` — 8 GB swap (D15).

Verified state (2026-11-11):
- `configure` done — deps fetched (raylib 5.5, imgui, rlimgui, elfio, fmt, libdwarf, rabbitizer, toml11); `CMAKE_POLICY_VERSION_MINIMUM=3.5` applied (D11).
- `ps2_recomp` built (binary + libps2_recomp_lib.a).
- `stage_boot` done — KERNEL.XFF symbols → `scus_symbols.txt`; `funcmap_elf.py` → `functions.csv`; recompiler emitted 1429 C++ files to `build/runner/` (recomp.log ends "Recompilation completed successfully"); build.ninja regenerated after the copy.
- `ps2EntryRunner` builds: attempts 1–4 fixed in sequence — NEON→SIMDe shim (D13), `-msse4.1` (D13), guarded-include force-include + restricted A32V7 alias shim (D13), mach-o/dyld.h shim via dl_iterate_phdr (D16). Attempt 5 (both shims) failed in unity_45 on three errors: two x86-host bugs the developer fixed directly in /root/celosia (2026-11-10 10:04, uncommitted local edits: `coll125.h` vf0c→vf0u; `decomp_collision.cpp` guard Vita-only `fast::check49()` behind `#if PS2X_VU_NEON_A32`), and one unity-build ODR collision (`decomp_group::NextRegs` defined in 3 override files) → **D17: unity build OFF for the capture host** (deterministic fix; also lowers per-TU memory under the 1.7 GiB limit).
- **Attempt 6 RUNNING** (2026-11-11, background): non-unity rebuild, 1567 targets at `-j2`, log `/root/sotc/logs/build-ps2EntryRunner-attempt6.log`. 8 GB swap re-activated (per-boot reset, KNOWN_ISSUES #10) and Xvfb :99 restarted (WSLg stacked the ro mount twice — fix-x11.sh now loops). At the link step (~1565/1567) as of 2026-11-11 ~07:00; `pipeline-remaining.sh` waits for it and continues automatically (D19).
- **Remaining pipeline RUNNING** (2026-11-11 07:01, background): `bash /root/sotc/pipeline-remaining.sh` (D19) — waits for attempt 6's ninja to exit, verifies the runner target (no-op on success; automatic retry of a transiently failed link), then capture KERNEL → `celosia-env.sh recompile` → MANAGER → recompile → GAMECORE → recompile → VU1, unattended. Master log `/root/sotc/logs/pipeline-remaining.log`; per-stage snapshots `stagegame-*.log`.

## What Works Today (verified)

- Android host app builds (Gradle wrapper, arm64-v8a + x86_64) and has 49 passing unit tests.
- Host foundation: EGL renderer, audio engine (AAudio/OpenSL), touch controls + gamepad input,
  ISO import + validation (ISO9660 / SYSTEM.CNF / PS2 ELF / XFF magic), diagnostics scene.
- **Launcher icon = developer's artwork** (2026-11-11, verified in APK): adaptive icon
  (`@mipmap/ic_launcher` + round) with black background layer, artwork foreground inset into the
  66dp safe zone, and a themed-icon monochrome layer; all five densities packaged and confirmed
  via `aapt2 dump badging` + APK entry listing. See `docs/ICON_ASSET.md`.
- `game_core.cpp` `dlopen` loader with ABI-v1 checks awaits a real `libsotc_game.so`.
- **Original disc image is present and validated** (`original iso/SCUS_974.72.Shadow of the Colossus.iso`):
  SCUS-97472, SHA-256 `d945516e...f015eb37`, report at `build/reports/original_iso.json`.

## What Does NOT Work Yet (honest status)

- No game execution of any kind. No `libsotc_game.so` exists yet.
- Recompilation has produced only the boot stage (`stage_boot`, 1429 functions from KERNEL);
  the full game (captures → stage_game → VU1) is pending the `ps2EntryRunner` host build.
- No ARM64 hardware verification has happened (no device connected).

## Immediate Next Actions

1. Set up WSL2 workspace for the Celosia pipeline (space-free path, dependencies: `libarchive-tools`, `pkg-config`, `xvfb`).
2. Run Celosia pipeline stage by stage: extract → configure → build `ps2_recomp` → `stage_boot`.
3. Module captures (KERNEL / MANAGER / GAMECORE) → `stage_game` → recompile → VU1 collection/codegen.
4. ARM64 cross-compile of generated core, then ABI-v1 integration into the Android app.

See `NEXT_STEPS.md` for the full ordered roadmap and `BLOCKERS.md` for known constraints.
