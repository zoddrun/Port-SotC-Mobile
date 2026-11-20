# No-Phone Verification Report

> Evidence ladder for the 2026-11-20 `v0.3.0-alpha.1` release.
> No physical device, USB debugging, ADB authorization or RSA prompt was used.
> Last updated: 2026-11-20 — all no-device gates pass and the debug APK is
> published. Physical ARM64 gameplay remains unverified.

## 1. Evidence ladder

Labels are strict and must never be promoted:

| Level | Meaning | This release |
|---|---|---|
| IMPLEMENTED | Source/config/test exists | Yes, including the original ES2/ES3 shader pack, installer, presets, native diagnostics pipeline, runner hook and refined English UI |
| COMPILED | Artifact compiled/linked | Host x86 smoke runner (manual relink, D32) and patched ARM64 runner with patches 0001-0011 |
| PACKAGED | Present in the APK | Yes — published debug APK contains the stripped runner and both host native libraries |
| UNIT-TESTED | JVM test passed | 21 suites, 139 tests, 0 failures/errors/skips |
| EMULATOR-VERIFIED | API 28 x86_64 SwiftShader, host diagnostics only | Historical evidence only; cannot load the ARM64 runner |
| HOST-RUNTIME-TESTED | Patched runner ran on x86_64 with captured logs | Yes — `HOST_SMOKE_0009_OK`, 13/13 pacing and 7/7 `[hd-shader]` checks |
| DEVICE-VERIFIED | Real ARM64 Android hardware | No — never claimed |

## 2. Implemented and verified without a phone

### 2.1 Kotlin/JVM layer

`gradlew testDebugUnitTest`: **21 suites, 139 tests, 0 failures, 0 errors, 0 skipped**. Coverage includes the environment contract (`SOTC_FRAME_LIMIT`, `SOTC_ADAPTIVE_RESOLUTION`, `SOTC_RENDER_SCALE`, `SOTC_PERF_OVERLAY`, `PS2X_STATS`, shader pack variables), locale-independent decimals, runner scale floor, GPU report parsing and probe-derived tiers, shader pack staging/verification/installer rollback, launcher and quick-settings state, tactile input mapping, and the eight recognized serials that must fail with `KNOWN_UNVERIFIED_SERIAL`.

`gradlew lintDebug`: **BUILD SUCCESSFUL**.

The app source contains no trophy catalog, RetroAchievements IDs/hashes, Portuguese Compose text, emulator backend or vendor-derived GPU tier.

### 2.2 Native host layer

`app/src/main/cpp/engine/gpu_info.cpp` reports EGL identity/extensions, GL limits, advertised timer/fence flags and shader/FBO/colour/depth/render-to-texture self-tests as `key=value` lines parsed by `GpuInfo.kt`. Tiers come from probes, never from vendor names (D21/D25). `HdShaderPipeline` compiles the capability-matching ES2 or ES3 variant on the diagnostics render thread; program binaries, anisotropic filtering and framebuffer invalidate/discard are extension-gated. `HdTextureCache` deduplicates repeated immutable diagnostics uploads. No game texture optimization is claimed.

`app/build.gradle` uses `-DANDROID_STL=c++_static`, so no `libc++_shared.so` is packaged (D24).

### 2.3 Runner patch contracts

| Patch | SHA-256 | Contract |
|---|---|---|
| `0009-android-runtime-pacing.patch` | `bd9c5c7a5e0dc5361f9eb58b833610c2c393d6919ca0c8939c94643549dde74e` | Forward/reverse apply and byte-for-byte restore verified at celosia `f8c269e` |
| `0010-android-adaptive-frame-time.patch` | `de23a8b679bc9b059082842adab2de847c90191bc4d1d7aafdb39c5f3a7a8f72` | Forward/reverse apply and byte-for-byte restore verified; marker `sotc_pacing::g_frameMs.store` |
| `0011-android-hd-shader-and-touch.patch` | `b6eec5cff3913840f89b79a08cec86ad0f3c35ba940012d9cdf307c5b4e73782` | Forward apply, reverse apply and byte-for-byte restoration verified; marker `sotc_hd_shader::initialize();` |

Patch 0011 adds the original GLSL pack loader, balanced preset defaults `sharpness=0.35` / `bloom=0.26`, an independent `[hd-shader]` telemetry record beside the unchanged `[pace]` record, sigil touch styling, a floating left stick, right-half look acquisition, grip hold mapped to the existing R1 bit and ownership cleanup on gamepad hotplug or active-state transitions. None of those behaviors has been observed on a phone.

### 2.4 Host x86 smoke run (patches 0009/0010/0011)

`tools/android-integration/smoke-runtime-pacing.sh` ran four sessions under Xvfb with software GL: `limit30` for 30 s, `limit60` for 75 s, `limit0` for 60 s and `hdshader` for 90 s. Result: `HOST_SMOKE_0009_OK`, **13/13 unchanged pacing assertions** plus **7/7 independent shader assertions**.

Key evidence:

```text
PASS  30 target time (1)
PASS  60 target time (1)
PASS  30 pace limit (2)
PASS  60 pace limit (14)
PASS  0 pace limit (11)
PASS  adaptive case accepts adaptive=1 (14)
PASS  live frame time in pace record (14)
PASS  adaptive activation recorded (1)
PASS  adaptive scale actually changed (10)
PASS  disabled adaptive emits no scale change absent ([scale-adaptive])
PASS  no host Frame stats in gameplay absent (Frame stats:)
PASS  hd shader source and exact preset defaults registered (1)
PASS  hd shader linked into runner (1)
PASS  hd shader compiled and loaded (18)
PASS  hd shader telemetry cadence (18 >= 2)
PASS  hd shader reports chosen preset (18)
PASS  hd shader failure absent absent ([hd-shader] enabled=0)
PASS  pace unchanged beside shader (17)
HOST_SMOKE_0009_OK
```

Representative records:

```text
[scale-adaptive] on: ceiling=1.000 floor=0.500
[scale-adaptive] frame 34: render scale 0.950
[hd-shader] enabled=1 compiled=1 loaded=1 backend=gl330 preset=balanced version=1.0.0 sharpness=0.35 bloom=0.26 fxaa=1.00 texel=0.00156,0.00195 frames=34 reason=ok
[pace] limit=60 targetFps=60 scale=0.800 adaptive=1 frameMs=16.667 gameFps=19.00 shownFps=0.60 holds=0,0,0,3
```

The host smoke runner was compiled and relinked with the exact commands from `ninja -t commands` because a restored `.ninja_log` made Ninja treat all ~9,300 host objects as dirty (D32). `SOTC_SKIP_HOST_REBUILD=1` records that path in the smoke log. Only harness wall-clock windows changed (75 s and 90 s for the full-scale cases); the assertion patterns and expected values are unchanged. The shipped ARM64 runner used the normal CMake/Ninja/LTO pipeline.

This is a host-runtime check of parsing, pacing, shader-module compilation and telemetry, **not** gameplay or visual verification.

### 2.5 ARM64 runner rebuild

`/root/sotc/build-android/ps2xRuntime/libps2EntryRunner.so` was rebuilt from celosia `f8c269e` with patches 0001-0011:

- `BUILD_OK`; log `/root/sotc/logs/build-android-ps2EntryRunner-20261120-092740.log`.
- Unstripped: 777,928,592 bytes, SHA-256 `08C541F06B5E651B733016C6D86C0F2025A987B4A35DB94545EB237D45180045`.
- BuildID: `47975ca2bbe4958a5776031c7d8c7215709c3087`.
- Stripped by `llvm-strip --strip-unneeded`: 107,409,464 bytes, SHA-256 `E7BD58EFFCCB461A2404EC1600077CA245C40590D23E662B4798019587F365766`.
- `tools/android-integration/verify-runner.sh`: `RUNNER_VERIFY_OK (0 warnings)` — ELF64 AArch64 DYN, 16 KB LOAD alignment, `ANativeActivity_onCreate` and touch symbols exported, no `libc++_shared.so`, links `libandroid.so`.

### 2.6 APK packaging gate

`gradlew assembleDebug`: **BUILD SUCCESSFUL** with JDK 17.

`python3 tools/android-integration/verify-apk.py app/build/outputs/apk/debug/app-debug.apk`:

```text
APK_NATIVE_VERIFY_OK
summary  0 failures, 0 warnings
```

Every packaged `.so` was checked for ELF64, correct ABI machine, `ET_DYN`, 16 KB `PT_LOAD` alignment, ZIP storage/alignment and resolvable `DT_NEEDED` entries. BuildIDs: runner `47975ca2bbe4958a5776031c7d8c7215709c3087`, ARM64 host library `a01148ec3b8b39d310efcc67ea1e8eef342c9f4c`, x86_64 host library `cdd9f55c90fe2d03179dac6f58443c45db898dc2`.

Published asset `SotC-Mobile-v0.3.0-alpha.1-debug.apk`: 238,697,332 bytes, SHA-256 `8D83BD57FC6858DD41F95256CC4F1C4BEB1A4E8B348E401F02464E2FD18BDEE8`. `aapt2 dump badging` confirms package `com.portsotc.mobile`, version `0.3.0` (code 3), label `SOTC`, and native libraries for `arm64-v8a` and `x86_64`.

## 3. Explicitly device-only (still unverified)

- runner load and first boot on ARM64 hardware;
- window initialization and real game rendering, including any vendor GPU;
- game audio output and volume result;
- touch overlay geometry, sigil/stick/look/grip ergonomics, multitouch ownership and gamepad merge;
- lifecycle: pause/resume, focus loss, rotation and process death;
- game saves and disc-tree behavior on a real device;
- sustained thermal behavior, frame times and 16 KB page-size loading;
- ES 3 rendering of the game picture and any HD shader visual output;
- any `[pace]`, `[scale-adaptive]` or `[hd-shader]` line captured from a real session.

No claim in this report may be promoted to DEVICE-VERIFIED without that evidence.

## 4. Provenance

- No upstream binary, game asset, ISO data or BIOS file was copied.
- The bundled shader pack is original project work under `app/src/main/assets/shaders/LICENSE` (MIT).
- The private disc image under `original iso/` was never committed, modified, moved or deleted.
- Research reports are text only, with exact revisions and licences in `UPSTREAM_REPO_AUDIT.md`.
- No first-party CI exists; all evidence above was produced locally with the documented host tools.
- `v0.3.0-alpha.1` is classified "not playable / no device evidence"; the debug APK is a development preview, not a release candidate.
