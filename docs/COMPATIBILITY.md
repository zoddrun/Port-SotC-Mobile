# Compatibility

Status: **Development (host foundation, not playable)**  
Last updated: **2026-11-19**

This document defines which original game release is targeted, how compatibility is validated, and what has (and has not) been verified. It is intentionally explicit and must remain consistent with tested reality.

> **Not a generic PS2 compatibility claim.** This project targets a specific retail release of *Shadow of the Colossus* for static recompilation and native Android execution. Different releases may differ in executable code, module layout/data, addresses, and function mappings.

## 1. Initial supported target

| Field | Required / Target value |
| --- | --- |
| Game title | *Shadow of the Colossus* |
| Original platform | PlayStation 2 |
| Region | USA (NTSC-U) retail |
| Game serial | `SCUS-97472` |
| Expected boot executable | `SCUS_974.72` |
| Container format | ISO9660 disc image (`.iso`) selected by the user |
| Required game files/modules | `SYSTEM.CNF`, boot executable `/SCUS_974.72`, `/KERNEL.XFF`, `/MANAGER.XFF`, `/GAMECORE.XFF` |

The native game engine/runtime is being prepared around this specific retail release. The application is intended to match the selected game image to the correct compiled game core/runtime for that release.

## 2. Core compatibility requirements

For an image to be considered compatible with the current importer/validator and target core, it must:

- Be a readable ISO9660 image containing the expected PlayStation 2 disc structure.
- Contain a valid `SYSTEM.CNF` identifying the supported boot executable/serial.
- Resolve to serial **`SCUS-97472`** and boot executable **`SCUS_974.72`**.
- Contain a valid PS2 ELF boot executable at the expected path (as validated by the app/tooling).
- Contain required modules **`KERNEL.XFF`**, **`MANAGER.XFF`**, and **`GAMECORE.XFF`** with expected module signatures/magic (e.g. `xff2` checks used by validation logic).
- Be an unmodified, supported retail image for this release (content/structure consistent with the target).

**Important:** Passing import/identity validation does **not** mean the game is playable. Playability requires a compatible, packaged, and verified native game core/runtime.

## 3. How the application validates the selected image

When a user selects an image through the Android Storage Access Framework (SAF), the importer/validator is designed to:

1. Open the selected document read-only (the original file is not modified or deleted by the app).
2. Copy the image into app-private staging/storage while tracking progress and allowing cancellation.
3. Compute integrity metadata (e.g. SHA-256 of the copied image) as part of import/manifest handling.
4. Inspect disc structure/content (ISO9660, `SYSTEM.CNF`, boot path/serial derivation).
5. Validate the boot PS2 ELF and required modules (`KERNEL.XFF`, `MANAGER.XFF`, `GAMECORE.XFF`).
6. Accept only the supported release identity (`SCUS-97472` / `SCUS_974.72`) and reject unsupported or structurally invalid images with specific errors.
7. Publish the validated install atomically and preserve reserved save data across re-import/recovery flows as implemented.

Validation is content-based, not filename-based. A file named `.iso` is not trusted unless its contents pass checks.

## 4. Compatibility status definitions

Use these terms consistently:

| Term | Meaning |
| --- | --- |
| **Identified** | Serial/boot identity recognized (e.g. `SCUS-97472`). |
| **Importable** | Passes importer/validator checks and can be stored in the app's managed layout. |
| **Core compatible** | Matches the exact release/runtime assumptions required by a built native core (not yet available/verified for gameplay). |
| **Boot verified** | Native runtime reaches verified boot/title behavior on a stated Android target (not yet achieved). |
| **Gameplay verified** | Actual in-game behavior verified on a stated Android target with evidence (not yet achieved). |

## 5. Release compatibility table

| Game release | Serial | Identified | Importable (host validation) | Core compatible | Boot verified | Gameplay verified | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| USA Retail | `SCUS-97472` | Target | **Validation logic implemented; synthetic SAF import verified on emulator (2026-11-09).** Retail image content validated on development host (2026-11-09); on-device retail import not yet exercised. | No (no verified packaged core/runtime yet) | No | No | **Initial target (in development)** |
| European Retail | `SCES-53326` | Not targeted | No | No | No | No | Not verified / not supported |
| Japanese Retail | Verify exact release identity before any claim | No | No | No | No | No | Not verified / not supported |
| Other PS2 games | Various | No | No | No | No | No | Not supported |

> "Identified" or "Importable" must **not** be presented as "Supported for gameplay."

## 6. Known unsupported or unverified cases

- Japanese preview build `SCPS_150.97` and other non-target serials/executables.
- PAL and Japanese retail releases (different code/data/addressing assumptions possible).
- Modified, translated, patched, repacked, trimmed, or otherwise altered images (even if they claim the same serial).
- Generic/unknown PS2 ISOs or images for other games.
- Images missing required files/modules or failing structural/ELF/module validation.
- Any release not independently implemented and verified against a matching native core/runtime.

## 7. Disc integrity and verification notes

- **Developer validation (host, 2026-11-09):** A legally obtained USA retail image (`SCUS-97472`) was validated on the development host using `tools/inspect_original_iso.py` (ISO9660, `SYSTEM.CNF`, boot ELF, required XFF module checks). The machine-readable report is written locally to `build/reports/original_iso.json` and includes integrity metadata (SHA-256) for that copy. The original image is stored privately under `original iso/` (gitignored) and is never committed or distributed by this project.
- **User responsibility:** You must provide your own legally obtained copy of the supported game release. This project does not require or provide a publicly downloaded ISO.
- **Checksums:** A checksum can help confirm you are testing the same image content, but users must rely on their own lawful copy and the app's/ tooling's structural validation. Do not assume a matching filename alone indicates compatibility.

## 8. BIOS and emulator clarification

- This project does **not** use PCSX2, AetherSX2, NetherSX2, RetroArch cores, Play!, Wine, or any other PS2 emulator/compatibility layer as a runtime backend.
- This project does **not** provide or require distribution of PlayStation 2 BIOS files as part of the repository.
- The intended approach is **static recompilation and native execution** with an Android host/runtime. Only dependencies and runtime requirements demonstrated by the actual implementation should be treated as required.

## 9. Android platform compatibility

| Requirement | Value / status |
| --- | --- |
| Minimum Android version | Android 9 (API 28) |
| Target Android version | Android 14 (API 34) |
| Compile SDK | 34 |
| Shipping ABI target | `arm64-v8a` |
| Debug/test ABI (emulator integration) | `x86_64` (not a supported shipping/gameplay target) |
| Graphics | OpenGL ES 2.0 minimum (manifest floor). The host requests ES 3 and falls back to ES 2 automatically; shaders are GLSL ES 1.00. Vulkan is not implemented. |
| Touchscreen | Required |
| Gamepad | Optional |
| Audio | AAudio path in host (48 kHz stereo requested in host implementation) |

## 10. Verified and unverified behavior (honest summary)

### Verified (host/integration checks)

On **2026-11-09** (development host and Android 9 / API 28 `x86_64` emulator, SwiftShader; see project history/docs for environment details):

- Host app build/test/lint success recorded for the verified state at that time (including 49 passing unit tests as documented).
- Emulator install/launch, EGL/GLES 3 surface creation, AAudio startup/callback activity, pause/resume lifecycle behavior.
- Touch input, multitouch behavior, and touch layout editor interactions (debug/input verification).
- Synthetic ISO9660 SAF import/copy/validation/extraction/manifest flow.
- Retail `SCUS-97472` image content validation on the development host via `tools/inspect_original_iso.py`.

Re-verified for the **`v0.1.0-alpha.1`** preview on **2026-11-18** (JDK 17, Android SDK 34, Android 9 / API 28 `x86_64` emulator with SwiftShader):

- 49 unit tests pass; lint and a clean debug build pass.
- APK installs and launches on the emulator without a startup crash.
- Main screen, Settings, Controller settings, and ISO import screen render and respond; the Storage Access Framework picker opens.
- Native host library loads; AAudio opens a 48 kHz stereo output stream; EGL 1.4 initializes.
- The APK is debug-signed and contains `arm64-v8a` and `x86_64` native libraries.

Verification for the **`v0.3.0-alpha.1`** preview (2026-11-20, published):

- 139 JVM tests pass; `lintDebug` and `assembleDebug` pass with JDK 17.
- The host x86 smoke run keeps the original 13 pacing assertions unchanged and adds independent `[hd-shader]` assertions for patch 0011. It proves shader-module compilation and telemetry only, not rendered output.
- The ARM64 runner with patches 0001-0011 is rebuilt, statically verified with `RUNNER_VERIFY_OK`, packaged and checked by `verify-apk.py` with `APK_NATIVE_VERIFY_OK` (0 failures, 0 warnings). All native libraries use 16 KB LOAD and ZIP alignment.
- Package and application ID remain `com.portsotc.mobile` / `SOTC`. Only `SCUS-97472` is supported; the other eight recognized serials still report `KNOWN_UNVERIFIED_SERIAL`.
- No physical ARM64 device has loaded this preview. First boot, gameplay, rendering, audio, touch ergonomics, lifecycle, saves, thermals, 16 KB device loading and on-device telemetry remain unverified.

Historical verification for the **`v0.2.0-alpha.1`** preview (2026-11-19, published):

- 105 unit tests pass; `lintDebug` and `assembleDebug` pass. Tests cover the GPU report parser, graphics presets, persisted settings, the runtime environment contract and capability tiers.
- APK badging confirms installed label `SOTC`, package `com.portsotc.mobile` (unchanged), version `0.2.0` (code 2), and native libraries for `arm64-v8a` + `x86_64`. The ARM64 runner is packaged and passes `RUNNER_VERIFY_OK` and `APK_NATIVE_VERIFY_OK`, but has never run on a device.
- GPU compatibility reworked: the manifest floor is OpenGL ES 2.0, the host requests ES 3 and falls back to ES 2 automatically, shaders are GLSL ES 1.00 with vertex buffers instead of VAOs, and Settings → Graphics offers `Force OpenGL ES 2.0`.
- Emulator verification on the API 28 image (which reports OpenGL ES 2.0 only) confirmed the fallback end to end: the ES 3 config request failed, the ES 2 config/context/surface succeeded, `Renderer backend: OpenGL ES 2.0` was logged, shaders compiled, and frames rendered at 1280x720 (screenshot: 100% non-black pixels, ~13-15 fps under SwiftShader).
- The native GPU capability query returned a real ES 2 context: backend `OpenGL ES 2.0`, vendor `Google (Google Inc.)`, renderer `Android Emulator OpenGL ES Translator (Google SwiftShader)`, GLSL `OpenGL ES GLSL ES 1.0.17`, texture size 8192, 16 attributes, 28 extensions.
- Adaptive resolution was observed stepping down (`26% -> 25%` at 50.6 ms against a 16.7 ms budget) and clamping at the 25% floor.
- The low-memory path was exercised with `am send-trim-memory ... RUNNING_LOW`: the offscreen render target was released and rendering continued directly to the window.
- Graphics presets persist real values: selecting Battery Saver wrote `resolution_scale_percent=50`, `adaptive_resolution=true` and `frame_limit_fps=30` to `shared_prefs/sotc_settings.xml` and the UI showed `CURRENT · BATTERY SAVER · 50% · 30 fps`.

### Not verified

- The OpenGL ES 3 renderer path on any ES 3-capable device or emulator (only the ES 2 fallback has been exercised).
- The frame limiter at runtime: SwiftShader cannot reach 30 fps, so the cap never binds.
- The thermal render-scale ceiling at runtime: the emulator always reports thermal status 0.
- On-device import of the retail multi-GB `SCUS-97472` image through a real device content provider flow.
- ARM64 hardware installation/execution/performance/thermal behavior.
- Native game core/runtime packaging and ABI/runtime interoperability in a shipped app build.
- Boot to title, in-game rendering, in-game audio, input during real gameplay, save/load behavior in gameplay, or any gameplay compatibility.
- Bluetooth/USB gamepad and rumble behavior on physical hardware.

## 11. Current limitations

- **No playable game core:** without a completed, packaged, and verified recompiled runtime/core, the app cannot run original game content. The host must present diagnostics/status honestly (no fake gameplay).
- **Runner built but never loaded:** the pipeline reached `PIPELINE_DONE`, the ARM64 `libps2EntryRunner.so` is built, stripped, verified and packaged in the APK, but no ARM64 device has loaded it. The x86_64 capture build produced one non-blank frame; that is the only runtime rendering evidence.
- **No ARM64 device validation:** real-device behavior is unknown until tested.
- **Performance unknown:** emulator/software-renderer numbers are not representative of ARM64 mobile hardware. Adaptive resolution, the frame limiter and the thermal ceiling are implemented in the host engine, but the limiter and the thermal path could not be exercised on the available emulator, and none of them reach the raylib-owned runtime loop.
- **No Vulkan backend:** Vulkan is not implemented. The compatibility path is OpenGL ES 2.0 and OpenGL ES 3.0.
- **Save/gameplay compatibility unknown:** memory-card/save behavior depends on the eventual runtime/core implementation and has not been verified.

## 12. Compatibility policy

A feature or release state may only be claimed as compatible/verified when:

1. It has been tested on the stated hardware or a documented emulator/environment.
2. The exact disc serial and boot identity are recorded (`SCUS-97472` / `SCUS_974.72` for the initial target).
3. Evidence exists (logs, screenshots, measurements, or automated test results).
4. Known defects and limitations are documented clearly.

Until then, status must remain **identified**, **importable (with scope stated)**, **implemented/not verified**, **blocked**, or **not available** as appropriate.