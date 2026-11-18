# Third-Party Integrations

> Status: **Evaluation recorded 2026-11-18 (recovery session). No new third-party libraries integrated in this session.**
>
> This document tracks every external project/library considered for SotC Mobile: purpose, license, compatibility assessment, integration decision, pinned version/commit (when adopted), and where it is used. Update this file whenever an integration is added, removed, or its pinned revision changes.

## Integration policy (project rules)

- Inspect source, license, current version, build requirements, Android/ARM64 compatibility, and overlap with existing code before integrating.
- Pin exact versions or commits for adopted dependencies; preserve all license/copyright notices.
- Do not add duplicate input systems (touch or physical controller). Keep one normalized input abstraction feeding the game runtime.
- Do not include Ghidra in the final APK (development-time analysis tool only).
- Do not add SDL3 as a full framework unless a specific subsystem is demonstrated necessary; the existing Android native runtime + EGL/GLES + AAudio foundation already works.
- No system-wide/root input injectors or privileged virtual controllers.
- Preferred stack (per master plan): **Celosia** (game-specific recompilation/runtime), **PS2Recomp** (MIPS→C++ static recompilation), **RetroTouch** (candidate virtual touch overlay), **Paddleboat / `androidx.games:games-controller`** (physical gamepad input), **GameActivity / AGDK samples** (reference/integration where beneficial), **Oboe** (audio compatibility/low-latency layer where useful), **Ghidra** (dev-time analysis), **SDL3** (optional reference only).

## Summary

| Component | Upstream | License | Android / ARM64 | Status | Decision / Notes |
|---|---|---|---|---|---|
| Celosia (pipeline + overrides + vendored runtime fork) | https://github.com/HenryKun55/celosia | GPL-3.0 | Source used to generate/build runtime; not a runtime Android AAR dependency | **Adopted (pinned checkout)** | Pinned at `f8c269e` in `third_party/celosia` (gitignored local checkout). Primary source of SotC-specific recompilation, overrides, PS2 runtime behavior, and the D18 Android integration path (raylib-owned NativeActivity runner). |
| PS2Recomp (framework + Android scaffold reference) | https://github.com/ran-j/PS2Recomp | GPL-3.0 | Android scaffold present upstream; ARM64 targetable via NDK toolchain | **Adopted (pinned checkout, reference/scaffold)** | Pinned checkout present at `third_party/PS2Recomp` (gitignored). Used as framework/scaffold reference; Celosia embeds its own game-tuned PS2Recomp fork for actual core generation (D4). |
| RetroTouch | https://github.com/Andiweli/RetroTouch | MIT (project); see upstream third-party notices (e.g. Lucide icons ISC) | Android 4.1/API 16–36+, Java 8, AAR delivery, no AndroidX runtime dependency stated | **Evaluated — candidate, not yet integrated** | Strong fit for reusable multitouch overlay/editor (analog sticks, look zones, D-pad, remap/resize/opacity, persistence, controller detection helpers). However, current app already has a working custom `SotcInput` stack (`PadState`, `TouchControllerView`, `TouchLayout`/editor/store, `GamepadManager`) with tap-latching, overlap resolution, PS2 bitmask semantics, and D18-aware integration needs. **Recommendation:** do not add now; preserve existing SotcInput for M5/M6. Re-evaluate as **Strategy C (hybrid)** only if a prototype proves clear reliability/maintenance wins without duplicating input ownership. Any adoption must keep stable persisted layout IDs or provide migration. |
| Game Controller library (Paddleboat) | https://developer.android.com/games/sdk/game-controller (`androidx.games:games-controller`) | Apache-2.0 (AndroidX/AGDK components; verify per artifact) | Native C/C++ oriented; official docs describe API 19+ / NDK 21+ compatibility (verify current artifact requirements) | **Evaluated — candidate, not yet integrated** | Provides standardized discovery/connect/disconnect, mapping metadata, sticks/triggers, vibration support (where available), and native integration patterns. Current app uses Android framework input (`InputDevice`/`KeyEvent`/`MotionEvent`) in `GamepadManager` + `ControllerMapping` with deadzone and merge semantics that already work in host diagnostics. **Recommendation:** prototype Paddleboat integration when wiring physical controllers into the D18 raylib-owned game runtime (Milestone 5+), preferring a single normalized `GameInputState` adapter; keep existing framework path as fallback if Paddleboat integration cost outweighs benefit for initial boot/gameplay. Pin a verified stable `androidx.games:games-controller` version at integration time (do not guess). |
| Android Game Development Kit samples (AGDK) | https://github.com/android/games-samples (esp. `agdk/game_controller`, `agdk/agdktunnel`) | Apache-2.0 (repository; verify individual sample notices) | Android game samples (C++/GameActivity/input/audio/frame pacing references) | **Reference only — not bundled** | Valuable implementation references for GameActivity lifecycle, Paddleboat usage, Oboe, frame pacing, and native integration patterns. Use for study/prototyping guidance; do not copy wholesale into the app. |
| Android GameActivity | https://developer.android.com/games/agdk/game-activity | Apache-2.0 (Jetpack `androidx.games:games-activity`; verify) | Designed for native C/C++ Android games; version/NDK-native-glue compatibility must be respected | **Evaluated — constrained by D18** | Project's D18 decision adopts the **upstream raylib-owned NativeActivity** path for gameplay integration (Celosia/PS2X runtime is raylib-coupled). Introducing `GameActivity` as a replacement would require re-hosting/forking the runtime platform layer — high risk, not justified now. **Recommendation:** do not replace current stable activity/lifecycle or D18 NativeActivity plan; revisit only with evidence of a specific, measurable benefit compatible with D18. |
| Oboe | https://github.com/google/oboe | Apache-2.0 | Android API 16+ library; selects AAudio/OpenSL ES as appropriate | **Evaluated — candidate adaptation layer, not yet integrated** | Useful C++ abstraction for low-latency audio and device-change handling. Current host app already initializes/runs audio via AAudio/OpenSL path (reported/implemented in engine) and the Celosia/PS2X runtime owns its own audio in the D18 game path. **Recommendation:** do not replace working host audio now; evaluate Oboe only if M5/M6 integration reveals latency, callback, or device-routing issues that Oboe demonstrably improves. Pin a verified release (web results observed recent releases such as 1.10.x/1.11.x — **verify exact latest stable tag/commit before adoption**). |
| SDL3 | https://github.com/libsdl-org/SDL | zlib | Cross-platform including Android; has gamepad/touch/input APIs | **Evaluated — not adopted** | Overlaps heavily with existing responsibilities (window/context lifecycle, input, controller handling, possibly audio) in both the Android host app and the raylib-coupled PS2X runtime. Adding SDL3 risks duplicate frameworks and integration churn. **Recommendation:** keep as **optional reference only**; do not add unless a narrowly scoped subsystem need is proven and cannot be met by existing stack/RetroTouch/Paddleboat. |
| Ghidra | https://github.com/NationalSecurityAgency/ghidra | Apache-2.0 | Desktop analysis tool (not an Android runtime dependency) | **Dev tool only — not bundled** | Useful for manual reverse-engineering/function recovery when needed. Celosia's heuristic mapping (`funcmap_elf.py`) is the primary path (D8). **Never include Ghidra in the APK or runtime packaging.** |

## Detailed evaluations

### 1) RetroTouch (touch overlay candidate)

- **Upstream:** https://github.com/Andiweli/RetroTouch (README fetched 2026-11-18 from `main`)
- **Observed release/status:** `1.0.0-beta.12` (AAR + source zip + integration guides in repo `source/`)
- **License:** MIT (README badge/text); includes third-party notices (e.g. Lucide icons ISC per README) — verify full notices before bundling any binary/source.
- **Capabilities relevant to SotC Mobile:** multitouch pointer handling, configurable buttons/sticks/look zones/D-pads, normalized layouts, layout editor (move/resize/opacity/remap/reset/save), persistence, controller detection helper, latching visuals, input reset API, safe-inset support, optional second/multiple sticks (up to 8 with `setMaxSticks`), icon/label styles.
- **Current project overlap:** `app/src/main/java/com/portsotc/mobile/input/` already implements a custom stack: `PadState` (thread-safe merge, tap latching between polls, separate touch/pad sources), `TouchControllerView` (multitouch, stick/D-pad/buttons, editor drag/select, overlap-nearest hit resolution), `TouchLayout`/`TouchLayoutStore` (normalized persisted layout with stable IDs), `GamepadManager`/`ControllerMapping`/`Analog`, and activity wiring (`GameActivity`, `TouchLayoutEditorActivity`, `ControllerSettingsActivity`).
- **Integration constraints:** D18 routes gameplay through a raylib-owned NativeActivity runner process; touch/gamepad input must ultimately reach the PS2X runtime's expected pad path. That bridge now exists in C++ (patch `0005-android-touch-input.patch`): `ps2_android_input` samples raylib's touches, `PSPadBackend::readState` merges them into the PS2 wire format, and the overlay is drawn in the runner's own GL frame. Any further overlay work (e.g. a third-party overlay library) would have to reproduce that native path - Android views cannot be layered over a `NativeActivity` window - so RetroTouch is no longer a candidate for the gameplay window.
- **Decision:** **Not integrated now.** Preserve and evolve existing SotcInput; treat RetroTouch as a **prototype candidate** (Strategy C) only with measured benefit and a clean bridge into the normalized game input contract used by the runtime.

### 2) Paddleboat / `androidx.games:games-controller`

- **Upstream docs:** https://developer.android.com/games/sdk/game-controller
- **Nature:** Native library/API (Paddleboat) for controller discovery, standardized input, mapping metadata/custom mappings, hot-plug callbacks, and optional features (e.g. vibration/mouse) — verify exact supported feature set per pinned release.
- **Compatibility (to verify at pin time):** official materials historically reference Android API 19+ and NDK requirements; confirm current stable artifact's min API/NDK/AGP/CMake constraints before adding.
- **Current project overlap:** `GamepadManager` already handles `KeyEvent`/`MotionEvent` joystick/gamepad sources, radial deadzone, triggers via axes, D-pad hat, and mapping table; `PadState` merges with touch. This is sufficient for host diagnostics and likely adequate short-term.
- **Decision:** **Not integrated now.** Adopt when implementing/validating physical controller input against the actual D18 game runtime if Paddleboat provides concrete benefits (standardized mapping, hot-plug robustness, native-side integration). If adopted, integrate through a single normalized input adapter (no parallel controller stacks) and pin a verified stable `androidx.games:games-controller` Maven artifact version/commit provenance.

### 3) AGDK samples / GameActivity

- **Upstream:** https://github.com/android/games-samples (`agdk/game_controller`, `agdk/agdktunnel`), https://developer.android.com/games/agdk/game-activity
- **Value:** Reference implementations for native lifecycle/input/audio/frame pacing patterns.
- **Decision:** **Use as references only.** Do not transplant wholesale. Do not replace the current stable host lifecycle or the D18 raylib-owned NativeActivity integration without demonstrated need and compatibility evidence.

### 4) Oboe

- **Upstream:** https://github.com/google/oboe, https://developer.android.com/games/sdk/oboe/low-latency-audio
- **License:** Apache-2.0.
- **Observed releases (web, 2026-11-18, not pinned):** search results reference 1.10.0 and 1.11.0; **verify the exact latest stable release/tag before adoption**.
- **Decision:** **Not integrated now.** Existing host audio path is functional; PS2X runtime manages game audio in D18 path. Consider Oboe as an adaptation layer only with measured benefit (latency/device-change robustness/callback behavior) during M5/M6.

### 5) SDL3

- **Upstream:** https://github.com/libsdl-org/SDL (Android docs, gamepad API)
- **License:** zlib.
- **Decision:** **Not adopted.** High overlap/risk of duplicate subsystems relative to existing EGL/GLES/AAudio host and raylib-coupled runtime. Retain only as optional reference material.

### 6) Ghidra

- **Upstream:** https://github.com/NationalSecurityAgency/ghidra
- **License:** Apache-2.0.
- **Decision:** **Development tool only.** Not part of app build/runtime/APK.

## Current in-repo third-party/vendored components (pinned locally)

| Component | Location | Pinned ref (local) | License | Usage |
|---|---|---|---|---|
| Celosia checkout | `third_party/celosia` (gitignored) | `f8c269e` (per agent memory) | GPL-3.0 | Pipeline driver/tools, SotC overrides, vendored PS2Recomp fork, runtime sources used for recompilation and Android runner build prep |
| PS2Recomp checkout | `third_party/PS2Recomp` (gitignored) | `e563698` (per agent memory) | GPL-3.0 | Upstream framework/scaffold reference (incl. Android scaffold) |
| Android Gradle Plugin | `build.gradle` | 8.5.2 | Apache-2.0 | App build |
| Kotlin Gradle plugin | `build.gradle` | 1.9.20 | Apache-2.0 | App build |
| Android SDK/NDK (local) | `local.properties`, `app/build.gradle` | compileSdk 34, minSdk 28, targetSdk 34, NDK 26.1.10909125, CMake 3.22.1 | Proprietary SDK terms / NDK licenses | Android build toolchain |
| JUnit | `app/build.gradle` | 4.13.2 | EPL-1.0 | Unit tests |

> Note: `third_party/celosia` and `third_party/PS2Recomp` are present as local gitignored checkouts; preserve their license files and do not redistribute copyrighted game data. Any new dependency added to Gradle/CMake must be recorded here with its exact version and license notice handling.

## Open integration actions (next)

1. **M4/M5 (D18):** when the WSL pipeline completes, run `tools/capture-host/configure-android.sh` + `build-android-runner.sh` to produce the arm64-v8a runner library; package and wire via `GameNativeActivity` + pre-launch disc extraction per `docs/agent/NEXT_STEPS.md` and D18.
2. **Input unification for gameplay runtime:** define/confirm a single normalized `GameInputState` bridge from existing touch/gamepad sources into the PS2X runtime input path (upstream touch mapping is a TODO); avoid duplicate stacks.
3. **Prototype-only evaluations (if needed):** RetroTouch (hybrid overlay) and/or Paddleboat (native controller path) behind adapters, only with evidence of benefit and no regression to existing input behavior.
4. **Audio/frame pacing:** reassess Oboe and AGDK frame-pacing guidance only against measured M6 runtime issues.
5. **License hygiene:** before any release including newly adopted third-party binaries/code, add/verify NOTICE/license packaging and update this document with pinned revisions and exact usage locations.
