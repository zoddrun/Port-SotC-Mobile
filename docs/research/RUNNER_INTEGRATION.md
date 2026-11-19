# Runner Integration Research Report

**Status:** research and design only. No source file was modified.  
**Date:** 2026-11-19  
**Output:** this report.  
**Scope:** connect the Android settings screen to the real raylib-owned game loop in `libps2EntryRunner.so`, with exactly one owner for simulation, rendering, presentation, pacing, and dynamic resolution.

## 1. Executive determinations

1. **The raylib `NativeActivity` remains the sole gameplay owner.** `GameNativeActivity` must not start `Engine`, must not drive `GameCore::render`, and must not introduce a host step/pull path. `PS2Runtime::run()` owns the host frame; `UploadFrame()` owns guest-frame presentation timing; the native renderer owns GL work.

2. **Do not add JNI for settings.** Android starts a `NativeActivity` without useful argv: raylib 5.5 `src/platforms/rcore_android.c:273-282` calls `main(1, { "raylib", NULL })`, ignores its return, then finishes the activity. The established startup channel is the process environment published by `RuntimeEnvironment.publish(...)` through `NativeCore.setEnvironment` before `super.onCreate` (`GameNativeActivity.kt:66-80`). All current runner knobs are `std::getenv` based. Adding JNI would create a second, harder-to-test interface with no benefit.

3. **One new upstream patch is sufficient for the missing behaviour.** Add a proposed `0009-android-runtime-pacing.patch` against celosia `f8c269e`, touching primarily `PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp`. It should:
   - honour `SOTC_FRAME_LIMIT` (`0`, `30`, `60`) by overriding the current `wantFps` expression before `SetTargetFPS(wantFps)` (`ps2_runtime.cpp:3190-3195`);
   - honour `SOTC_ADAPTIVE_RESOLUTION` (`0`/`1`) by adjusting `native_renderer::tuning().renderScale` in the runner, mirroring the host engine's smoothing and cadence;
   - publish a 5-second `[pace]` log beside the existing `PS2X_STATS` block (`ps2_runtime.cpp:3354-3432`) so `GameLogs.kt` can read measured frame time, selected limit, and effective render scale.

4. **Do not use `SOTC_CAP20=1` as the app's 20 fps or battery setting.** Upstream's Steady 20 path changes guest frame release cadence and the game time step to 50 ms (`native_tuning.h:758-761`, `ps2_runtime.cpp:3186-3190`). The app's frame limit is a presentation cap only; it must leave the guest timeline alone.

5. **The host `Engine` remains diagnostics/ABI-v1 code only.** `app/src/main/cpp/engine/engine.cpp` contains useful reference behaviour (25% floor, 5% steps, 0.75 s cadence, 1.20x/0.75x thresholds, thermal caps, 5-second `Frame stats:` log) but none of it currently reaches gameplay. Mirror the relevant parts in the runner; never run both owners at once.

6. **Most Android settings are already honest or can be made honest with one patch.** The exceptions are host-only diagnostics, presentation filtering, gamepad-only fields, custom touch layout, and the 25% resolution floor, which upstream clamps to 50% (`native_tuning.h:803`).

7. **A phone is not required for regression evidence.** Kotlin unit tests, patch-contract checks, a host x86 smoke run with the same environment, and stricter ELF verification can prove the contract. First-boot rendering, touch geometry, and thermal behaviour still require a real ARM64 device and remain explicitly unverified.

8. **Rebuild cost dominates schedule risk.** The last full ARM64 build compiled 9,333 objects and took about 55 minutes for thin-LTO. Preserve the current packaged baseline (`107,384,168` bytes, SHA-256 `134F2FBF3B0A5F260FFF8A01B5F36221AAEEC377242B602B7F6952B18B9AEB8C`) until a new artifact passes every check.

## 2. Evidence base

Local repository files inspected:

- `tools/android-integration/README.md`
- `tools/android-integration/verify-runner.sh`
- `tools/android-integration/package-runner.sh`
- `tools/android-integration/patches/0001`-`0008` and `patches/apply-patches.sh`
- `app/src/main/java/com/portsotc/mobile/GameNativeActivity.kt`
- `app/src/main/java/com/portsotc/mobile/core/RuntimeEnvironment.kt`
- `app/src/main/java/com/portsotc/mobile/core/SettingsStore.kt`
- `app/src/main/java/com/portsotc/mobile/core/GraphicsPreset.kt`
- `app/src/main/java/com/portsotc/mobile/core/GameLogs.kt`
- `app/src/main/cpp/engine/engine.cpp`
- `app/src/main/cpp/engine/egl_renderer.cpp`
- `app/src/test/java/com/portsotc/mobile/core/RuntimeEnvironmentTest.kt` and related tests

Upstream sources inspected at exact revisions:

- celosia `f8c269e`, especially:
  - `PS2Recomp/ps2xRuntime/CMakeLists.txt`
  - `PS2Recomp/ps2xRuntime/src/main.cpp`
  - `PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp`
  - `PS2Recomp/ps2xRuntime/src/lib/native/native_renderer.cpp`
  - `PS2Recomp/ps2xRuntime/src/lib/host_menu.cpp`
  - `PS2Recomp/ps2xRuntime/include/runtime/native_tuning.h`
  - `PS2Recomp/ps2xRuntime/include/runtime/ps2_stats.h`
- raylib `5.5`:
  - `src/platforms/rcore_android.c`
  - `src/rcore.c`

## 3. Current runtime topology and the single-owner rule

| Concern | Actual gameplay owner today | Evidence | Required decision |
|---|---|---|---|
| Simulation | PS2 EE/IOP/GS threads inside `PS2Runtime` | `PS2Runtime::run()` starts at `ps2_runtime.cpp:2937`; patch 0007 keeps the Android scheduler buildable | Leave untouched. Do not add host stepping. |
| Rendering | celosia native renderer plus raylib drawing | `drawHostFrame` lambda at `ps2_runtime.cpp:2964`; `BeginDrawing()` at 2966; native renderer sources added at `PS2Recomp/ps2xRuntime/CMakeLists.txt:456` | Keep. No new EGL backend. |
| Presentation | `UploadFrame()` | `ps2_runtime.cpp:444-497`: `kVsyncRate = 59.94`, `kLatencyVsyncs = 2.0`, `eeScheduler().currentVSyncTick()`, `gs().latchHostPresentationFrame()`, `ps2_stats::frameHolds` | Keep. This is the game's picture-timing authority. |
| Host pacing | raylib `SetTargetFPS` / `EndDrawing` | `rcore.c:1645-1651` sets `CORE.Time.target`; `rcore.c:939-956` waits only when `CORE.Time.frame < CORE.Time.target`; `eglSwapInterval` is commented out on Android (`rcore_android.c:913-914`) | Make app setting authoritative here only. |
| Dynamic resolution | runner renderer scale | `native_tuning.h:803-807` clamps render scale to `0.5..2` on this platform; `native_renderer.cpp:2858-2873` already mutates `tuning().renderScale` live | Move host-engine adaptive logic into the runner loop; do not resize a host offscreen target. |
| Thermal response | host `Engine` only | `engine.cpp:30-39`, `126-137` | Out of first patch. A future Android thermal publisher would be a separate, device-tested change. |
| App-layer frame loop | host `Engine` (diagnostics / ABI-v1) | `engine.cpp:52-71`, `160-351`; `egl_renderer.cpp:249` `swapBuffers()` | Never call from `GameNativeActivity`. Keep as reference implementation only. |

The rule is therefore: **settings change knobs inside the runner; they never become a second loop.** The only new writes from the app are process environment values published before the runner loads.

## 4. Upstream patch targets

### 4.1 Existing series (unchanged by this research)

| Patch | Target path(s) | Function / area | Role |
|---|---|---|---|
| 0001 | `PS2Recomp/ps2xRuntime/src/lib/host_menu.cpp` | menu selectors and language text | English-only menu |
| 0002 | `PS2Recomp/ps2xRuntime/src/lib/games_database.cpp` | game-name table | resolves `SCUS-97472` |
| 0003 | `PS2Recomp/ps2xRuntime/src/main.cpp` | Android exit path | returns instead of `std::_Exit`, matching raylib's graceful teardown |
| 0004 | `PS2Recomp/ps2xRuntime/src/main.cpp` | boot ELF selection | `SOTC_BOOT_ELF` |
| 0005 | new `include/runtime/ps2_android_input.h`, `src/lib/ps2_android_runtime.cpp`, `src/lib/ps2_pad.cpp`, `src/lib/ps2_runtime.cpp` | touch sampling, pad merge, overlay | touch controls and look knobs |
| 0006 | `PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp` | `PS2Runtime::initialize()` | `SOTC_AUDIO_VOLUME` after `hostmenu::load()` |
| 0007 | `PS2Recomp/ps2xRuntime/src/lib/Kernel/EeScheduler.cpp` | audit backtrace guard | Android API 28 compile fix |
| 0008 | `game/overrides/sotc_native.cpp` | `sfmt105` forwards | NDK format-security compile fix |

All eight were compile/link verified in the Milestone 4 run, but none has been run on ARM64 hardware. Patch 0005's overlay geometry, multitouch ownership, and pad merge remain device-only evidence.

### 4.2 Proposed patch 0009: app-authoritative pacing and adaptive resolution

**Primary target:** `PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp`.

- Add local `std::getenv` helpers in the existing anonymous namespace or near other runners helpers. Parse:
  - `SOTC_FRAME_LIMIT` as exactly `0`, `30`, or `60`; anything else falls back to `60`.
  - `SOTC_ADAPTIVE_RESOLUTION` as `1` or `0`; anything else falls back to `1` to preserve current app defaults.
  - Log the parsed values once on first use so `GameLogs.kt` can prove what the runner accepted.
- At `ps2_runtime.cpp:3190-3195`, if `SOTC_FRAME_LIMIT` was published, set `wantFps` to that value before `SetTargetFPS(wantFps)`. If it was not published, keep the upstream `steady20`/`frameCap30` expression unchanged for non-Android hosts.
- In `PS2Runtime::run()`, after the host drawing path and with the same frame cadence as the host engine, maintain a local smoothed frame time and effective scale. Use `native_renderer::perfSnapshot(PerfStats&)` (`native_renderer.cpp:1140-1147`) when available, or the host frame interval already measured in the loop. Write the result only to `native_renderer::tuning().renderScale`, the mechanism proven by `SOTC_SCALE_CYCLE` (`native_renderer.cpp:2858-2873`). Skip adaptive writes when `SOTC_SCALE_CYCLE` is set, to avoid two writers.
- Use the host engine's behaviour as the reference contract (`engine.cpp:288-318`), translated to runner scale units:
  - ceiling = configured `SOTC_RENDER_SCALE`, captured at first use;
  - floor = `max(0.5, ceiling * 0.5)` because upstream minimum render scale is `0.5` (`native_tuning.h:803`);
  - step = `0.05` of absolute scale (equivalent to 5 percentage points at `1.0`);
  - cadence = `0.75 s`;
  - reduce when smoothed frame time is over `1.20 * budget`, raise when under `0.75 * budget`;
  - budget = `1000 / (frameLimit > 0 ? frameLimit : 60)`;
  - reset the smoother after a scale change.
- Add a 5-second `[pace]` line under the existing `PS2X_STATS` gate (`ps2_runtime.cpp:3354-3432`) containing selected limit, actual `SetTargetFPS` value, `renderScale`, adaptive flag, `PerfStats.frameMs`, `gameFps`, `shownFps`, and `ps2_stats::frameHolds`.

**Optional follow-up, not in the first patch:** extend `native_renderer::PerfStats` (`native_tuning.h:768-778`) with limit/scale fields and show them in `host_menu.cpp:742-818`. This requires updating every `perfPublish()` producer and should only be done after the standalone `[pace]` log identifies a real need.

**No CMake change is required.** No new source file is needed; all edits are in an existing compiled translation unit. Add a marker such as `Port-SotC-Mobile (D18): app-authoritative frame limit` to `patches/apply-patches.sh` so the new patch is idempotent.

## 5. Stable environment / startup interface

### 5.1 Why environment, not JNI

- `NativeActivity` receives no application argv. Raylib supplies only `argv[0] = "raylib"` (`rcore_android.c:270-282`).
- `RuntimeEnvironment.kt:5-13` documents the environment channel and keeps names aligned with runner `std::getenv` calls.
- `GameNativeActivity.kt:39-80` redirects stdio, loads settings, publishes values, logs failures, and only then calls `super.onCreate`, which loads the library. This ordering is the contract.
- All runner readers use startup-cached values (`static const` in patch 0005; `tuningFirst()` in `native_renderer.cpp:160-242`; patch 0006 in `initialize()`). Changing Android settings during gameplay therefore applies on the next launch. That is acceptable and must remain stated in the UI/docs.

### 5.2 Current published names

From `RuntimeEnvironment.kt:17-45` and `67-77`:

| Constant | Env name | Format / range |
|---|---|---|
| `BOOT_PATH` | `SOTC_BOOT_ELF` | absolute path; omitted when blank |
| `TOUCH_OVERLAY` | `SOTC_TOUCH_OVERLAY` | `1`/`0` |
| `TOUCH_PRESET` | `SOTC_TOUCH_PRESET` | `classic`, `modern`, `compact` |
| `TOUCH_HIDE_WITH_GAMEPAD` | `SOTC_TOUCH_HIDE_WITH_GAMEPAD` | `1`/`0` |
| `TOUCH_SCALE` | `SOTC_TOUCH_SCALE` | decimal, runner clamps `0.6..1.4` |
| `TOUCH_ALPHA` | `SOTC_TOUCH_ALPHA` | decimal, runner clamps `0.2..1.0` |
| `TOUCH_DEADZONE` | `SOTC_TOUCH_DEADZONE` | decimal, runner clamps `0..0.6` |
| `LOOK_SENSITIVITY` | `SOTC_LOOK_SENSITIVITY` | decimal, runner clamps `0.2..3` |
| `INVERT_LOOK_X` | `SOTC_INVERT_LOOK_X` | `1`/`0` |
| `AUDIO_VOLUME` | `SOTC_AUDIO_VOLUME` | decimal `0..1`; patch 0006 quantizes to 11 steps |

Numeric values use `String.format(Locale.US, "%.3f", value)` (`RuntimeEnvironment.kt:91`) because `std::atof` stops at a comma.

### 5.3 New names required for the runner patch

These would be added as constants in `RuntimeEnvironment.kt` and included in the same published map:

| Proposed constant | Env name | Values | Reader |
|---|---|---|---|
| `FRAME_LIMIT` | `SOTC_FRAME_LIMIT` | `0`, `30`, `60` | proposed patch 0009 in `ps2_runtime.cpp` |
| `ADAPTIVE_RESOLUTION` | `SOTC_ADAPTIVE_RESOLUTION` | `1`, `0` | proposed patch 0009 in `ps2_runtime.cpp` |
| `RENDER_SCALE` | `SOTC_RENDER_SCALE` | decimal `0.500`, `0.750`, `1.000` | already read at `native_renderer.cpp:102` |
| `PERF_OVERLAY` | `SOTC_PERF_OVERLAY` | `1`, `0` | already read at `native_renderer.cpp:181`, drawn at `host_menu.cpp:776-803` |
| `STATS` | `PS2X_STATS` | present / absent | already read at `ps2_runtime.cpp:3356` |

`SOTC_FRAME_LIMIT` and `SOTC_ADAPTIVE_RESOLUTION` are new runner knobs, not invented existing APIs. `SOTC_RENDER_SCALE`, `SOTC_PERF_OVERLAY`, and `PS2X_STATS` already exist upstream.

### 5.4 Contract tests for the interface

Keep the interface testable without a phone:

- every name in `RuntimeEnvironment.kt` has a corresponding `std::getenv` string in the patched celosia tree;
- every runner `SOTC_*` name used by gameplay settings is published by the app;
- `NativeCore.setEnvironment` failures are included in the returned list and logged (`RuntimeEnvironment.publish`, `GameNativeActivity.kt:76-78`);
- no new settings are read via JNI or `argv`;
- locale formatting remains dot-decimal.

## 6. Honourable settings matrix

| `Settings` field | Gameplay status | Runner path | Notes |
|---|---|---|---|
| `resolutionScalePercent` | Partial now; fully intended | `SOTC_RENDER_SCALE` → `native_renderer.cpp:102` → `tuning().renderScale` | App allows `25..100`; upstream clamps to `0.5..1.0` for those percentages, so `25` becomes `50`. Do not claim 25% support. Adaptive patch uses the published value as its ceiling. |
| `linearFiltering` | Not honoured | host `Engine` only | `SOTC_TEXTURE_FILTER` controls texture sampling, not the host present blit. Do not conflate them. |
| `audioEnabled`, `audioVolume` | Honoured now | `SOTC_AUDIO_VOLUME`; patch 0006 in `PS2Runtime::initialize()` | Mute becomes `0.000`; runner quantizes to `0..10` menu steps. Runtime menu changes still win for that session. |
| `diagnosticsOverlay` | Not honoured in gameplay | host `GameActivity` only | Keep as diagnostics UI. Do not map to the runner perf overlay. |
| `showStatistics` | Honourable now | `SOTC_PERF_OVERLAY=1` | Shows the existing 3-line overlay plus 120-frame graph (`host_menu.cpp:790-816`). No runner patch needed. |
| `touchControlsEnabled` | Honoured now | `SOTC_TOUCH_OVERLAY`; patch 0005 | `0` removes overlay and touch input. |
| `touchPreset` | Partial now | `SOTC_TOUCH_PRESET`; patch 0005 | `classic`/`modern`/`compact` only; `custom` falls back to classic geometry. |
| `hideTouchWithGamepad` | Honoured now | `SOTC_TOUCH_HIDE_WITH_GAMEPAD`; patch 0005 | Uses `IsGamepadAvailable(0)` each frame. |
| `touchScale` | Honoured within clamps | `SOTC_TOUCH_SCALE`; patch 0005 | App permits `1.6`, runner clamps to `1.4`. Align UI or document clamp. |
| `touchAlpha` | Honoured within clamps | `SOTC_TOUCH_ALPHA`; patch 0005 | App permits `0.15`, runner clamps to `0.2`. |
| `touchDeadzone` | Honoured | `SOTC_TOUCH_DEADZONE`; patch 0005 | App range `0..0.3` fits runner `0..0.6`. |
| `cameraSensitivity` | Honoured | `SOTC_LOOK_SENSITIVITY`; patch 0005 | App range `0.4..2` fits runner `0.2..3`. |
| `invertLookX` | Honoured | `SOTC_INVERT_LOOK_X`; patch 0005 | Right stick horizontal inversion. |
| `padDeadzone` | Not honoured | host `GamepadManager` only | Patch 0005 does not publish a gamepad deadzone to the runner. |
| `invertLookY` | Not honoured | host `GamepadManager` / `TouchControllerView` only | No runner env reader. |
| `vibrationEnabled` | Not honoured | host `GamepadManager` only | Raylib runner path has no published rumble setting. |
| `adaptiveResolution` | Needs patch 0009 | new `SOTC_ADAPTIVE_RESOLUTION` | Runner will mutate `tuning().renderScale`; host engine must stay idle. |
| `frameLimitFps` | Needs patch 0009 | new `SOTC_FRAME_LIMIT` | `0`/`30`/`60` already sanitized by `Settings.sanitized()` (`SettingsStore.kt:38-42`). |
| `forceEs2` | Effectively honoured | raylib Android EGL context | `EGL_CONTEXT_CLIENT_VERSION, 2` is fixed at `rcore_android.c:855-858`; no runner action needed. |

### Preset mapping

`GraphicsPreset.kt:17-19` defines:

- Battery: scale `50`, adaptive on, limit `30`
- Balanced: scale `75`, adaptive on, limit `60`
- Performance: scale `100`, adaptive off, limit `0`

After patch 0009 these map cleanly to `SOTC_RENDER_SCALE=0.500/0.750/1.000`, `SOTC_ADAPTIVE_RESOLUTION=1/1/0`, and `SOTC_FRAME_LIMIT=30/60/0`. Before that patch, only the scale part reaches the runner.

## 7. Frame-time instrumentation

### 7.1 Already available upstream

- `native_renderer::PerfStats` (`native_tuning.h:768-778`) already carries `fps`, `frameMs`, `gameMs`, `hooksMs`, `renderMs`, `waitMs`, `presentMs`, `worstMs`, `draws`, and `history[120]`.
- `native_renderer::perfPublish()` / `perfSnapshot()` (`native_renderer.cpp:1134-1147`) publish a mutex-protected snapshot.
- `hostmenu::drawPerfOverlay()` (`host_menu.cpp:742-818`) already renders FPS, worst frame, game/hooks/wait, render/present, draws, and the 120-frame graph, enabled by `SOTC_PERF_OVERLAY`.
- `ps2_stats.h` already exposes `vblanks` (line 29), `nowUs()` (79), frame timeline counters (85), `presentedFrames` and `frameSequence` (125-126), and `frameHolds[4]` (128).
- `PS2Runtime::run()` already logs a 5-second `[load]`/`[stats]` block when `PS2X_STATS` is set (`ps2_runtime.cpp:3356`, `3389-3432`) with `gameFps`, `shownFps`, and frame timeline averages.
- `GameNativeActivity.kt:42-51` redirects stdout/stderr to `<files>/logs/game-<yyyyMMdd-HHmmss>.log`; `GameLogs.kt:27-63` lists and tails those files. No new transport is needed.

### 7.2 Recommended minimal instrumentation

Add a `[pace]` line in the existing `PS2X_STATS` block rather than creating a separate profiler:

- wall interval: selected `SOTC_FRAME_LIMIT`, actual value passed to `SetTargetFPS`, smoothed host frame time from `PerfStats.frameMs`;
- resolution: configured scale, current `tuning().renderScale`, adaptive on/off, last adaptation direction;
- presentation: `shownFps` and `frameHolds[0..3]` from `ps2_stats`;
- simulation: `gameFps` from `ps2_stats::vif1Chains`, already in the same block.

This gives a single 5-second record per session that can be asserted in a host smoke test and inspected from the app's Advanced log screen.

### 7.3 Measurement cautions

- Do not treat `SetTargetFPS(30)` as evidence that render work fell to 33 ms; raylib's wait is in `EndDrawing` (`rcore.c:947-955`). Use the published `PerfStats.frameMs` consistently and verify its meaning with a host run before changing thresholds.
- Do not compare `shownFps` with `gameFps` as if they were the same thing: `UploadFrame()` deliberately holds frames according to guest VSync (`ps2_runtime.cpp:458-497`). A 1:2 hold pattern is correct behaviour, not a dropped frame.
- Do not enable `SOTC_SCALE_CYCLE` and adaptive resolution in the same run; both write `tuning().renderScale`.
- Do not use host `Frame stats:` from `engine.cpp:320-343` as gameplay evidence. That line belongs to the diagnostics engine and will be absent from the runner path.

## 8. No-phone regression plan

### 8.1 Kotlin unit tests (this repository)

Extend the existing JUnit suite:

- `RuntimeEnvironmentTest.kt`: assert new constants, values for all three graphics presets, `0/30/60` sanitization, `1`/`0` flags, dot-decimal scale formatting, and failure reporting for the new names.
- `GraphicsPresetTest.kt`: assert Battery/Balanced/Performance still set scale, adaptive, and limit exactly as declared.
- `SettingsTest.kt`: keep range checks for frame limits and scale clamps; add an explicit expectation that app scale can be `25` while runner clamps to `0.5`.
- `GameLogsTest.kt`: add a parser assertion for the new `[pace] limit=... scale=...` line if a UI summary is added later.

Command target: the normal Gradle unit-test task (`./gradlew test`). No device or runner library is needed.

### 8.2 Patch contract checks (WSL, no device)

Create a scripted check that:

1. verifies celosia `HEAD` is `f8c269e`, as `patches/apply-patches.sh:17-25` already does;
2. applies `tools/capture-host/patches/` and `tools/android-integration/patches/` to a clean worktree;
3. asserts every patch marker from `patches/apply-patches.sh:32-50`, including new patch 0009;
4. verifies both `git apply --check <patch>` and `git apply -R --check <patch>` for every patch;
5. greps the patched tree for exact contract strings:
   - `SOTC_BOOT_ELF`, all eight `SOTC_TOUCH_*`/`SOTC_LOOK_*`/`SOTC_AUDIO_VOLUME` names;
   - `SOTC_FRAME_LIMIT`, `SOTC_ADAPTIVE_RESOLUTION`;
   - `SetTargetFPS(wantFps)` preceded by the app-limit override;
   - absence of `std::_Exit` in the Android branch of `main.cpp`;
   - no `Engine::start` / `Engine::configure` call in `GameNativeActivity.kt`;
6. records the patch files' SHA-256 values with the build evidence.

### 8.3 Host x86 smoke run (WSL, no device)

Build the patched celosia host runner and run short sessions with fixed environments:

- `SOTC_FRAME_LIMIT=30 SOTC_ADAPTIVE_RESOLUTION=0 SOTC_RENDER_SCALE=0.500 PS2X_STATS=1 SOTC_PERF_OVERLAY=0`
- `SOTC_FRAME_LIMIT=60 SOTC_ADAPTIVE_RESOLUTION=1 SOTC_RENDER_SCALE=1.000 PS2X_STATS=1`
- `SOTC_FRAME_LIMIT=0 SOTC_ADAPTIVE_RESOLUTION=0 SOTC_RENDER_SCALE=1.000 PS2X_STATS=1`

Assert from captured stdout:

- raylib logs `TIMER: Target time per frame: 33.333 milliseconds` for `30`, and `16.667` for `60`;
- `[pace] limit=` matches the published value;
- `[scale-adaptive]` appears only when adaptive resolution is enabled;
- no host `Frame stats:` line appears in the gameplay path;
- no process abort on quit (patch 0003 path).

This verifies parsing, pacing, logging, and adaptive decisions without rendering evidence on a phone.

### 8.4 Artifact verification

Extend `tools/android-integration/verify-runner.sh` without weakening current checks:

- keep ELF64, AArch64, `DYN`, 16 KB LOAD alignment, `ANativeActivity_onCreate`, touch overlay symbols, no `libc++_shared.so`, and `libandroid.so` checks (`verify-runner.sh:39-105`);
- add the new patch marker check if patch 0009 introduces an exported symbol; do not add a brittle `strings` assertion for inline constants;
- record size, SHA-256, and BuildID beside the patch hashes;
- compare the packaged artifact to the known baseline before accepting a rebuild.

### 8.5 Explicitly device-only

The following cannot be signed off without ARM64 hardware:

- first boot, window initialization, and actual GL output;
- touch overlay geometry, multitouch ownership, and pad merge from patch 0005;
- audio volume result on device;
- thermal behaviour and real sustained frame times;
- 16 KB page-size device loading.

These must remain marked unverified in release notes.

## 9. Rebuild, revert, and reproducibility risks

### 9.1 Apply and revert discipline

- `tools/android-integration/patches/apply-patches.sh` pins celosia to `f8c269e` and refuses drift. Keep that check.
- Both patch series are required: `tools/capture-host/patches/` first, then `tools/android-integration/patches/`. Do not apply while a capture-host build is running.
- Revert in reverse numeric order with `git apply -R --check <patch>` followed by `git apply -R <patch>`, starting with 0009. The per-patch markers are the idempotence source because the celosia tree also carries unrelated uncommitted developer fixes.
- Never touch the developer's uncommitted `coll125.h` / `decomp_collision.cpp` capture-host fixes.
- If the celosia commit changes, rebase the patches and regenerate; do not force or reset the checkout.

### 9.2 Build cost and resilience

From `tools/android-integration/README.md:80-98`:

- 9,333 objects; thin-LTO link about 55 minutes on WSL with 1.7 GiB RAM plus an 8 GB swapfile;
- use `-j2`, `setsid`, and ninja resume; re-enable `/root/swapfile` after `wsl --shutdown`;
- two prior builds were interrupted (`WSAETIMEDOUT`, SIGTERM during link) and ninja resumed correctly. Do not delete the build tree when an interruption occurs.

### 9.3 Baseline artifact to preserve

| Property | Value |
|---|---|
| Path | `app/src/main/jniLibs/arm64-v8a/libps2EntryRunner.so` |
| Size | `107,384,168` bytes |
| SHA-256 | `134F2FBF3B0A5F260FFF8A01B5F36221AAEEC377242B602B7F6952B18B9AEB8C` |
| Unstripped build artifact | `777,574,008` bytes, BuildID `8c5f7557f979db6ad9bcc3b64785c64f6d83413c` |
| Verification | `RUNNER_VERIFY_OK` from `tools/android-integration/verify-runner.sh` |

Keep the unstripped library for symbolisation. `package-runner.sh` strips only the copy in `jniLibs` with `llvm-strip --strip-unneeded` (`package-runner.sh:54-80`).

### 9.4 Rebuild acceptance gate

A new runner is accepted only if all of the following hold:

1. all patches apply and reverse-check cleanly;
2. host x86 smoke assertions pass;
3. `verify-runner.sh` prints `RUNNER_VERIFY_OK` for the new file and records the new SHA-256;
4. the APK builds with the new library;
5. first-boot device evidence is obtained before any release claim.

Until then, package or keep the baseline file unchanged. A failed rebuild must not overwrite the packaged baseline.

### 9.5 Reproducibility checklist

Record with every build:

- celosia commit `f8c269e` and both patch series with hashes;
- NDK r26b (`/root/android-ndk-r26b`; Windows SDK reports `26.1.10909125`, scaffold pins `28.2`);
- FFmpeg off on Android;
- `sse2neon` v1.9.1 with `USE_SSE2NEON`; no x86 SIMDe shim on the ARM64 include path;
- `-Wl,-z,max-page-size=16384` and `c++_static`;
- object count, link time, build ID, stripped size, SHA-256, and verifier output.

## 10. Scope split

### Can be prepared in this repository now

- This report.
- Kotlin env constants and tests for `SOTC_FRAME_LIMIT`, `SOTC_ADAPTIVE_RESOLUTION`, and the scale/statistics names.
- Patch contract and verifier extensions.
- Documentation of honest ranges and the next-session-only behaviour.

None of these changes runtime behaviour until the runner is rebuilt.

### Requires a WSL celosia rebuild

- Proposed patch 0009 in `ps2_runtime.cpp`.
- Any change to upstream env parsing, frame pacing, adaptive scale, or instrumentation.
- A new AArch64 `.so`, a new SHA-256, repackaging, and a new `RUNNER_VERIFY_OK`.

### Requires a real ARM64 device

- All first-boot and gameplay claims listed in section 8.5.

## 11. Final recommendation

Proceed with the single-patch design in section 4.2: publish the three graphics settings through the existing environment channel, let raylib remain the frame-pacing owner, let the runner mutate its own `renderScale`, and put the 5-second `[pace]` evidence into the existing `PS2X_STATS` log. Do not add JNI, do not re-enable the host `Engine` in gameplay, and do not use `SOTC_CAP20`. Preserve the current packaged runner until a patched rebuild passes the full no-phone regression gate.
