# Upstream Platform Research Report: Repositories C–H

Date: 2026-11-19  
Scope: Read-only research of upstream repositories C–H. No project source files were modified. No binaries, game assets, emulator backends, or generated game code were copied into this repository.

## 1. Executive Summary

Decision key:

- **ADOPT**: A concrete pattern is low-risk and should be used in our design, reimplemented independently in our own code.
- **EVALUATE LATER**: Potentially useful, but blocked by toolchain version, architecture, licensing, or a missing precondition.
- **REJECT**: Do not integrate; record the reason so the decision is not reopened without new evidence.

| Component | Repository | Decision | Reason |
|---|---|---|---|
| Static recompilation runtime / runner architecture | PS2Recomp | **EVALUATE LATER** | Useful boundary and stdout-to-logcat pattern, but GPL-3.0, raylib-owned `NativeActivity`, NDK/Gradle/AGP conflicts, and no Kotlin/UI/SAF layer. |
| Boot ELF bundled through Gradle property | PS2Recomp | **REJECT** | Conflicts with the rule against importing or bundling game code/assets and with our SAF importer. |
| Android game platform APIs (ADPF, Game Mode, Game Activity, Frame Pacing, gamepad) | games-samples | **EVALUATE LATER** | API patterns are useful, but the samples target SDK 37 / NDK 29 and GameActivity would replace our raylib-owned runner. |
| Wholesale AGDKTunnel / GameActivity / Frame Pacing integration | games-samples | **REJECT** | Would displace the existing `NativeActivity`/raylib loop and newer toolchain requirements. |
| Oboe as an AAudio replacement | Oboe | **EVALUATE LATER** | Stronger loader/quirks/recovery story than the current raw AAudio engine, but only viable as a full replacement. |
| Oboe added beside the current AAudio engine | Oboe / games-samples | **REJECT** | Would create two audio engines. |
| `sotc-vibe-pc` scenario and golden-run methodology | sotc-vibe-pc | **EVALUATE LATER** | Test-structure ideas are useful; region/version-specific hooks and golden data are not reusable as code. |
| `sotc-vibe-pc` code, hooks, addresses, and Windows harness | sotc-vibe-pc | **REJECT** | GPL-3.0 plus SCES-53326 v1.00-specific addresses and generated game-code context. |
| PCSX2 GS dump as an external test oracle | PCSX2 | **EVALUATE LATER** | Useful offline comparison format; must remain external to the app. |
| PCSX2 as runtime backend or bundled component | PCSX2 | **REJECT** | Violates the native-execution requirement and would introduce an emulator backend. |
| ANGLE graphics behavior / conformance reference | ANGLE | **EVALUATE LATER** | Documentation-only reference for GLES/Vulkan behavior. |
| ANGLE bundled as a second renderer | ANGLE | **REJECT** | Would create a second renderer, add license ambiguity, and violate project constraints. |

## 2. Baseline Used for Comparison

| Item | SOTC Mobile baseline |
|---|---|
| Gradle | 8.7 |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.20 |
| NDK | 26.1.10909125 |
| CMake | 3.22.1 |
| C++ standard | C++20 |
| `minSdk` | 28 |
| `compileSdk` | 34 |
| `targetSdk` | 34 |
| ABIs | `arm64-v8a`, `x86_64` |
| Native entry | `android.app.NativeActivity` with raylib-owned window, loop, audio, and input |
| Native build | `app/src/main/cpp/CMakeLists.txt` with explicit `-Wl,-z,max-page-size=16384` for 64-bit ABIs |
| Audio | `AAudio_createStreamBuilder` in `app/src/main/cpp/engine/audio_engine.cpp`; `LOW_LATENCY`, `SHARED`, PCM float, stereo, 48 kHz, data callback; no error callback or stream recovery |
| UI / import boundary | Kotlin UI, settings, and SAF ISO importer must remain authoritative |

Hard constraints applied during review:

- Native ARM64 execution through static recompilation, not emulation.
- No PCSX2, AetherSX2, NetherSX2, RetroArch cores, Wine, or streaming backend.
- No game assets, ISO content, BIOS files, or generated game code in the repository.
- No replacement of the existing Kotlin UI, settings, or SAF importer.
- No second audio engine.
- No bundling of PCSX2, ANGLE, or Oboe as a second engine/runtime.
- Text/source research only; no binary artifacts.
- All project content in English.

## 3. Provenance and Retrieval Method

Repositories were located through their GitHub root URLs. File paths were discovered with the recursive tree API and file contents were read from `raw.githubusercontent.com` at the recorded commit SHA.

Tree API pattern:

```text
https://api.github.com/repos/OWNER/REPO/git/trees/SHA?recursive=1
```

Raw file pattern:

```text
https://raw.githubusercontent.com/OWNER/REPO/SHA/PATH
```

Commit SHA pattern:

```text
https://api.github.com/repos/OWNER/REPO/commits/DEFAULT_BRANCH
```

A later recheck returned HTTP 403 for five commit-API calls. The public commit Atom feeds were then used to confirm the exact same SHAs. The SHAs below are therefore treated as immutable research anchors.

### 3.1 Repository Metadata

| ID | Repository URL | Default branch | Commit SHA | License |
|---|---|---|---|---|
| C | `https://github.com/ran-j/PS2Recomp` | `main` | `e5636981a1c9de368f6b71e819c3dc188dfedbf4` | GPL-3.0 |
| D | `https://github.com/android/games-samples` | `main` | `d7aab5fbbd1ea83f34e60f62d46e7654edaa104d` | Apache-2.0 |
| E | `https://github.com/google/oboe` | `main` | `6925acbd57590ded8b449030e46b0bd5c017a2ca` | Apache-2.0 |
| F | `https://github.com/LightVelox/sotc-vibe-pc` | `main` | `1ce4a5ea4955878bd2edabdc4e1a6b6f49a8e1ad` | GPL-3.0 |
| G | `https://github.com/PCSX2/pcsx2` | `master` | `f0c84e93813ae3d5e9eefd0ac9edf1bbd86e315f` | GPL-3.0 |
| H | `https://github.com/google/angle` | `main` | `64a0b11458e24cc3afa7e04a2fb534109f30f7ab` | GitHub API: `Other` / `NOASSERTION`; raw `LICENSE` is BSD-style with a no-endorsement clause |

Exact commit-API URLs used:

- `https://api.github.com/repos/ran-j/PS2Recomp/commits/main`
- `https://api.github.com/repos/android/games-samples/commits/main`
- `https://api.github.com/repos/google/oboe/commits/main`
- `https://api.github.com/repos/LightVelox/sotc-vibe-pc/commits/main`
- `https://api.github.com/repos/PCSX2/pcsx2/commits/master`
- `https://api.github.com/repos/google/angle/commits/main`

Exact recursive-tree API URLs used:

- `https://api.github.com/repos/ran-j/PS2Recomp/git/trees/e5636981a1c9de368f6b71e819c3dc188dfedbf4?recursive=1`
- `https://api.github.com/repos/android/games-samples/git/trees/d7aab5fbbd1ea83f34e60f62d46e7654edaa104d?recursive=1`
- `https://api.github.com/repos/google/oboe/git/trees/6925acbd57590ded8b449030e46b0bd5c017a2ca?recursive=1`
- `https://api.github.com/repos/LightVelox/sotc-vibe-pc/git/trees/1ce4a5ea4955878bd2edabdc4e1a6b6f49a8e1ad?recursive=1`
- `https://api.github.com/repos/PCSX2/pcsx2/git/trees/f0c84e93813ae3d5e9eefd0ac9edf1bbd86e315f?recursive=1`
- `https://api.github.com/repos/google/angle/git/trees/64a0b11458e24cc3afa7e04a2fb534109f30f7ab?recursive=1`

Atom-feed confirmation URLs:

- `https://github.com/ran-j/PS2Recomp/commits/main.atom`
- `https://github.com/android/games-samples/commits/main.atom`
- `https://github.com/google/oboe/commits/main.atom`
- `https://github.com/LightVelox/sotc-vibe-pc/commits/main.atom`
- `https://github.com/PCSX2/pcsx2/commits/master.atom`
- `https://github.com/google/angle/commits/main.atom`

## 4. Repository C: PS2Recomp

### 4.1 Purpose and Fit

PS2Recomp is a PS2 static-recompilation toolchain and Android runtime experiment. It is the closest architectural reference to the project objective, but its Android layer is a minimal raylib runner rather than a full application framework.

### 4.2 Exact Files Read

Raw base:

```text
https://raw.githubusercontent.com/ran-j/PS2Recomp/e5636981a1c9de368f6b71e819c3dc188dfedbf4/
```

Files read:

- `android/README.md`
- `android/app/build.gradle`
- `android/build.gradle`
- `android/settings.gradle`
- `android/gradle.properties`
- `android/gradle/wrapper/gradle-wrapper.properties`
- `android/app/src/main/AndroidManifest.xml`
- `CMakeLists.txt`
- `ps2xRuntime/CMakeLists.txt`
- `ps2xRuntime/src/lib/ps2_android_runtime.cpp`
- `ps2xRuntime/src/main.cpp`
- `ps2xRecomp/src/runner/main.cpp`
- `ps2xRuntime/src/lib/ps2_runtime.cpp` — targeted reads of lines 724–744, 2342–2378, and 2415–2475

### 4.3 Version Comparison

| Item | PS2Recomp | SOTC Mobile baseline | Assessment |
|---|---|---|---|
| Gradle wrapper | 8.9; README says Gradle 8.7+/JDK 17 | 8.7 | Conflict at wrapper level; do not silently upgrade our wrapper. |
| AGP | 8.6.1 | 8.5.2 | Conflict; requires deliberate migration if any Gradle code is reused. |
| `compileSdk` | 34 | 34 | Match. |
| `minSdk` | 28 | 28 | Match. |
| `targetSdk` | 34 | 34 | Match. |
| NDK | 28.2.13676358 | 26.1.10909125 | Conflict; toolchain behavior and prefab compatibility must be revalidated. |
| CMake | 3.22.1 | 3.22.1 | Match. |
| raylib | 5.5 | Not used as an app framework | Architecture conflict: raylib owns window, loop, audio, and input. |
| sse2neon | v1.9.1 | Not present | Additional SIMD dependency; evaluate only with performance evidence. |
| 16 KB pages | No explicit flag observed | Explicit `-Wl,-z,max-page-size=16384` for 64-bit ABIs | Missing upstream compliance signal; keep our flag. |

### 4.4 Architecture Findings

- The manifest uses raw `android.app.NativeActivity`, `android:hasCode="false"`, and `android.app.lib_name=ps2EntryRunner`.
- There is no Java/Kotlin application code, no JNI bridge for UI/settings/import, and no in-repo `native_app_glue`.
- raylib owns the Android window, event loop, audio, and input. Adopting the runner wholesale would displace the existing SOTC Mobile UI/settings/SAF importer.
- The boot ELF is supplied through the Gradle property `PS2X_DEFAULT_BOOT_ELF`. This is a build-time game-code path and conflicts with the rule against bundling or importing game code/assets.
- Runtime stdout is piped to `__android_log_write(ANDROID_LOG_INFO, "ps2", line)`. This is a small, reusable pattern for native diagnostics.
- `WindowShouldClose()` maps to `requestStop()`, and `main()` ends in `std::_Exit(0)`. This is not a safe match for our Android lifecycle handling.
- The touch input file contains only `// TODO: on-screen touch joystick / button overlay.` It is not an implementation and must not be counted as touch support.
- GPL-3.0 means source copying would create compliance obligations. Prefer architectural learning and independent reimplementation.

### 4.5 Decisions

- **ADOPT**: Logcat bridge pattern for native stdout/stderr diagnostics, reimplemented independently.
- **EVALUATE LATER**: Recompiler-output ABI boundaries and runtime separation after our own recompiler interface is frozen.
- **REJECT**: Wholesale Android runner, raylib-owned loop, `PS2X_DEFAULT_BOOT_ELF` bundling, lifecycle exit behavior, and the TODO touch overlay.

## 5. Repository D: games-samples

### 5.1 Purpose and Fit

The Android games samples repository is the official source for GameActivity, Frame Pacing, ADPF, Game Mode, and game-controller integration patterns. It is useful as API documentation and sample code, but its current toolchain and application shell are much newer than our baseline.

At inspected SHA `d7aab5fbbd1ea83f34e60f62d46e7654edaa104d`, there is **no dedicated top-level Frame Pacing or GameActivity sample directory**. The relevant GameActivity/Frame Pacing integration is demonstrated inside `agdk/agdktunnel/`.

### 5.2 Exact Files Read

Raw base:

```text
https://raw.githubusercontent.com/android/games-samples/d7aab5fbbd1ea83f34e60f62d46e7654edaa104d/
```

Files read:

- `README.md`
- `agdk/README.md`
- `agdk/agdktunnel/README.md`
- `agdk/agdktunnel/app/build.gradle`
- `agdk/agdktunnel/app/src/main/AndroidManifest.xml`
- `agdk/agdktunnel/app/src/main/cpp/android_main.cpp`
- `agdk/agdktunnel/app/src/main/cpp/native_engine.cpp`
- `agdk/game_controller/README.md`
- `agdk/game_controller/app/build.gradle`
- `agdk/game_controller/app/src/main/AndroidManifest.xml`
- `agdk/adpf/README.md`
- `agdk/adpf/app/build.gradle`
- `agdk/game_mode/README.md`
- `agdk/game_mode/app/build.gradle`
- `agdk/game_mode/app/src/main/res/xml/game_mode_config.xml`
- `agdk/common/include/Versions.h`
- `agdk/common/src/Versions.cpp`
- `agdk/common/include/adpf_manager.h`
- `agdk/common/src/adpf_manager.cpp`
- `codelabs/native-gamepad/README.md`
- `codelabs/native-gamepad/final/app/build.gradle`

### 5.3 Version Comparison

| Item | games-samples | SOTC Mobile baseline | Assessment |
|---|---|---|---|
| `compileSdk` | 37 | 34 | Conflict; do not copy Gradle blocks directly. |
| `targetSdk` | 37 | 34 | Conflict; would change platform behavior and require validation. |
| `minSdk` | 24 | 28 | Not a conflict; baseline is stricter. Raise copied modules to 28. |
| NDK | 29.0.14206865 | 26.1.10909125 | Conflict; API guards may work, but native build validation is required. |
| Android Studio | READMEs require 2026.1.1 Patch 1 | Baseline not tied to that IDE version | Toolchain drift; avoid wholesale sample adoption. |
| games-activity | 4.4.2 | `android.app.NativeActivity` + raylib | Architecture conflict; adopting it would replace our runner. |
| games-frame-pacing | 2.1.3 | Not used | Evaluate only after raylib swap/pacing measurements. |
| games-controller | 2.0.2 | Existing input path | Evaluate for gamepad mapping, not wholesale input replacement. |
| Oboe in AGDKTunnel | 1.10.0 | Raw AAudio engine | Do not add beside the current engine. |
| Flexible page sizes | `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` | Explicit `-Wl,-z,max-page-size=16384` for 64-bit ABIs | Similar goal, different mechanism; keep ours until measured. |

### 5.4 API Floors and Guards

- ADPF Thermal API requires API 30.
- Performance Hint Manager requires API 31.
- Game Mode API requires API 33.
- Samples use runtime checks with `__builtin_available` and weak-symbol handling through `__ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__`; the weak-symbol mechanism assumes NDK 26+.
- `ADPFManager` combines `AThermalManager`, thermal headroom, battery state, and JNI calls into a Performance Hint session.

Our `minSdk` is 28, so any ADPF/Game Mode code must be guarded and must degrade cleanly on API 28–29 devices.

### 5.5 Architecture Findings

- AGDKTunnel uses GameActivity and a native engine shell; this would replace our existing `NativeActivity`/raylib loop.
- The game-controller and codelab samples show controller discovery and event handling patterns, but input decisions must preserve our settings and touch-control design.
- The `game_mode_config.xml` pattern is promising for API 33+ devices and does not inherently replace our UI.
- Oboe appears as a sample dependency; importing it “as-is” would risk creating a second audio engine.

### 5.6 Decisions

- **ADOPT**: Runtime API availability guards and explicit 16 KB/flexible-page-size awareness as design requirements.
- **EVALUATE LATER**: ADPF thermal/Performance Hint integration, Game Mode config, and controller mapping patterns.
- **REJECT**: Wholesale AGDKTunnel shell, GameActivity/Frame Pacing integration in the current milestone, Oboe sample dependency as-is, and compile/target 37 / NDK 29 toolchain jump.

## 6. Repository E: Oboe

### 6.1 Purpose and Fit

Oboe is Google’s native audio library. It can use AAudio underneath while adding loader behavior, device quirks, and stream-recovery handling. It is relevant only if we decide to replace the current raw AAudio engine.

### 6.2 Exact Files Read

Raw base:

```text
https://raw.githubusercontent.com/google/oboe/6925acbd57590ded8b449030e46b0bd5c017a2ca/
```

Files read:

- `docs/GettingStarted.md`
- `include/oboe/AudioStreamBuilder.h`
- `src/aaudio/AudioStreamAAudio.cpp`
- `include/oboe/Definitions.h`

### 6.3 Version and Integration Findings

- Integration options include Google Maven via Prefab or building from source.
- The games-samples AGDKTunnel sample pins Oboe 1.10.0; that version is sample-specific, not automatically our target.
- `DefaultStreamValues::SampleRate`, `FramesPerBurst`, and `ChannelCount` can be supplied through JNI. Our Kotlin layer would need to own that bridge.
- The AAudio backend contains loader and quirk handling plus stream-recovery behavior.
- Our current `audio_engine.cpp` uses AAudio directly and has no error callback or stream recovery.

### 6.4 Architecture Findings

- Adding Oboe while keeping `audio_engine.cpp` would produce two audio engines and ambiguous stream ownership.
- A viable Oboe migration must replace the existing engine, preserve the single data-callback path, and keep all lifecycle/stream state under one owner.
- Apache-2.0 is acceptable with attribution, but this does not remove the architectural constraint.

### 6.5 Decisions

- **ADOPT**: Only as a complete replacement for the current AAudio engine, and only after measured evidence of AAudio failure or latency/recovery problems.
- **EVALUATE LATER**: Error callback, stream recovery, and JNI-supplied default stream values.
- **REJECT**: Adding Oboe as a second audio dependency beside the existing engine.

## 7. Repository F: sotc-vibe-pc

### 7.1 Purpose and Fit

`sotc-vibe-pc` is a Windows PC analysis/testing project for Shadow of the Colossus. It is useful as a methodology reference for deterministic scenarios and golden-run comparison, not as source to import.

### 7.2 Exact Files Read

Raw base:

```text
https://raw.githubusercontent.com/LightVelox/sotc-vibe-pc/1ce4a5ea4955878bd2edabdc4e1a6b6f49a8e1ad/
```

Files read:

- `Docs/TESTING.md`
- `Tools/golden_run.py`
- `Tools/scenario.py`

### 7.3 Environment and Specificity

| Item | Upstream value |
|---|---|
| Platform | Windows 10/11 x64 |
| CPU | AVX2 |
| Graphics | OpenGL 4.6 |
| Game region/build | SCES-53326 PAL |
| ISO SHA-256 | `068925770645266189005f09c9a9a1fee25726bfc93c89b12f832f3673664ac4` |
| License | GPL-3.0 |

All addresses and hooks are specific to SCES-53326 v1.00. They must not be treated as portable across regions or revisions.

### 7.4 Reusable Testing Ideas

- Save-state-driven scenarios.
- Named scenario helpers such as `bossStatTimeAttackJumpSekiban(n)`.
- Access patterns such as `GAMEUTIL_get_boss_obj()`.
- Debug death hook.
- Scheduler and VBlank counters.
- Golden comparison against GS dumps under `Analysis/oracle/gs_golden`.

These ideas can inform our own test harness structure. The actual hooks, addresses, ISO hash, and golden data must not be copied.

### 7.5 Decisions

- **ADOPT**: Scenario/golden-run test structure as a concept, reimplemented for our own supported ISO and validation workflow.
- **EVALUATE LATER**: Deterministic save-state scenarios and VBlank/scheduler counters once our gameplay test harness exists.
- **REJECT**: Importing generated game code, SCES-53326-specific addresses, region-pinned ISO data, Windows/OpenGL harness code, or GPL-3.0 source into the Android app.

## 8. Repository G: PCSX2

### 8.1 Purpose and Fit

PCSX2 is an emulator. Its GS dump tooling is useful as an external offline oracle format, but PCSX2 must never become part of the SOTC Mobile runtime.

### 8.2 Exact Files Read

Raw base:

```text
https://raw.githubusercontent.com/PCSX2/pcsx2/f0c84e93813ae3d5e9eefd0ac9edf1bbd86e315f/
```

Files read:

- `pcsx2/GS/GSDump.cpp`
- `pcsx2/GS/Renderers/Common/GSRenderer.cpp`

### 8.3 Findings

- GS dumps capture command-stream behavior that can be compared offline against our own recompiled renderer state.
- PCSX2 is GPL-3.0 and has a large C++ emulator codebase unrelated to our Gradle/Kotlin/native app structure.
- Any use of PCSX2 code would violate the native-execution requirement and create a second engine.

### 8.4 Decisions

- **ADOPT**: The concept of an external GS-dump golden oracle for development-time verification only.
- **EVALUATE LATER**: A comparison tool that consumes dumps generated during authorized testing; keep it outside the shipped app.
- **REJECT**: PCSX2 as a backend, bundled component, runtime dependency, or source base.

## 9. Repository H: ANGLE

### 9.1 Purpose and Fit

ANGLE is a graphics translation and conformance project. It is useful as a reference for GLES/Vulkan behavior and test methodology. It is not suitable as a bundled renderer for this project.

### 9.2 Exact Files Read

Raw base:

```text
https://raw.githubusercontent.com/google/angle/64a0b11458e24cc3afa7e04a2fb534109f30f7ab/
```

Files read:

- `README.md`
- `LICENSE`

### 9.3 License Finding

The GitHub API reports the license as `Other` / `NOASSERTION`. The raw `LICENSE` file contains BSD-style terms with a no-endorsement clause. Treat this as requiring review before any source reuse; documentation-level reference does not create that risk.

### 9.4 Decisions

- **ADOPT**: Conformance-testing mindset and graphics-behavior reference documentation only.
- **EVALUATE LATER**: Specific GLES/Vulkan behavior notes when debugging our native renderer.
- **REJECT**: Bundling ANGLE as a second renderer, using it as a runtime translation layer, or copying source without explicit legal clearance.

## 10. Consolidated Version Conflict Matrix

| Area | Upstream signals | Baseline | Conflict and action |
|---|---|---|---|
| Gradle | C wrapper 8.9; D requires newer Android Studio toolchain | 8.7 | **Conflict.** Do not upgrade the project wrapper for research findings alone. |
| AGP | C 8.6.1; D newer sample toolchain | 8.5.2 | **Conflict.** Any copied Gradle block must be rewritten for 8.5.2 or migrated deliberately. |
| Kotlin | C has no Kotlin app code; D samples use a newer Android Studio/AGP environment; E/F/G/H have no Kotlin build integration relevant here | 1.9.20 | **No direct Kotlin adoption.** Keep 1.9.20; revalidate any sample Kotlin code if it is ever considered. |
| NDK | C 28.2.13676358; D 29.0.14206865 | 26.1.10909125 | **Conflict.** Guard API usage; revalidate native builds before toolchain changes. |
| CMake | C 3.22.1 | 3.22.1 | **No conflict.** |
| `minSdk` | C 28; D 24 | 28 | **No conflict**, but copied D modules must be raised to 28. |
| `compileSdk` / `targetSdk` | C 34/34; D 37/37 | 34/34 | **Conflict with D.** Keep 34 until a deliberate platform upgrade. |
| Page size | C has no explicit flag; D uses flexible page sizes with NDK 29 | Explicit `-Wl,-z,max-page-size=16384` for 64-bit ABIs | **Keep ours.** Do not replace a verified requirement with an upstream sample flag. |
| App shell | C raylib `NativeActivity`; D GameActivity | Kotlin UI + raylib `NativeActivity` | **Conflict.** Reject wholesale shell replacement. |
| Audio | D Oboe 1.10.0; E Oboe library | Raw AAudio engine | **Conflict if added alongside.** Oboe only as replacement, never as second engine. |
| Licenses | C/F/G GPL-3.0; D/E Apache-2.0; H Other/NOASSERTION | Project must respect all licenses | **Legal constraint.** Prefer concepts over source copying; require review for any source reuse. |

## 11. Final Recommendation

Proceed with a narrow, evidence-driven platform path:

1. Keep the current Gradle/AGP/Kotlin/NDK/CMake baseline until a measured need forces an upgrade.
2. Keep the current Kotlin UI, settings, and SAF importer; do not adopt upstream Android app shells.
3. Keep the explicit 16 KB page-size linker flag.
4. Preserve a single audio engine; treat Oboe as a future replacement candidate only.
5. Use games-samples APIs only as guarded, API-floor-aware features after the existing runtime is stable.
6. Use PS2Recomp only as an architectural reference for recompiler/runtime boundaries and native diagnostics.
7. Use PCSX2 and `sotc-vibe-pc` only as external development-time oracle/test-methodology references.
8. Use ANGLE only as documentation/reference, never as a bundled renderer.

No source files were modified during this research. The only output of this task is this report.
