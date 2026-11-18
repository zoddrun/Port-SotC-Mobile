# Project State

> Agent memory file. Read this first when resuming work.
> Last updated: 2026-11-18

## Project Identity

- **Project:** Port-SotC-Mobile - native Android port of Shadow of the Colossus (PS2, SCUS-97472 USA)
- **Method:** Static recompilation of PS2 EE code to native ARM64 (Celosia / PS2Recomp pipeline). NOT emulation.
- **Repository:** https://github.com/zoddrun/Port-SotC-Mobile.git (branch `main`; remote `origin` published and public as of 2026-11-18; documentation commit `42cf6c3`, merge commit `50bd46c`)
- **Hard rules:** 100% English everywhere; no emulator backend ever; no game assets committed; never fake functionality.
- **Latest release:** `v0.1.0-alpha.1` - Platform Preview (2026-11-18). Android host application only; **not playable**; debug-signed APK. See `docs/releases/v0.1.0-alpha.1.md`.

## Milestone Status

| Milestone | Description | Status |
|---|---|---|
| 0 | Technical / repository recovery audit | **COMPLETE** (2026-11-09) |
| 1 | Original ISO validation | **COMPLETE** (2026-11-09) |
| 2 | Celosia upstream audit | **COMPLETE** (2026-11-09, background research) |
| 3 | Real recompilation pipeline run (extract -> capture -> recompile) | **IN PROGRESS (recovered 2026-11-18)** - extract, configure, ps2_recomp build, stage_boot (1429 functions) done; **ps2EntryRunner BUILT (attempt 6, 17.9 MB)**; **all three modules captured** (`kernel.bin`, `manager.bin`, `gamecore.bin`, 32 MiB each in `build/captures/`); `stage_game after KERNEL` completed and **`stage_game after GAMECORE` is rebuilding the runner** (9,196 / 9,607 objects at 18:48). No VU1 output or `PIPELINE_DONE` yet. Each stage_game round is a full runner rebuild on a 4-core / 1.7 GiB WSL host, so the remaining work is the LTO link plus VU1. |
| 4 | ARM64 cross-compile of game core | NOT STARTED - prep done: patch series + scripts ready; NDK r26b toolchain smoke-tested **NDK_SMOKE_OK** (see NEXT_STEPS M4) |
| 5 | Runtime integration into the app (D18: raylib-owned NativeActivity; ABI-v1 re-scoped to diagnostics) | **PARTIAL (2026-11-18)** - host side implemented: `GameNativeActivity` (publishes `SOTC_BOOT_ELF`, the touch/look settings and `SOTC_AUDIO_VOLUME` before library load, redirects the runtime's stdio to a per-launch log), disc-tree extractor + marker + Storage action, launcher gating on runtime + prepared tree, patch 0004, the gameplay touch overlay + pad merge as patch 0005 (`ps2_android_input`, compile-verified for `aarch64-linux-android28`), and the audio-volume handoff as patch 0006 (applied + syntax-checked only). Blocked on an actual `libps2EntryRunner.so` and on any device/emulator run. |
| 6 | First boot on device/emulator | NOT STARTED |
| 7 | Gameplay verification | NOT STARTED |
| 8 | Performance optimization | NOT STARTED |
| 9 | Polish (controls, lifecycle, saves) | NOT STARTED |
| 10 | Release validation | NOT STARTED |

## Pipeline Workspace (Milestone 3) - /root/sotc in WSL2 Ubuntu 26.04

> 2026-11-18 recovery note: WSL is reachable and the pipeline was confirmed running via `wsl -e` (`pipeline-remaining.sh` + `ninja`). The 8 GB swapfile is active and Xvfb was repaired before the resumed run.

Adopted from the developer (D10): `/root/celosia` = clean clone of our `third_party/celosia` @ f8c269e; build in `/root/sotc/build`; disc extracted in `/root/sotc/disc`; ISO at `/root/sotc/source.iso` (hash-verified).

Helper scripts (in `/root/sotc/` - mirrored in-repo at `tools/capture-host/`, byte-verified):
- `celosia-env.sh` - wraps the celosia driver with all host workarounds (paths, `-j2`, CMake flags incl. SSE4.1 + NEON shim + policy fix, `DISPLAY=:99`, software GL). Use for `recompile` / `build` / `vu1` / `autotest`.
- `capture-module.sh KERNEL|MANAGER|GAMECORE` - celosia `capture()` replica with display env baked in.
- `pipeline-remaining.sh` - unattended continuation of the pipeline from the built boot runner through captures x3 + `stage_game` x3 + VU1 (D19: host recovery, wait-for-ninja guard, verify/retry pass, per-stage log snapshots). NOT `celosia setup` - that re-runs `stage_boot` and would discard the current build.
- `install-ndk.sh` - fetches + verifies the Linux Android NDK r26b (26.1.10909125, D20) into `/root/android-ndk-r26b` for the M4 cross-compile (idempotent; archive test + exact `Pkg.Revision` + clang smoke test; log `logs/install-ndk.log`).
- `configure-android.sh` - M4 ARM64 cross-configure (pipeline-done guards, patch verify-or-apply, NDK r26b toolchain; run only after PIPELINE_DONE).
- `build-android-runner.sh` - builds `libps2EntryRunner.so` (arm64-v8a) from that configure (per-attempt log; `BUILD_OK`/`RUNNER_SO_PRESENT` markers).
- `android-after-pipeline.sh` - unattended M4 handoff: waits for `PIPELINE_DONE`, then runs `configure-android.sh` and `build-android-runner.sh` (`ANDROID_CONFIGURE_OK` / `ANDROID_BUILD_OK` / `ANDROID_PIPELINE_FAILED`). Started detached via `launch-android-after.sh`; waiting since 11:46.
- `neon-shim/arm_neon.h` - SIMDe shim (D13); `neon-shim/mach-o/dyld.h` - dl_iterate_phdr shim (D16).
- `fix-x11.sh` / `diag-x11.sh` - per-boot WSLg/Xvfb recovery (D14; fix-x11 now loops for stacked mounts).
- `swapfile` - 8 GB swap (D15).

Verified state (2026-11-11):
- `configure` done - deps fetched (raylib 5.5, imgui, rlimgui, elfio, fmt, libdwarf, rabbitizer, toml11); `CMAKE_POLICY_VERSION_MINIMUM=3.5` applied (D11).
- `ps2_recomp` built (binary + libps2_recomp_lib.a).
- `stage_boot` done - KERNEL.XFF symbols -> `scus_symbols.txt`; `funcmap_elf.py` -> `functions.csv`; recompiler emitted 1429 C++ files to `build/runner/` (recomp.log ends "Recompilation completed successfully"); build.ninja regenerated after the copy.
- `ps2EntryRunner` builds: attempts 1-4 fixed in sequence - NEON->SIMDe shim (D13), `-msse4.1` (D13), guarded-include force-include + restricted A32V7 alias shim (D13), mach-o/dyld.h shim via dl_iterate_phdr (D16). Attempt 5 (both shims) failed in unity_45 on three errors: two x86-host bugs the developer fixed directly in /root/celosia (2026-11-10 10:04, uncommitted local edits: `coll125.h` vf0c->vf0u; `decomp_collision.cpp` guard Vita-only `fast::check49()` behind `#if PS2X_VU_NEON_A32`), and one unity-build ODR collision (`decomp_group::NextRegs` defined in 3 override files) -> **D17: unity build OFF for the capture host** (deterministic fix; also lowers per-TU memory under the 1.7 GiB limit).
- **Attempt 6 BUILD_OK** (2026-11-11 07:05): non-unity rebuild, 1567 targets at `-j2`, log `/root/sotc/logs/build-ps2EntryRunner-attempt6.log`; binary `ps2xRuntime/ps2EntryRunner` (17,944,488 bytes). One benign GCC false-positive warning (`sotc_native.cpp:438`, triaged - KNOWN_ISSUES #13). 8 GB swap re-activated (per-boot reset, KNOWN_ISSUES #10) and Xvfb :99 restarted (WSLg stacked the ro mount twice - fix-x11.sh now loops).
- **Remaining pipeline - recovered 2026-11-18:** `bash /root/sotc/pipeline-remaining.sh` was restarted after activating the 8 GB swapfile and repairing Xvfb. It passed the runner verification, re-captured KERNEL, finished `stage_game after KERNEL`, captured MANAGER and GAMECORE, and is now in **`stage_game after GAMECORE`** (full `ps2EntryRunner` rebuild; 9,196 / 9,607 objects at 18:48). Master log `/root/sotc/logs/pipeline-remaining.log`; per-stage snapshots are written as `stagegame-*.log`. Remaining after this stage: VU1 collection/codegen and `PIPELINE_DONE`. `android-after-pipeline.sh` is already waiting to start the M4 configure the moment the marker appears.
- M4 prep done: NDK r26b (26.1.10909125, D20) installed + verified at `/root/android-ndk-r26b` (Pkg.Revision + clang 17.0.2; log `logs/install-ndk.log`); both patch series mirrored (`patches-capture-host/`, `patches-android-integration/`); Android dyld shim at `android-shim/` (no SIMDe header); `configure-android.sh` + `build-android-runner.sh` ready (hash-verified mirrors). NDK toolchain smoke test passed (`tools/capture-host/tests/ndk-smoke.sh` -> NDK_SMOKE_OK 07:22: r26b toolchain + CMake 4.2.3 + Ninja + arm64-v8a + android-28 produce valid AArch64 Android ELFs; D20 fallback retired). M5 runtime I/O model researched from source and recorded (NEXT_STEPS M5: full-disc extraction required; mc0/mc1 beside the boot ELF; cdImage ISO fallback needs a patch, deferred to M6 evidence).

## What Works Today (verified)

- Android host app builds (Gradle wrapper, arm64-v8a + x86_64) and has 76 passing unit tests; lint and `assembleDebug` pass.
- Installed application label is `SOTC`; application ID remains `com.portsotc.mobile`; debug APK SHA-256 `1E6D5D01CDA9F299C9C951115D6D7BE497CEA25DD413CAF46CCF9AF3F024B3F4`.
- Launcher is a product screen: status card, single primary Play action, details dialog, import/settings/controls actions. Play is disabled when the runtime is absent; when the disc tree is not prepared it opens Storage instead of starting a session. Diagnostics are reachable only from Settings -> Advanced.
- Disc-tree extraction is implemented and tested: `DiscTreeExtractor` extracts the whole ISO into `<files>/games/SCUS-97472/disc/`, publishes atomically, writes `disc_tree.properties` last, creates `mc0`, and is exposed as `PREPARE GAME DATA` / `RE-EXTRACT DISC DATA` in Settings -> Storage with progress and cancellation. 5 unit tests cover the paths.
- Gameplay host side is wired: `GameNativeActivity` (NativeActivity, `android.app.lib_name=ps2EntryRunner`) publishes `SOTC_BOOT_ELF`, the control knobs and `SOTC_AUDIO_VOLUME` through `NativeCore.setEnvironment` before `super.onCreate`, redirects the runtime's stdio to `<files>/logs/game-<yyyyMMdd-HHmmss>.log` (`nativeRedirectStdio`), and `core/RuntimeEnvironment.kt` owns the variable names and locale-independent formatting. Patches 0004/0005/0006 teach the runner to read them. The runner artifact itself does not exist yet.
- `CoreProbe` detects native libraries both in the extracted `nativeLibraryDir` and inside the APK for the device's ABIs, covering uncompressed (`useLegacyPackaging=false`) packaging. `tools/android-integration/package-runner.sh` verifies (AArch64, `ANativeActivity_onCreate`) and copies the runner into `jniLibs/arm64-v8a` without committing it.
- Settings are categorized (General, Graphics, Performance, Audio, Controls, Storage, Advanced) with honest availability labels for unimplemented options.
- Touch controls support Classic/Modern/Compact/Custom presets, radial deadzone, camera sensitivity, axis inversion, input release on pause/focus loss, and automatic hiding when a gamepad is hot-plugged. The gameplay window has its own native overlay (patch 0005) that merges into the PS2 pad wire format; it compiles for ARM64 but has not run.
- Audio settings reach gameplay: the Audio category's toggle and master volume are published as `SOTC_AUDIO_VOLUME` and applied by patch 0006 right after the runtime loads its own settings, so mute works. The runtime's own menu can still change the volume for the current session. Not yet exercised on a device.
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

- No game execution of any kind. No Android-packaged `libps2EntryRunner.so` / `libsotc_game.so` / ARM64 runner artifact exists in the app yet.
- M3 recompilation pipeline is not complete: KERNEL capture exists, and the recovered run is rebuilding the runner for `stage_game after KERNEL`; MANAGER/GAMECORE captures, VU1 collection/codegen, and `PIPELINE_DONE` are not evidenced yet.
- M4 not started: no ARM64 runner has been cross-compiled.
- M5 is only partially done: the activity, boot-path and settings handoff, disc-tree extraction, launcher gating, and the runner-side touch overlay exist, but nothing has been linked or booted on a device; no first boot or gameplay verification has happened.
- No ARM64 hardware verification has happened (no device connected; B4).
- No emulator verification of the new launcher/settings screens yet in this session: the host was at ~250 MiB free RAM while the WSL build was running, so starting an AVD was deferred to avoid starving the pipeline.

## Immediate Next Actions

1. **Keep the recovered M3 pipeline running:** monitor `/root/sotc/logs/pipeline-remaining.log`; confirm `stage_game after KERNEL` completes, then MANAGER and GAMECORE captures/stages, then VU1 and `PIPELINE_DONE`. Do not restart completed stages.
2. **After M3 completes (PIPELINE_DONE):** run M4 in WSL - verify/apply patch series as scripted, run `tools/capture-host/configure-android.sh` then `tools/capture-host/build-android-runner.sh` (NDK r26b, D18/D20) and verify the arm64-v8a runner artifact. `tools/capture-host/android-after-pipeline.sh` does exactly this unattended: it waits for the pipeline to exit, then runs both scripts and prints `ANDROID_CONFIGURE_OK` / `ANDROID_BUILD_OK` / `ANDROID_PIPELINE_FAILED`.
3. **M5 integration (D18):** package the runner in the APK when M4 produces it, then verify the native overlay/pad merge on a device (geometry, multitouch ownership, gamepad coexistence), handle quit and any `sceCdRead` fallback per evidence. Disc extraction, `GameNativeActivity`, boot-path/settings handoff (patches 0004/0005), and launcher gating are already implemented.
4. **M6+ validation and release (only when real):** build/test on x86_64 emulator then ARM64 device, verify boot/gameplay/input/audio/saves/lifecycle, update docs/agent memory, and only with explicit authorization publish an accurately labeled, genuinely tested APK release.
5. **UI verification when memory allows:** install the current APK on the API 28 emulator and confirm the `SOTC` label, launcher states, settings categories, touch presets, and disabled Play behavior. This is host-UI verification, not gameplay.

See `NEXT_STEPS.md` for the full ordered roadmap and `BLOCKERS.md` for known constraints.
