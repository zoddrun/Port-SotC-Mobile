# Performance Architecture and Single-Owner Contract

> Agent memory file. Defines who owns gameplay performance, what currently exists, what is reference-only, and the measurement plan.
> Last updated: 2026-11-19

## 1. Single-owner rule

There is exactly one owner for gameplay simulation, rendering, presentation, frame pacing, and dynamic resolution:

> **The raylib `NativeActivity` runtime inside `libps2EntryRunner.so`.**

Concretely:

| Concern | Sole gameplay owner | Evidence |
|---|---|---|
| Simulation | PS2 EE/IOP/GS threads in `PS2Runtime` | `PS2Runtime::run()` starts near `ps2_runtime.cpp:2937` in celosia `f8c269e` |
| Rendering | Celosia native renderer plus raylib drawing | `drawHostFrame` lambda near `ps2_runtime.cpp:2964`; `BeginDrawing()` near 2966 |
| Presentation | `UploadFrame()` | `ps2_runtime.cpp:444-497`, guest VSync rate `59.94`, latency two VSyncs |
| Host pacing | raylib `SetTargetFPS` / `EndDrawing` | raylib 5.5 `rcore.c:1645-1651`, `rcore.c:939-956`; Android `eglSwapInterval` is commented out at `rcore_android.c:913-914` |
| Dynamic resolution | runner render scale | `native_tuning.h:803-807` clamps to `0.5..2`; `native_renderer.cpp:2858-2873` mutates `tuning().renderScale` live |

The host `Engine` (`app/src/main/cpp/engine/engine.cpp`) contains useful reference behaviour but is diagnostics/ABI-v1 only. `GameNativeActivity` must never call `Engine::start`, `Engine::configure`, `GameCore::render`, or any host step/pull path. Adding a second loop would create competing presentation and resolution owners and is explicitly forbidden.

## 2. What exists today

### 2.1 Host engine reference behaviour

Implementations in `engine.cpp` (all **COMPILED**, **EMULATOR-VERIFIED** only for adaptive step-down and low-memory fallback on API 28 x86_64 SwiftShader):

- minimum render scale `0.25`;
- frame-limit values restricted to `0`, `30`, `60`;
- thermal cap mapping near `engine.cpp:25-39`;
- effective scale calculation near `engine.cpp:218-228`;
- render-target versus direct-render fallback near `engine.cpp:233-261`;
- frame limiter using `sleep_until` near `engine.cpp:274-286`;
- adaptive resolution near `engine.cpp:288-304`: 5% steps, 0.75 s cadence, 1.20x/0.75x thresholds;
- 5-second `Frame stats:` log near `engine.cpp:320-343`.

None of this reaches gameplay. The `Frame stats:` line will not appear in a runner session and must never be used as gameplay evidence.

### 2.2 Runner behaviour before this expansion

- `SetTargetFPS(60)` at `ps2_runtime.cpp:1161`.
- `wantFps` expression near `ps2_runtime.cpp:3190-3195` chooses 20 (Steady 20), 30 (frameCap30 on slow scenes), or 60. These are upstream Vita/Mac presentation rules, not Android settings.
- `PS2X_STATS=1` enables a 5-second `[stats]`/`[load]` block near `ps2_runtime.cpp:3354-3432` with `gameFps`, `shownFps`, thread load, GS/IOP/VU1 timings, and frame holds.
- `native_renderer::PerfStats` (`native_tuning.h:768-778`) carries `fps`, `frameMs`, `gameMs`, `hooksMs`, `renderMs`, `waitMs`, `presentMs`, `worstMs`, `draws`, and `history[120]`, published via `perfPublish()`/`perfSnapshot()`.
- `hostmenu::drawPerfOverlay()` (`host_menu.cpp:742-818`) draws FPS, worst frame, thread split, draws, and a 120-frame graph when `SOTC_PERF_OVERLAY=1`.
- `ps2_stats.h` exposes `vblanks`, `presentedFrames`, `frameSequence`, and `frameHolds[4]`.

### 2.3 Verified raylib pacing semantics

raylib 5.5 `SetTargetFPS(int fps)`:

```c
if (fps < 1) CORE.Time.target = 0.0;
else CORE.Time.target = 1.0/(double)fps;
```

- `30` logs `33.333 milliseconds`;
- `60` logs `16.667 milliseconds`;
- `0` or negative is uncapped.

`SetTargetFPS` changes the wait in `EndDrawing`; it does **not** change the PS2 guest time step. `SOTC_CAP20` / Steady 20 does change guest release cadence and is rejected as an app setting.

## 3. Target interface

Settings are published as process environment values before `super.onCreate` loads the runner (`GameNativeActivity.kt:66-80`). No JNI settings call and no argv are used: raylib supplies only `argv[0] = "raylib"` on Android.

New runner knobs in the proposed patch 0009:

| Environment name | Values | Purpose |
|---|---|---|
| `SOTC_FRAME_LIMIT` | `0`, `30`, `60` | Presentation cap only; overrides `wantFps` before `SetTargetFPS` |
| `SOTC_ADAPTIVE_RESOLUTION` | `1`, `0` | Enables runner-owned scale adaptation |
| `SOTC_RENDER_SCALE` | decimal `0.500`..`1.000` | Ceiling for adaptation; already read upstream at `native_renderer.cpp:102` |

Existing upstream knobs reused by the app:

| Environment name | Values | Purpose |
|---|---|---|
| `SOTC_PERF_OVERLAY` | `1`, `0` | Existing on-screen performance overlay |
| `PS2X_STATS` | present / absent | Existing 5-second diagnostic log gate |

Host sanitization already restricts `frameLimitFps` to `0`/`30`/`60` (`SettingsStore.kt:38-42`).

## 4. Dynamic resolution contract

Mirror the host engine's proven behaviour in the runner, translated to upstream scale units:

- ceiling = published `SOTC_RENDER_SCALE`, captured at session start;
- floor = `max(0.5, ceiling * 0.5)` because celosia clamps render scale to `0.5..2` on this platform;
- step = `0.05` absolute scale per change;
- cadence = at least `0.75 s` between changes;
- reduce when smoothed frame time exceeds `1.20 * budget`, raise when below `0.75 * budget`;
- `budget = 1000 / (frameLimit > 0 ? frameLimit : 60)` milliseconds;
- reset the smoother after a scale change to avoid oscillation;
- skip adaptation when `SOTC_SCALE_CYCLE` is active, because that debug mode also writes `renderScale`.

The app's `resolutionScalePercent` allows `25..100`, but the runner clamps `0.25` up to `0.50`. The UI must state that effective minimum. No 25% gameplay claim is permitted.

Render-target recreation failure handling: upstream `tuning().renderScale` is read by the renderer; if a scale change produces an incomplete render target, revert to the previous scale and log the failure. Do not resize the host EGL surface and call that dynamic resolution.

## 5. Frame pacing and double limiting

- Only raylib's `EndDrawing` wait may pace the game. Do not add a host `sleep_until` to the gameplay path.
- Do not run host `Engine` pacing simultaneously with runner pacing.
- Do not treat `SetTargetFPS(30)` as proof that render work fell to 33 ms; measure `PerfStats.frameMs` and `shownFps` separately.
- Android Frame Pacing / Swappy is evaluated later, never layered on top of an already-correct single limiter without a measured need.
- `shownFps` and `gameFps` are different by design: `UploadFrame()` holds guest frames according to VSync. A 1:2 hold pattern is not a dropped frame.

## 6. Thermal and performance hints

- ADPF and Game Mode are evaluate-later. They require API guards on minSdk 28 and toolchain validation.
- The host engine polls thermal status from `GameActivity.kt:201-209`, but that activity is diagnostics-only.
- When thermal information is unavailable, keep the configured preset unchanged; never guess a thermal state.
- A future thermal publisher into the runner must be a separate, device-tested change.

## 7. Measurement plan

Record per 5-second window, from the runner:

| Metric | Source |
|---|---|
| Average FPS, median FPS, 1% lows | frame-time history, not just `GetFPS()` |
| Frame time P50/P90/P95/P99 | `PerfStats.history[120]` extended if needed |
| CPU / game / hooks time | `PerfStats.gameMs`, `hooksMs` |
| Render time | `PerfStats.renderMs` |
| Presentation wait | `PerfStats.waitMs`, `presentMs` |
| Memory consumption | Android `Debug.MemoryInfo` on the host side, reported separately |
| Thermal status | Android thermal API when available, otherwise `UNAVAILABLE` |
| Effective render scale and selected limit | new `[pace]` log |

The `[pace]` line belongs inside the existing `PS2X_STATS` gate so there is one 5-second record per session:

```
[pace] limit=30 targetFps=30 scale=0.750 adaptive=1 frameMs=... gameFps=... shownFps=... holds=...
```

Use P50/P90/P95/P99 and 1% lows for decisions. Average FPS alone is not an optimization result.

## 8. Quality presets

`GraphicsPreset.kt` defines:

| Preset | Scale | Adaptive | Frame limit |
|---|---|---|---|
| Battery Saver | `50` | on | `30` |
| Balanced | `75` | on | `60` |
| Performance | `100` | off | `0` (uncapped presentation) |

These map to `SOTC_RENDER_SCALE=0.500/0.750/1.000`, `SOTC_ADAPTIVE_RESOLUTION=1/1/0`, and `SOTC_FRAME_LIMIT=30/60/0` after patch 0009. Before the rebuild, only the scale value reaches the runner. The UI must say that new pacing settings apply to the next game session.

Additional upstream quality controls (particles, model detail, distant-character update frequency, draw distance, camera collision checks) were studied but are not exposed. A setting is added only when the real runner honours it and a regression test can prove it.

## 9. Acceptance gate for performance claims

1. Patch contract checks pass; patch 0009 applies and reverse-checks cleanly.
2. Host x86 smoke run shows raylib `33.333 ms` for limit 30 and `16.667 ms` for limit 60.
3. `[pace] limit=...` matches the published value for each scenario.
4. `[scale-adaptive]` appears only when adaptive resolution is enabled.
5. `verify-runner.sh` reports `RUNNER_VERIFY_OK` for any rebuilt artifact.
6. Gameplay frame metrics and thermal data are captured on an ARM64 device before any performance improvement claim.

Until step 6, all performance numbers are either host-engine reference measurements or simulated, and must be labelled as such.
