# Android Porting Plan

Status: living document. No phase below is marked complete unless its exit
criteria have actually been met.
Last updated: 2026-11-08.

## Phase summary

| Phase | Name | Status | Blocking issue |
| --- | --- | --- | --- |
| 0 | Technical audit | Complete | - |
| 1 | Native foundation | Implemented, build-verified; runtime unverified | No device/emulator available on the current host |
| 2 | Engine port | Not started | Requires a recompiled `SCUS-97472` core |
| 3 | ISO support | Implemented and unit-tested | Real disc image not available for end-to-end verification |
| 4 | Gameplay | Blocked | Depends on Phase 2 |
| 5 | Controls | Implemented and unit-tested | Device-level touch/gamepad verification pending |
| 6 | Optimization | Not started | Requires a runnable core and profiling hardware |
| 7 | Application polish | Partially implemented | Runtime validation pending |
| 8 | Release validation | Blocked | Depends on Phases 2, 4, and 6 |

## PHASE 0 - Technical audit

Status: complete. See `RESEARCH.md`.

Deliverables:

- Upstream review of Celosia, PS2Recomp, and Fantaskink/SOTC.
- Disc structure, serial, ELF, and module facts.
- Decision to build the Android host independently of the core generation
  pipeline.
- Licensing analysis.

Exit criteria met:

- A concrete architecture exists.
- The emulator route is ruled out.
- The core boundary is defined.

## PHASE 1 - Native foundation

Status: implemented and build-verified. Runtime verification is blocked by the
current host environment (3.8 GB total RAM, below the API 37 emulator minimum;
no physical ARM64 device connected).

Implemented:

- Gradle 8.7 / AGP 8.5.2 / Kotlin 1.9.20 project with `compileSdk 34`,
  `minSdk 28`, `targetSdk 34`.
- `arm64-v8a` shipping ABI and an `x86_64` emulator/debug ABI.
- CMake 3.22.1 native build using NDK 26.1.10909125 and C++20.
- `libsotc_core.so` with EGL/GLES3 initialization, render thread, offscreen
  render target, and scaled blit.
- AAudio float stereo output with a procedural diagnostic ambience.
- JNI bridge that fails safely when the native library is unavailable.
- `SurfaceView`-based native surface and lifecycle integration.
- Diagnostic scene, frame timing, FPS, and status JSON.
- `libsotc_game.so` loader with ABI version checks and a restricted file
  callback.

Exit criteria:

- [x] Debug APK builds for `arm64-v8a`.
- [x] Debug APK builds for `x86_64`.
- [x] Unit tests pass.
- [x] Lint passes.
- [ ] App launches and renders on a device or emulator.
- [ ] EGL/AAudio startup confirmed in logcat.
- [ ] Surface loss/recreation verified.

The unchecked items are environmental, not code-complete claims.

## PHASE 2 - Engine port

Status: not started. This phase cannot be completed inside the Android
repository alone.

Required work:

1. Obtain a legally owned `SCUS-97472` disc image.
2. Set up the GPL-3.0 Celosia/PS2Recomp host toolchain.
3. Recompile the boot ELF.
4. Perform module capture runs and relink `KERNEL.XFF`, `MANAGER.XFF`, and
   `GAMECORE.XFF`.
5. Recompile the relinked modules.
6. Capture and recompile VU1 microcode.
7. Adapt the generated code to the host ABI in `sotc_game_api.h`.
8. Produce `libsotc_game.so` for `arm64-v8a`.
9. Package it as a debug-only side load; do not commit it or game data.

Exit criteria:

- [ ] Core exports `sotc_game_query_api` with ABI version 1.
- [ ] `initialize` succeeds against an imported game directory.
- [ ] Boot sequence renders more than a cleared frame.
- [ ] Audio callback produces game audio.
- [ ] Input reaches the game.
- [ ] Core shuts down cleanly and can be reloaded.

Risks:

- The host pipeline is macOS-oriented and may need porting work.
- GPL-3.0 obligations apply to any distributed core.
- The core may need renderer or runtime features not yet present in the host.

## PHASE 3 - ISO support

Status: implemented and covered by synthetic-disc unit tests. Not yet exercised
against a real retail image in this environment.

Implemented:

- ISO9660 primary volume descriptor parsing.
- Directory record traversal with multi-sector and multi-extent handling.
- Case-insensitive path lookup and PS2 path normalization (`\` to `/`, strip
  version suffix).
- `SYSTEM.CNF` parsing and serial derivation.
- PS2 ELF validation (32-bit, little-endian, `ET_EXEC`, `EM_MIPS`, loadable
  segments).
- Support table limited to `SCUS-97472` / `SCUS_974.72`.
- Required module presence and `xff2` magic validation.
- SAF import with staged copy, SHA-256, progress, cancellation, free-space
  checks, atomic publish, and recovery.
- Save preservation across re-import.
- Original ISO is never deleted or modified.

Exit criteria:

- [x] Synthetic valid disc accepted.
- [x] Invalid magic, missing files, wrong serial, wrong ELF, and truncated
  images rejected.
- [x] Import cancellation leaves no published install.
- [ ] Real `SCUS-97472` image imported and validated end-to-end.
- [ ] Multi-GB copy tested on device storage and through a real content
  provider.

## PHASE 4 - Gameplay

Status: blocked by Phase 2.

Planned work once a core exists:

- Boot to the title screen.
- Verify frame pacing and input latency.
- Verify memory-card save creation and reload.
- Verify pause/resume and surface recreation during gameplay.
- Verify that the core handles Android file access only through the host
  callback.
- Record actual gameplay evidence (logs, screenshots, frame statistics).

Exit criteria:

- [ ] Title screen reached.
- [ ] New game starts.
- [ ] Movement, camera, and combat input work.
- [ ] Audio and video stay synchronized.
- [ ] Save and load work across process restarts.
- [ ] No emulator, BIOS, or streaming component is involved.

## PHASE 5 - Controls

Status: implemented and unit-tested; device verification pending.

Implemented:

- Multitouch D-pad, two analog sticks, face buttons, shoulders, Start/Select,
  L3/R3.
- Edit mode with drag, resize, hide, reset, and persistence.
- Normalized geometry that adapts to screen size and aspect ratio.
- Physical gamepad discovery, key/axis mapping, and rumble.
- Radial deadzone and source merging in `PadState`.
- Settings for deadzone, invert Y, vibration, touch opacity, and touch size.

Exit criteria:

- [x] Button bit tests pass.
- [x] Touch layout persistence tests pass.
- [x] Deadzone/magnitude tests pass.
- [ ] Multitouch verified on a physical touchscreen.
- [ ] Bluetooth and USB gamepads verified.
- [ ] Rumble verified on supported hardware.
- [ ] Layout editing verified across portrait/landscape and screen sizes.

## PHASE 6 - Optimization

Status: not started.

Planned work:

- Measure frame time, GPU time, CPU time, and thermal throttling on ARM64.
- Profile the core and host separately.
- Tune resolution scaling, filtering, audio buffering, and thread priorities.
- Test sustained sessions of at least 30 minutes.
- Document every optimization with before/after measurements.

Exit criteria:

- [ ] Stable target frame rate on the reference device.
- [ ] No sustained thermal throttling that breaks playability.
- [ ] Memory usage remains bounded across level transitions.
- [ ] Measurements and methodology documented.

## PHASE 7 - Application polish

Status: partially implemented.

Implemented:

- Main, import, settings, controller, touch editor, and game screens.
- English-only UI.
- Explicit native core status.
- Diagnostics entry point.
- Pause menu and statistics overlay.
- Immersive mode and keep-screen-on.
- Recovery messaging for failed imports.
- Legal notice that the user must supply their own disc.

Remaining:

- [ ] Accessibility pass (content descriptions, focus order, scaling).
- [ ] Localization infrastructure (English source only for now).
- [ ] Device rotation and foldable behaviour review.
- [ ] Error-report export.
- [ ] Final visual pass on real hardware.
- [ ] Crash-free session testing.

## PHASE 8 - Release validation

Status: blocked.

Required evidence:

- [ ] Signed release APK/AAB installs on a clean device.
- [ ] arm64-v8a execution verified with `uname -m`/ABI inspection.
- [ ] No emulator or emulator-like component present.
- [ ] Import, gameplay, audio, controls, saves, pause/resume, and storage
  pressure tested.
- [ ] Performance and thermal report.
- [ ] Licensing and notices reviewed for all distributed artifacts.
- [ ] No game data, BIOS, or ISO in the repository or APK.
- [ ] Reproducible build instructions verified from a clean checkout.

## Immediate next actions

1. Run the app on an ARM64 Android device and capture logcat for EGL, AAudio,
   and surface lifecycle.
2. Import a legally owned `SCUS-97472` image and confirm the validation report
   and manifest.
3. Set up the GPL-3.0 host recompilation pipeline and attempt a boot ELF
   recompile, then module capture.
4. Add instrumentation tests for import recovery and settings persistence.
5. Add a benchmark harness for frame time and audio underrun reporting.

## Environment constraints recorded during this milestone

- Host total physical RAM: approximately 3.8 GB.
- Android emulator available: `Medium_Phone_API_37.0` (`x86_64`), which requires
  at least 4 GB of host RAM and refuses to start on this host.
- No physical Android device was connected.
- No retail game image was present.
- No prebuilt recompiled core was present.

These constraints prevent runtime and gameplay claims. They do not prevent the
build, unit tests, lint, and static verification that were completed.
