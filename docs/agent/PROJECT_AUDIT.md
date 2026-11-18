# Project Audit

Status: **Development — host foundation complete, native game core not yet packaged**  
Audit date: **2026-11-18**  
Auditor: project engineering session

This document records a factual audit of the repository, the Android application, the native host, and the recompilation workspace. It separates verified evidence from planned work and lists the concrete blockers that prevent a playable release. Nothing in this document should be read as a gameplay claim.

## 1. Scope

Audited areas:

- Repository layout, Git state, protected assets, and ignored content.
- Gradle/AGP/Kotlin/NDK configuration and reproducible build commands.
- Android manifest, activities, permissions, orientation, and packaged ABI list.
- Kotlin application layer: launcher, import, settings, controls, game session.
- Native host layer (`app/src/main/cpp`): JNI bridge, EGL/GLES host, audio, core loader.
- Host-side recompilation workspace in WSL (`/root/sotc`) and its logs/artifacts.
- Public documentation accuracy against the current build.

Out of scope: reverse-engineering results not already captured by the pipeline, ARM64 hardware behavior (no device attached), and game content analysis beyond disc validation.

## 2. Repository and build evidence

| Item | Evidence | Result |
| --- | --- | --- |
| Application ID | `app/build.gradle`; `aapt2 dump badging` | `com.portsotc.mobile` unchanged |
| Installed label | `app/src/main/res/values/strings.xml`; APK badging | `SOTC` |
| Version | APK badging | `0.1.0` (versionCode 1) |
| Minimum / target SDK | `app/build.gradle` | 28 / 34 |
| Packaged ABIs | APK native-code list | `arm64-v8a`, `x86_64` |
| Unit tests | `gradlew testDebugUnitTest` | 57 tests, 0 failures |
| Lint | `gradlew lintDebug` | Pass |
| Debug APK | `gradlew assembleDebug` | Built successfully |
| Protected ISO | `.gitignore`, `git status` | `original iso/` untracked and unmodified |
| Launcher icon | APK resources | Developer artwork retained |

Build toolchain used for this audit:

- JDK 17 (`C:\Users\maria\jdk-17`); the default JDK 25 is incompatible with Gradle 8.7/AGP 8.5.2.
- Gradle 8.7, AGP 8.5.2, Kotlin 1.9.20, CMake 3.22.1, NDK `26.1.10909125`.

## 3. Android application audit

### 3.1 Launcher

The launcher was rewritten as a product screen rather than a diagnostic dashboard:

- Wordmark, title, and target-release subtitle.
- Single status card: state label, one-line explanation, serial/title metadata.
- One primary action (`PLAY`), disabled unless game data and the native runtime are both present.
- Contextual hint under the primary action explaining exactly what is missing.
- `VIEW DETAILS` opens a dialog with serial, title, boot file, image size, SHA-256, modules, and runtime state.
- Secondary actions: Import, Settings, Controls.
- Legal notice and build summary.
- Diagnostics are no longer reachable from the launcher; they live under Settings → Advanced.

This satisfies the requirement that diagnostics are never presented as gameplay and that the primary action is honest about availability.

### 3.2 Settings

Settings are now an index plus one screen per category:

| Category | Content |
| --- | --- |
| General | Build identity, touch-control master switch, reset |
| Graphics | Internal resolution scale, linear filtering, GLES capability readout |
| Performance | Host statistics overlay, engine state banner, render presets |
| Audio | Enable/disable, master volume, device sample-rate/burst readout |
| Controls | Touch presets, size/opacity, camera sensitivity, deadzone, inversion, layout editor, controller settings |
| Storage | Installed game metadata, storage usage, SHA-256 verification, remove data |
| Advanced | Runtime status, ABI, capabilities, diagnostics entry, report copy, reset |

Unimplemented options are documented as unavailable rather than simulated:

- Frame limiter and refresh-rate control are not implemented.
- A Vulkan backend is not implemented.
- Separate music and sound-effect volumes are not implemented.
- FPS overlay is a host render-statistics overlay, not a game frame-rate measurement, and is off by default.

### 3.3 Touch controls

- Three presets: Classic, Modern, Compact, plus Custom.
- Presets only change position, radius, and scale; button masks and stick sides are invariant (covered by unit tests).
- Editing a layout marks the active preset as Custom; resetting restores Classic.
- Multitouch pointers are tracked by pointer id, so releasing one finger does not cancel another held control.
- Radial deadzone with rescaling, separate camera sensitivity, and horizontal/vertical inversion.
- Input is released on focus loss, detach, and activity pause.
- Touch controls can hide automatically while a physical gamepad is connected; hot-plug is observed through `InputManager.InputDeviceListener`.

### 3.4 Game session

- Landscape, immersive, keep-screen-on.
- Dedicated pad polling loop feeds the native host.
- Pause panel exposes resume, settings, controls, and quit.
- Statistics are labeled `HOST FPS` to avoid implying game-engine performance.
- When the native core is absent, the session is explicitly labeled as diagnostics, not gameplay.

## 4. Native host audit

Implemented and previously verified in the host:

- EGL/GLES rendering surface and render-target scaling.
- AAudio output with OpenSL fallback.
- JNI status reporting and guarded calls that fail safely when the core library is absent.
- ABI-v1 `dlopen` loader with version and symbol validation for `libsotc_game.so`.
- Input snapshot bridge using SCE_PAD-compatible bits and normalized analog values.

Not implemented or not verified:

- A packaged `libsotc_game.so` for `arm64-v8a`.
- A first boot of the original game through that library.
- On-device rendering, audio, input, saves, and lifecycle behavior for actual gameplay.
- GLES 3 rendering verification: the available API 28 emulator image reports OpenGL ES 2.0 only.

## 5. Recompilation pipeline audit

Workspace: `/root/sotc` in WSL2, driven by `pipeline-remaining.sh`.

Verified history:

- Disc extracted and validated against `SCUS-97472`.
- Recompiler built; `stage_boot` emitted 1429 C++ translation units.
- `ps2EntryRunner` built successfully on attempt 6 after resolving NEON, x86 host, dyld, and ODR issues.
- KERNEL capture produced `kernel.bin` (32 MiB).

Current state at audit time:

- The pipeline was recovered and is running unattended.
- It is in `stage_game after KERNEL`, rebuilding `ps2EntryRunner` with the new KERNEL-derived code.
- No `manager.bin`, `gamecore.bin`, VU1 output, or `PIPELINE_DONE` marker exists yet.
- Each `stage_game` round regenerates runner sources, so each round is a full rebuild; on this host (4 cores, 1.7 GiB RAM plus 8 GB swap) a round takes hours.
- The pipeline must complete MANAGER, GAMECORE, and VU1 stages before M4 can start.

Constraints:

- WSL host memory is the limiting factor; `-j2` is deliberate to avoid OOM during compilation and LTO linking.
- No physical ARM64 device is attached, so device verification cannot be performed from this environment.

## 6. Documentation audit

Corrected in this pass:

- Application label and launcher behavior.
- Settings structure and honest availability of options.
- Control presets, deadzone, sensitivity, inversion, and gamepad hot-plug behavior.
- Pipeline status: recovered and running rather than stalled.
- Test count and build evidence.

Still required before a playable release:

- Verified boot and gameplay evidence.
- ARM64 device results.
- A release note that does not imply gameplay before it exists.

## 7. Risk register

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Pipeline rebuild takes many hours per stage | Delays M3 | Keep the unattended run alive; monitor logs; do not restart completed stages |
| WSL memory exhaustion during LTO | Build failure | 8 GB swap active; `-j2`; retry the link if it fails |
| Emulator lacks GLES 3 | Rendering path unverified | Verify on capable hardware; keep GLES 2 failure handling |
| No ARM64 device | Cannot verify target | Continue host/pipeline work; test when hardware is available |
| Temptation to present diagnostics as gameplay | Project integrity | Launcher disables Play; sessions labeled; docs explicit |
| Large generated artifacts accidentally committed | Repository pollution | Keep derived content under ignored paths; never commit ISO or dumps |

## 8. Conclusion

The Android product layer is now a coherent, honest application: a premium launcher, categorized settings, real touch and gamepad input handling, storage verification, and diagnostics isolated from gameplay. The project still has no playable native game core. The recompilation pipeline is running again, but its remaining stages and the ARM64 cross-compile are prerequisites for any gameplay claim. The next engineering milestone is completion of the pipeline, followed by the ARM64 build and runtime integration.
