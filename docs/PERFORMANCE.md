# Performance

This document tracks performance goals, measurement guidance, and current known performance evidence for Shadow of the Colossus Mobile. **No gameplay performance claims are made until measured on a real, playable build.**

## 1. Current status

- **Playable game runtime on Android:** Not yet available/verified.
- **ARM64 device performance profiling:** Not performed.
- **Thermal/battery impact during gameplay:** Not measured.

Performance work is planned after a runnable native runtime/core is integrated and verified (see [ROADMAP.md](ROADMAP.md)).

## 2. Existing host-side measurements (diagnostics/emulator only)

Historical host verification (2026-11-09) on an **Android 9 (API 28) x86_64 emulator with SwiftShader** (software GPU) recorded diagnostic renderer frame-rate behavior at 1280x720:

| Internal resolution scale | Approx. FPS | Approx. frame time |
| --- | --- | --- |
| 100% | ~1.8–2.7 | ~360–570 ms |
| 26% | ~10–13 | ~67–160 ms |

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

Updates to this document must be based on verified measurements, not estimates. For current compatibility status, see [COMPATIBILITY.md](COMPATIBILITY.md). For planned work, see [ROADMAP.md](ROADMAP.md).