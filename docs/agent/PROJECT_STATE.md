# Project State

> Agent memory file. Read this first when resuming work.
> Last updated: 2026-11-18

## Project Identity

- **Project:** Port-SotC-Mobile — native Android port of Shadow of the Colossus (PS2, SCUS-97472 USA)
- **Method:** Static recompilation of PS2 EE code to native ARM64 (Celosia / PS2Recomp pipeline). NOT emulation.
- **Repository:** https://github.com/zoddrun/Port-SotC-Mobile.git (branch `main`; remote `origin` published and public as of 2026-11-18; documentation commit `42cf6c3`, merge commit `50bd46c`)
- **Hard rules:** 100% English everywhere; no emulator backend ever; no game assets committed; never fake functionality.
- **Latest release:** `v0.1.0-alpha.1` — Platform Preview (2026-11-18). Android host application only; **not playable**; debug-signed APK. See `docs/releases/v0.1.0-alpha.1.md`.

## Milestone Status

| Milestone | Description | Status |
|---|---|---|
| 0 | Technical / repository recovery audit | **COMPLETE** (2026-11-09) |
| 1 | Original ISO validation | **COMPLETE** (2026-11-09) |
| 2 | Celosia upstream audit | **COMPLETE** (2026-11-09, background research) |
| 3 | Real recompilation pipeline run (extract → capture → recompile) | **IN PROGRESS (recovered 2026-11-18)** — extract, configure, ps2_recomp build, stage_boot (1429 functions) done; **ps2EntryRunner BUILT (attempt 6, 17.9 MB)**; KERNEL captured (`kernel.bin`, 32 MiB). `pipeline-remaining.sh` was recovered on 2026-11-18 after activating 8 GB swap and repairing Xvfb; it is currently in **stage_game after KERNEL**, rebuilding the runner. No `manager.bin`/`gamecore.bin`, VU1 output, or `PIPELINE_DONE` yet. Each stage_game round is a full runner rebuild on a 4-core / 1.7 GiB WSL host, so remaining work is measured in hours. |
| 4 | ARM64 cross-compile of game core | NOT STARTED — prep done: patch series + scripts ready; NDK r26b toolchain smoke-tested **NDK_SMOKE_OK** (see NEXT_STEPS M4) |
| 5 | Runtime integration into the app (D18: raylib-owned NativeActivity; ABI-v1 re-scoped to diagnostics) | NOT STARTED — I/O model pinned by source read (full-disc extraction required; see NEXT_STEPS M5) |
| 6 | First boot on device/emulator | NOT STARTED |
| 7 | Gameplay verification | NOT STARTED |
| 8 | Performance optimization | NOT STARTED |
| 9 | Polish (controls, lifecycle, saves) | NOT STARTED |
| 10 | Release validation | NOT STARTED |

## Pipeline Workspace (Milestone 3) — /root/sotc in WSL2 Ubuntu 26.04

> 2026-11-18 recovery note: WSL is reachable and the pipeline was confirmed running via `wsl -e` (`pipeline-remaining.sh` + `ninja`). The 8 GB swapfile is active and Xvfb was repaired before the resumed run.

Adopted from the developer (D10): `/root/celosia` = clean clone of our `third_party/celosia` @ f8c269e; build in `/root/sotc/build`; disc extracted in `/root/sotc/disc`; ISO at `/root/sotc/source.iso` (hash-verified).

Helper scripts (in `/root/sotc/` — mirrored in-repo at `tools/capture-host/`, byte-verified):
- `celosia-env.sh` — wraps the celosia driver with all host workarounds (paths, `-j2`, CMake flags incl. SSE4.1 + NEON shim + policy fix, `DISPLAY=:99`, software GL). Use for `recompile` / `build` / `vu1` / `autotest`.
- `capture-module.sh KERNEL|MANAGER|GAMECORE` — celosia `capture()` replica with display env baked in.
- `pipeline-remaining.sh` — unattended continuation of the pipeline from the built boot runner through captures ×3 + `stage_game` ×3 + VU1 (D19: host recovery, wait-for-ninja guard, verify/retry pass, per-stage log snapshots). NOT `celosia setup` — that re-runs `stage_boot` and would discard the current build.
- `install-ndk.sh` — fetches + verifies the Linux Android NDK r26b (26.1.10909125, D20) into `/root/android-ndk-r26b` for the M4 cross-compile (idempotent; archive test + exact `Pkg.Revision` + clang smoke test; log `logs/install-ndk.log`).
- `configure-android.sh` — M4 ARM64 cross-configure (pipeline-done guards, patch verify-or-apply, NDK r26b toolchain; run only after PIPELINE_DONE).
- `build-android-runner.sh` — builds `libps2EntryRunner.so` (arm64-v8a) from that configure (per-attempt log; `BUILD_OK`/`RUNNER_SO_PRESENT` markers).
- `neon-shim/arm_neon.h` — SIMDe shim (D13); `neon-shim/mach-o/dyld.h` — dl_iterate_phdr shim (D16).
- `fix-x11.sh` / `diag-x11.sh` — per-boot WSLg/Xvfb recovery (D14; fix-x11 now loops for stacked mounts).
- `swapfile` — 8 GB swap (D15).

Verified state (2026-11-11):
- `configure` done — deps fetched (raylib 5.5, imgui, rlimgui, elfio, fmt, libdwarf, rabbitizer, toml11); `CMAKE_POLICY_VERSION_MINIMUM=3.5` applied (D11).
- `ps2_recomp` built (binary + libps2_recomp_lib.a).
- `stage_boot` done — KERNEL.XFF symbols → `scus_symbols.txt`; `funcmap_elf.py` → `functions.csv`; recompiler emitted 1429 C++ files to `build/runner/` (recomp.log ends "Recompilation completed successfully"); build.ninja regenerated after the copy.
- `ps2EntryRunner` builds: attempts 1–4 fixed in sequence — NEON→SIMDe shim (D13), `-msse4.1` (D13), guarded-include force-include + restricted A32V7 alias shim (D13), mach-o/dyld.h shim via dl_iterate_phdr (D16). Attempt 5 (both shims) failed in unity_45 on three errors: two x86-host bugs the developer fixed directly in /root/celosia (2026-11-10 10:04, uncommitted local edits: `coll125.h` vf0c→vf0u; `decomp_collision.cpp` guard Vita-only `fast::check49()` behind `#if PS2X_VU_NEON_A32`), and one unity-build ODR collision (`decomp_group::NextRegs` defined in 3 override files) → **D17: unity build OFF for the capture host** (deterministic fix; also lowers per-TU memory under the 1.7 GiB limit).
- **Attempt 6 BUILD_OK** (2026-11-11 07:05): non-unity rebuild, 1567 targets at `-j2`, log `/root/sotc/logs/build-ps2EntryRunner-attempt6.log`; binary `ps2xRuntime/ps2EntryRunner` (17,944,488 bytes). One benign GCC false-positive warning (`sotc_native.cpp:438`, triaged — KNOWN_ISSUES #13). 8 GB swap re-activated (per-boot reset, KNOWN_ISSUES #10) and Xvfb :99 restarted (WSLg stacked the ro mount twice — fix-x11.sh now loops).
- **Remaining pipeline — recovered 2026-11-18:** `bash /root/sotc/pipeline-remaining.sh` was restarted after activating the 8 GB swapfile and repairing Xvfb. It passed the runner verification, re-captured KERNEL, and is currently running **`stage_game after KERNEL`** (full `ps2EntryRunner` rebuild). Master log `/root/sotc/logs/pipeline-remaining.log`; per-stage snapshots are written as `stagegame-*.log`. Remaining after this stage: MANAGER capture + stage_game, GAMECORE capture + stage_game, then VU1 collection/codegen and `PIPELINE_DONE`.
- M4 prep done: NDK r26b (26.1.10909125, D20) installed + verified at `/root/android-ndk-r26b` (Pkg.Revision + clang 17.0.2; log `logs/install-ndk.log`); both patch series mirrored (`patches-capture-host/`, `patches-android-integration/`); Android dyld shim at `android-shim/` (no SIMDe header); `configure-android.sh` + `build-android-runner.sh` ready (hash-verified mirrors). NDK toolchain smoke test passed (`tools/capture-host/tests/ndk-smoke.sh` → NDK_SMOKE_OK 07:22: r26b toolchain + CMake 4.2.3 + Ninja + arm64-v8a + android-28 produce valid AArch64 Android ELFs; D20 fallback retired). M5 runtime I/O model researched from source and recorded (NEXT_STEPS M5: full-disc extraction required; mc0/mc1 beside the boot ELF; cdImage ISO fallback needs a patch, deferred to M6 evidence).

## What Works Today (verified)

- Android host app builds (Gradle wrapper, arm64-v8a + x86_64) and has 57 passing unit tests; lint and `assembleDebug` pass.
- Installed application label is `SOTC`; application ID remains `com.portsotc.mobile`.
- Launcher is a product screen: status card, single primary Play action (disabled unless the runtime is present), details dialog, import/settings/controls actions. Diagnostics are reachable only from Settings → Advanced.
- Settings are categorized (General, Graphics, Performance, Audio, Controls, Storage, Advanced) with honest availability labels for unimplemented options.
- Touch controls support Classic/Modern/Compact/Custom presets, radial deadzone, camera sensitivity, axis inversion, input release on pause/focus loss, and automatic hiding when a gamepad is hot-plugged.
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

- No game execution of any kind. No Android-packaged `libsotc_game.so` / ARM64 runner artifact exists in the app yet.
- M3 recompilation pipeline is not complete: KERNEL capture exists, and the recovered run is rebuilding the runner for `stage_game after KERNEL`; MANAGER/GAMECORE captures, VU1 collection/codegen, and `PIPELINE_DONE` are not evidenced yet.
- M4/M5/M6+ not started or not verified: no ARM64 runner packaged into the app, no D18 `GameNativeActivity`/disc-extraction/input bridge integration completed, no first boot, no gameplay verification.
- No ARM64 hardware verification has happened (no device connected; B4).
- No emulator verification of the new launcher/settings screens yet in this session: the host was at ~250 MiB free RAM while the WSL build was running, so starting an AVD was deferred to avoid starving the pipeline.

## Immediate Next Actions

1. **Keep the recovered M3 pipeline running:** monitor `/root/sotc/logs/pipeline-remaining.log`; confirm `stage_game after KERNEL` completes, then MANAGER and GAMECORE captures/stages, then VU1 and `PIPELINE_DONE`. Do not restart completed stages.
2. **After M3 completes (PIPELINE_DONE):** run M4 in WSL — verify/apply patch series as scripted, run `tools/capture-host/configure-android.sh` then `tools/capture-host/build-android-runner.sh` (NDK r26b, D18/D20) and verify the arm64-v8a runner artifact.
3. **M5 integration (D18):** package runner in APK, add `GameNativeActivity`, implement pre-launch full disc extraction to app-private storage, wire normalized touch/gamepad input to runtime pad path, and handle boot path / quit / `sceCdRead` fallback per evidence.
4. **M6+ validation and release (only when real):** build/test on x86_64 emulator then ARM64 device, verify boot/gameplay/input/audio/saves/lifecycle, update docs/agent memory, and only with explicit authorization publish an accurately labeled, genuinely tested APK release.
5. **UI verification when memory allows:** install the current APK on the API 28 emulator and confirm the `SOTC` label, launcher states, settings categories, touch presets, and disabled Play behavior. This is host-UI verification, not gameplay.

See `NEXT_STEPS.md` for the full ordered roadmap and `BLOCKERS.md` for known constraints.
