# Performance

This document tracks performance goals, measurement guidance, and current known performance evidence for Shadow of the Colossus Mobile. **No gameplay performance claims are made until measured on a real, playable build.**

## 1. Current status

- **Playable game runtime on Android:** Not yet available/verified.
- **ARM64 device performance profiling:** Not performed.
- **Thermal/battery impact during gameplay:** Not measured.

Performance work is planned after a runnable native runtime/core is integrated and verified (see [ROADMAP.md](ROADMAP.md)).

### Implemented controls in the host engine (2026-11-19)

These are real, compiled settings. They act on the host engine view (`libsotc_core.so`), which draws the diagnostic scene or an ABI-v1 core. The raylib-owned game runtime has its own frame loop and presentation, so none of them reach it yet.

| Control | Behaviour | Runtime evidence |
| --- | --- | --- |
| Internal resolution (25-100%) | Renders into an offscreen target at the chosen scale and blits to the window. | Verified on the API 28 emulator |
| Linear / nearest filtering | Chooses the blit filter. | Verified in the settings UI and status JSON |
| Adaptive resolution | Adjusts the effective scale in 5% steps every 0.75 s using smoothed frame time against the frame budget, clamped to a 25% floor. Saved settings are never rewritten. | Verified: `26% -> 25%` at 50.6 ms vs 16.7 ms budget |
| Frame limit (uncapped / 60 / 30) | Paces presentation with `sleep_until`; the simulation delta stays wall-clock based, so game logic is neither sped up nor slowed down. | **Not verified at runtime**: SwiftShader cannot reach 30 fps, so the cap never binds |
| Thermal ceiling | Maps Android thermal status to a scale ceiling (100/100/85/70/60/50%) without touching saved settings. | **Not verified at runtime**: the emulator always reports status 0 |
| Low-memory mode | On `onTrimMemory`/`onLowMemory` the engine releases the offscreen target and renders directly to the window for the rest of the session. | Verified with `am send-trim-memory ... RUNNING_LOW` |
| Graphics presets | Battery saver (50%, adaptive, 30 fps), Balanced (75%, adaptive, 60 fps), Performance (100%, no adaptive, uncapped). | Persistence verified in `shared_prefs/sotc_settings.xml` |

`statusJson()` and the debug `Frame stats:` log line report fps, frame time, surface size, effective scale, backend, configured scale, frame limit, thermal status and audio callback count, so the behaviour can be measured from `adb` without reading the on-screen overlay.

## 2. Existing host-side measurements (diagnostics/emulator only)

Historical host verification (2026-11-09) on an **Android 9 (API 28) x86_64 emulator with SwiftShader** (software GPU) recorded diagnostic renderer frame-rate behavior at 1280x720:

| Internal resolution scale | Approx. FPS | Approx. frame time |
| --- | --- | --- |
| 100% | ~1.8–2.7 | ~360–570 ms |
| 26% | ~10–13 | ~67–160 ms |

Re-measured on **2026-11-19** on the same emulator class through the OpenGL ES 2 fallback (1280x720 surface, diagnostic scene, 25-26% internal scale): ~13-15 fps at ~62-75 ms per frame. After the low-memory path released the offscreen target and rendered directly to the window, the same scene dropped to ~2-3 fps at ~350-400 ms per frame, because the procedural shader then runs at the full surface size. That is the intended trade: memory is released at the cost of GPU time. The offscreen path is the normal one.

**Interpretation and caveats:**

- These numbers are from the **diagnostic renderer/host path**, not original game rendering/gameplay.
- SwiftShader and x86_64 emulation make results **unrepresentative** of ARM64 mobile devices.
- Use only for relative host-integration checks, never as device or gameplay benchmarks.

## 3. Performance goals (future, evidence-based)

Once a playable build exists, optimization targets should be defined and validated per device class using measurements. Potential focus areas include:

- Stable frame pacing during gameplay/cutscenes
- Acceptable input latency
- Audio stability (no persistent underruns/glitches)
- Controlled memory usage across gameplay sessions
- Sustainable thermals (avoiding rapid throttling that breaks playability)

Targets must be documented with device model, Android version, settings, scene/context, and measurement method.

## 4. Measurement approach (when applicable)

Before and after optimization changes:

- Profile CPU, GPU, memory, frame time, and thermal behavior on **ARM64 hardware**.
- Separate host overhead from runtime/core costs where possible.
- Record reproducible test scenarios (boot, traversal, combat/colossus encounters, cutscenes) once gameplay is reachable.
- Capture logs and, where available, profiling traces/screenshots of metrics.
- Document regressions and trade-offs honestly.

## 5. Mobile constraints to consider

- ARM64 CPU/GPU performance varies widely across devices.
- Thermal throttling can reduce sustained performance over time.
- Memory bandwidth and allocation patterns matter for large game workloads.
- Static recompilation removes interpreter overhead but does **not** guarantee that PS2 workloads will meet mobile performance/thermal budgets without optimization.

## 6. Known issues / unknowns

- No real-game frame rate, load time, or stability data yet.
- Audio latency/underrun behavior with real game audio is unknown.
- Resolution scaling effectiveness for the actual game renderer is unknown until integrated.
- Battery drain and heat characteristics are unknown.
- The frame limiter and the thermal ceiling are compiled and unit-tested at the settings level, but neither has been observed doing its job on real hardware; the available emulator cannot reach 30 fps and always reports thermal status 0.
- Adaptive resolution has only been observed stepping down. Step-up behaviour (frame time below 75% of budget) is implemented but unverified.
- Low-memory mode is a one-way switch for the session: once the offscreen target is released, the session keeps rendering directly until it ends.

Updates to this document must be based on verified measurements, not estimates. For current compatibility status, see [COMPATIBILITY.md](COMPATIBILITY.md). For planned work, see [ROADMAP.md](ROADMAP.md).