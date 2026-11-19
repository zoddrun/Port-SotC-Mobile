# No-Phone Verification Report

> Evidence ladder for the 2026-11-19 runtime-integration expansion.
> No physical device, USB debugging, ADB authorization or RSA prompt was used.
> Last updated: 2026-11-19 (ARM64 rebuild packaged; host smoke complete — `HOST_SMOKE_0009_OK`, 9/9).

## 1. Evidence ladder

Labels are strict and must never be promoted:

| Level | Meaning | This expansion |
|---|---|---|
| IMPLEMENTED | Source/config/test exists | Yes |
| COMPILED | Artifact compiled/linked | Host + patched ARM64 runner compiled and linked (`BUILD_OK`) |
| PACKAGED | Present in APK | Patched runner packaged; APK rebuilt with static C++ runtime |
| UNIT-TESTED | JVM test passed | 105 tests, 0 failures |
| EMULATOR-VERIFIED | API 28 x86_64 SwiftShader, host diagnostics only | Reproduced earlier; launcher re-check pending after the STL change |
| HOST-RUNTIME-TESTED | Patched runner ran on x86_64 with captured logs | Yes — `smoke-runtime-pacing.sh`, `HOST_SMOKE_0009_OK` (9/9); parsing/pacing only |
| DEVICE-VERIFIED | Real ARM64 Android hardware | No — never claimed |

## 2. Implemented and verified without a phone

### 2.1 Kotlin/JVM layer

`gradlew testDebugUnitTest`: 17 suites, **105 tests, 0 failures, 0 errors**. New coverage:

- environment names for `SOTC_FRAME_LIMIT`, `SOTC_ADAPTIVE_RESOLUTION`, `SOTC_RENDER_SCALE`, `SOTC_PERF_OVERLAY`, `PS2X_STATS`;
- all three frame-limit values `0`/`30`/`60` published as integers;
- render-scale clamp to the runner floor `0.500` even when the app slider is 25%;
- locale-independent decimal formatting under `Locale.GERMANY`;
- adaptive flag and statistics/overlay/log gate behaviour;
- GPU capability report parsing, advertised-vs-probed flags, and `ES2_SAFE` / `ES3_BASE` / `ES3_ENHANCED` / `UNKNOWN` tiers derived from probes.

`gradlew lintDebug`: **BUILD SUCCESSFUL**.

### 2.2 Native host layer

`app/src/main/cpp/engine/gpu_info.cpp` was extended with:

- EGL vendor/version/extensions and extra GL limits;
- advertised timer-query and fence-sync flags kept separate from actual probes;
- shader compile/link, RGBA8/RGB565/DEPTH16/DEPTH24_STENCIL8 framebuffer completeness, texture clear/readback and render-to-texture self-tests;
- a line-based `key=value` report consumed by `GpuInfo.parse`.

`app/build.gradle` switched to `-DANDROID_STL=c++_static`. This removes both 4 KB-aligned `libc++_shared.so` entries from the APK. The host library is loaded only from our JNI bridge, so no C++ object crosses a library boundary.

### 2.3 APK packaging gate

`gradlew assembleDebug`: **BUILD SUCCESSFUL**.

`python tools/android-integration/verify-apk.py app/build/outputs/apk/debug/app-debug.apk`:

```
APK_NATIVE_VERIFY_OK
summary  0 failures, 0 warnings
```

Checked for every packaged `.so`: ELF64, ABI-correct machine (AArch64 / x86_64), `ET_DYN`, `PT_LOAD` alignment 16384, ZIP stored and 16 KB data offset, and only resolvable `DT_NEEDED` entries. BuildIDs recorded: runner `8c5f7557f979db6ad9bcc3b64785c64f6d83413c`, ARM64 host library `f0baf0c485f52b81915b4e6729b947cb5cfb8462`, x86_64 host library `078bd30fa3f1b45272c76c90dbf4f1a43efae008`.

Final `v0.2.0-alpha.1` APK (with patched runner, version 0.2.0/code 2): 112,640,655 bytes, SHA-256 `F7A70D120B8646618E93EF77DECBDEF0426D37D703CBBD04E96955F2826767D8`. Packaged runner: 107,386,928 bytes, SHA-256 `1DD555D4C5117C9C250248FEFAD3B701C3E0CAE55E96FF48B77551BB8496DBD2`, BuildID `6a70d3160a38acb6850b59a788bc19554992e4ce` (new BuildID proves patch 0009 is in the shipped artifact).

### 2.4 Runner patch contract

Patch `tools/android-integration/patches/0009-android-runtime-pacing.patch`: 7,644 bytes, SHA-256 `bd9c5c7a5e0dc5361f9eb58b833610c2c393d6919ca0c8939c94643549dde74e`. At celosia `f8c269e5a5dc3b7afd7b25d25fe56684e1ad96f5`:

```
FORWARD CHECK: ok
APPLY: ok
REVERSE CHECK: ok
MARKERS: 5
ENV NAMES: SOTC_FRAME_LIMIT=3 SOTC_ADAPTIVE_RESOLUTION=2
REVERSE: ok
RESTORED EXACTLY: yes
```

The patch recompiles and links: the ARM64 build reported `BUILD_OK` with artifacts `RUNNER_SO_PRESENT`, and the log shows `Building CXX object ps2xRuntime/CMakeFiles/ps2_runtime.dir/src/lib/ps2_runtime.cpp.o` succeeding. API usage (`native_renderer::PerfStats`, `perfSnapshot`, `tuning()`, `ps2_stats::vblanks`, `frameHolds`) was checked against the pinned source. The new unstripped runner is 777,586,120 bytes (SHA-256 `4E276E0E0128D45627F2E1D7AD08E24CE8036C20C5A9D91E1DB531818B1115EC`, BuildID `6a70d3160a38acb6850b59a788bc19554992e4ce`); the stripped packaged copy passes `RUNNER_VERIFY_OK (0 warnings)`.

## 3. Pending no-phone checks

### 3.1 Host x86 smoke run

`tools/android-integration/smoke-runtime-pacing.sh` rebuilds the host runner incrementally and runs three 30-second sessions with llvmpipe on Xvfb :99. It is a host-only parsing/pacing diagnostic, **not** gameplay or rendering evidence.

| Case | Frame limit | Adaptive | Scale | Result |
|---|---|---|---|---|
| limit30 | 30 | 0 | 0.500 | PASS — `TIMER: ... 33.333 milliseconds`, `[pace] limit=30`, `adaptive=0`, no `[scale-adaptive]` |
| limit60 | 60 | 1 | 1.000 | PASS — `TIMER: ... 16.667 milliseconds`, `[pace] limit=60`, `adaptive=1` |
| limit0 | 0 | 0 | 1.000 | PASS — `[pace] limit=0`, `adaptive=0`, `SetTargetFPS(0)`, no host `Frame stats:` |

Final result: `HOST_SMOKE_0009_OK` — 9/9 assertions pass. The first run with a 14-second timeout failed two assertions; both were diagnosed as harness artifacts and corrected without touching the shipped runner:

1. `[pace] limit=0` was absent because the uncapped case reached the five-second pace record later than 14 s under llvmpipe. A 45-second isolated re-run produced eight `[pace] limit=0` lines. The harness timeout is now 30 s.
2. `[scale-adaptive] on:` was expected, but the host desktop-GL build never populates `perfSnapshot().frameMs` (it stays `0.000` even with `SOTC_PERF_OVERLAY=1` and `SOTC_FRAME_LOG=1`), so `adapt()`'s `frameMs > 0` guard suppresses the activation line. The assertion now checks the host-verifiable contract — `adaptive=1`/`adaptive=0` in the `[pace]` record. **Actual adaptive scale changes are still device-only and are not claimed here.**

This also documents a real coupling (see `KNOWN_ISSUES.md`): adaptive resolution depends on the perf-statistics publication path, which is tied to `SOTC_PERF_OVERLAY`. With the app default `showStatistics = false`, adaptive resolution is a no-op until statistics are enabled. No device evidence exists to confirm the Android path behaves differently.

### 3.2 Patched ARM64 runner

Accepted only when all of the following hold:

1. `BUILD_OK` and a fresh `ps2EntryRunner` BuildID;
2. `verify-runner.sh` prints `RUNNER_VERIFY_OK`;
3. new size/SHA-256 recorded and the stripped copy installed in `app/src/main/jniLibs/arm64-v8a/`;
4. `assembleDebug` + `verify-apk.py` pass again with the new runner;
5. baseline runner preserved if any gate fails.

Status: **complete**. `BUILD_OK`, fresh BuildID `6a70d3160a38acb6850b59a788bc19554992e4ce`, `RUNNER_VERIFY_OK (0 warnings)`, stripped copy installed in `jniLibs/arm64-v8a`, `assembleDebug` + `verify-apk.py` re-run and passing (`APK_NATIVE_VERIFY_OK`, 0 failures/0 warnings). All five conditions met; the new runner supersedes the baseline.

### 3.3 Launcher emulator re-check

The API 28 x86_64 emulator can exercise the launcher and `libsotc_core.so` after the static-runtime switch. It cannot load the ARM64 runner. Status: pending; earlier host-diagnostics evidence remains valid for the UI, not for gameplay.

## 4. Explicitly device-only (still unverified)

- runner load and first boot on ARM64 hardware;
- window initialization and real game rendering, including any vendor GPU;
- touch overlay geometry, multitouch ownership and gamepad merge;
- game audio volume result and lifecycle/pause/resume/rotation;
- sustained thermal behaviour and frame times;
- 16 KB page-size device loading;
- any `[pace]` line captured from a real session.

No claim in this report may be promoted to DEVICE-VERIFIED without that evidence.

## 5. Provenance

- No upstream binary, game asset, ISO data or BIOS file was copied.
- Research reports are text only, with exact revisions and licences in `UPSTREAM_REPO_AUDIT.md`.
- No first-party CI exists; all evidence above was produced locally with the documented host tools.
- The `v0.2.0-alpha.1` preview is published from this verified state with an honest "not playable / no device evidence" classification; no gameplay claim is made.
