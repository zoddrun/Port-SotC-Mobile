# No-Phone Verification Report

> Evidence ladder for the 2026-11-19 runtime-integration expansion and the
> 2026-11-20 patch-0010 defect fix.
> No physical device, USB debugging, ADB authorization or RSA prompt was used.
> Last updated: 2026-11-20 — patch 0010 gate complete; the resulting local APK
> has not been published.

## 1. Evidence ladder

Labels are strict and must never be promoted:

| Level | Meaning | This expansion |
|---|---|---|
| IMPLEMENTED | Source/config/test exists | Yes |
| COMPILED | Artifact compiled/linked | Host x86 smoke runner (no LTO, host only) and patched ARM64 runner compiled and linked |
| PACKAGED | Present in APK | Patched runner packaged in a local debug APK; not published |
| UNIT-TESTED | JVM test passed | 106 tests, 0 failures |
| EMULATOR-VERIFIED | API 28 x86_64 SwiftShader, host diagnostics only | Reproduced earlier; launcher re-check still deferred under host memory pressure |
| HOST-RUNTIME-TESTED | Patched runner ran on x86_64 with captured logs | Yes — `smoke-runtime-pacing.sh`, `HOST_SMOKE_0009_OK` (13/13); parsing, pacing and adaptive decisions only |
| DEVICE-VERIFIED | Real ARM64 Android hardware | No — never claimed |

## 2. Implemented and verified without a phone

### 2.1 Kotlin/JVM layer

`gradlew testDebugUnitTest`: 17 suites, **106 tests, 0 failures, 0 errors, 0 skipped**. Coverage includes:

- environment names for `SOTC_FRAME_LIMIT`, `SOTC_ADAPTIVE_RESOLUTION`, `SOTC_RENDER_SCALE`, `SOTC_PERF_OVERLAY`, `PS2X_STATS`;
- all three frame-limit values `0`/`30`/`60` published as integers;
- render-scale clamp to the runner floor `0.500` even when the app slider is 25%;
- locale-independent decimal formatting under `Locale.GERMANY`;
- adaptive flag and statistics/overlay/log gate behaviour;
- GPU capability report parsing, advertised-vs-probed flags and probe-derived tiers;
- recognition of all nine regional serials from the production specification, while `SupportedDiscs` still admits only `SCUS-97472`.

`gradlew lintDebug`: **BUILD SUCCESSFUL**.

### 2.2 Native host layer

`app/src/main/cpp/engine/gpu_info.cpp` reports EGL identity/extensions, GL limits, advertised timer/fence flags and shader/FBO/colour/depth/render-to-texture self-tests as `key=value` lines parsed by `GpuInfo.kt`. Tiers come from probes, never from vendor names (D21/D25). `app/build.gradle` uses `-DANDROID_STL=c++_static`, so no `libc++_shared.so` is packaged.

### 2.3 APK packaging gate

`gradlew assembleDebug`: **BUILD SUCCESSFUL**.

`python tools/android-integration/verify-apk.py app/build/outputs/apk/debug/app-debug.apk`:

```
APK_NATIVE_VERIFY_OK
summary  0 failures, 0 warnings
```

Every packaged `.so` was checked for ELF64, correct ABI machine, `ET_DYN`, 16 KB `PT_LOAD` alignment, ZIP storage/alignment and resolvable `DT_NEEDED` entries. BuildIDs: runner `44df4606af1e0e4bdc32baabee0ed29111230706`, ARM64 host library `f0baf0c485f52b81915b4e6729b947cb5cfb8462`, x86_64 host library `078bd30fa3f1b45272c76c90dbf4f1a43efae008`.

The local debug APK is 220,051,031 bytes, SHA-256 `F2761B4A9BD1BE305FB87DD9DCBBBA5B13C097CC08A447A66BF5DFD8A40B9308` (version 0.2.0, code 2). Its packaged runner is 107,386,992 bytes, SHA-256 `FBB1D7888747A7E7C86B88508039F54A1F960F8F45258270823C92FA77619FF9`. This APK was **not published**: the latest release remains `v0.2.0-alpha.1` with its original hash `F7A70D12...367D8`.

### 2.4 Runner patch contract

Patch `0009-android-runtime-pacing.patch`: SHA-256 `bd9c5c7a5e0dc5361f9eb58b833610c2c393d6919ca0c8939c94643549dde74e`. At celosia `f8c269e5a5dc3b7afd7b25d25fe56684e1ad96f5`:

```
FORWARD CHECK: ok
APPLY: ok
REVERSE CHECK: ok
MARKERS: 5
ENV NAMES: SOTC_FRAME_LIMIT=3 SOTC_ADAPTIVE_RESOLUTION=2
REVERSE: ok
RESTORED EXACTLY: yes
```

Patch `0010-android-adaptive-frame-time.patch`: SHA-256 `de23a8b679bc9b059082842adab2de847c90191bc4d1d7aafdb39c5f3a7a8f72`, marker `sotc_pacing::g_frameMs.store`, forward/reverse apply and byte-for-byte restore verified at the same commit. It changes only `ps2_runtime.cpp`: `adapt()` now receives raylib's `GetFrameTime()` when `perfSnapshot()` has no frame time, and the `[pace]` record prints that live value. The perf-overlay path remains intact when enabled.

The patched ARM64 runner was rebuilt (`BUILD_OK`, log `/root/sotc/logs/build-android-ps2EntryRunner-20261120-013240.log`): unstripped 777,589,832 bytes, SHA-256 `D3AE4C528EE2537CD7DA64CC08AE9FED87E8BC2D6E499EDF46512B92332693C4`, BuildID `44df4606...706`; stripped copy 107,386,992 bytes, SHA-256 `FBB1D788...19FF9`, `RUNNER_VERIFY_OK (0 warnings)`.

### 2.5 Host x86 smoke run (patches 0009/0010)

The host smoke binary was rebuilt with `-fno-lto` **only for this verification host**. GCC LTO WPA had used ~1.3 GB RSS for over an hour inside the 1.7 GiB WSL limit; the no-LTO host binary is 76,213,160 bytes instead of the previous 72,237,696-byte LTO build. Game sources and the ARM64 artifact are unchanged (D30).

`tools/android-integration/smoke-runtime-pacing.sh` ran three sessions under Xvfb :99 with software GL: `limit30` for 30 s, `limit60` for 30 s and `limit0` for 60 s. Result: `HOST_SMOKE_0009_OK`, **13/13 assertions**.

| # | Assertion | Evidence |
|---|---|---|
| 1 | 30 fps target time | `TIMER: Target time per frame: 33.333 milliseconds` |
| 2 | 60 fps target time | `TIMER: Target time per frame: 16.667 milliseconds` |
| 3 | `[pace] limit=30` | present in `limit30.log` |
| 4 | `[pace] limit=60` | present in `limit60.log` |
| 5 | `[pace] limit=0` | present in `limit0.log` |
| 6 | adaptive accepted | `adaptive=1 frameMs` in `limit60.log` |
| 7 | adaptive disabled (30) | `adaptive=0 frameMs` in `limit30.log` |
| 8 | adaptive disabled (0) | `adaptive=0 frameMs` in `limit0.log` |
| 9 | live frame time | at least one positive `frameMs` with `SOTC_PERF_OVERLAY=0` |
| 10 | activation recorded | `[scale-adaptive] on: ceiling=1.000 floor=0.500` |
| 11 | scale actually changed | `[scale-adaptive] frame 391: render scale 0.950` |
| 12 | disabled emits no scale change | no `[scale-adaptive]` in `limit30.log` |
| 13 | no host stats leak | no `Frame stats:` in gameplay logs |

Representative records:

```
[pace] limit=30 targetFps=30 scale=0.500 adaptive=0 frameMs=33.334 ...
[pace] limit=60 targetFps=60 scale=1.000 adaptive=1 frameMs=29.708 ...
[scale-adaptive] on: ceiling=1.000 floor=0.500
[scale-adaptive] frame 391: render scale 0.950
[pace] limit=60 targetFps=60 scale=0.950 adaptive=1 frameMs=25.233 ...
[pace] limit=0 targetFps=0 scale=1.000 adaptive=0 frameMs=6.381 ...
```

Two harness corrections were required and are documented in D30: a startup `frameMs=0.000` is valid before the first rendered frame, so the check now requires one positive measurement rather than zero zero-valued records; and the uncapped case needs 60 s under llvmpipe to reach its first five-second `[pace]` record reliably. Neither change touches the shipped runner.

This is a host-runtime check of parsing, pacing and adaptive decisions, **not** gameplay or rendering verification.

## 3. Pending no-phone checks

### 3.1 Patched ARM64 runner

Status: **complete**. All five conditions hold: `BUILD_OK`, fresh BuildID `44df4606...706`, `RUNNER_VERIFY_OK (0 warnings)`, stripped copy installed in `jniLibs/arm64-v8a`, and `assembleDebug` + `verify-apk.py` re-run passing.

### 3.2 Launcher emulator re-check

The API 28 x86_64 emulator (`Sotc_API_28`, emulator and AVD installed) can exercise the launcher and `libsotc_core.so` after the static-runtime switch. It cannot load the ARM64 runner. Status: **deferred — host memory pressure**. The earlier host-diagnostics evidence remains valid for the UI, not for gameplay. The static-runtime change is covered by `assembleDebug`, `verify-apk.py` and 106 passing JVM tests.

## 4. Explicitly device-only (still unverified)

- runner load and first boot on ARM64 hardware;
- window initialization and real game rendering, including any vendor GPU;
- touch overlay geometry, multitouch ownership and gamepad merge;
- game audio volume result and lifecycle/pause/resume/rotation;
- sustained thermal behaviour and frame times;
- 16 KB page-size device loading;
- any `[pace]` or `[scale-adaptive]` line captured from a real session.

No claim in this report may be promoted to DEVICE-VERIFIED without that evidence.

## 5. Provenance

- No upstream binary, game asset, ISO data or BIOS file was copied.
- Research reports are text only, with exact revisions and licences in `UPSTREAM_REPO_AUDIT.md`.
- No first-party CI exists; all evidence above was produced locally with the documented host tools.
- `v0.2.0-alpha.1` remains the only published preview and is classified "not playable / no device evidence". The 2026-11-20 local debug APK is an untested-on-device build and was not published or pushed.
