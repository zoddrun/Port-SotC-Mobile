# Upstream Integration Matrix

Status: **Reference document for engineering decisions**  
Last updated: **2026-11-18**

This matrix records which upstream projects are used, evaluated, or explicitly rejected by Shadow of the Colossus Mobile. It exists to keep licensing, integration status, and technical rationale in one place and to prevent undocumented dependencies from entering the build.

## 1. Evaluation criteria

Each candidate is assessed against:

1. **Legality and licensing** — compatible with the project's non-commercial development model and distribution obligations.
2. **Native execution** — does not introduce an emulator, compatibility layer, or remote streaming backend.
3. **ARM64 Android viability** — builds with the NDK and runs on `arm64-v8a`.
4. **Integration cost** — measurable benefit against maintenance and binary-size cost.
5. **Evidence** — can the integration be tested and verified on this project's targets.

## 2. Matrix

| Project | License | Role | Current use | Status | Decision and rationale | Risks / notes |
| --- | --- | --- | --- | --- | --- | --- |
| **Celosia** | GPL-3.0 | Host-side driver for the PS2 static recompilation workflow (extract, capture, stage, VU1 codegen) | Yes — pinned under `third_party/celosia`; live workspace at `/root/celosia` | Active, build-time only | Chosen driver because it sequences PS2Recomp stages and already supports the target workflow. Not shipped in the APK. | GPL-3.0 obligations apply to derived runtime binaries; keep license texts and source availability. |
| **PS2Recomp** | GPL-3.0 | Recompiler and runtime used to translate PS2 EE/VU code into C++ and execute it natively | Yes — embedded by Celosia and present under `third_party/PS2Recomp` | Active, build-time and runtime | Core of the static recompilation approach. Runtime pieces become part of the game core, not the Android host shell. | Large generated source tree; long builds; GPL-3.0 distribution obligations. |
| **GhostScitSMT Android port** | Reference only | Prior art for running a recompiled PS2 game on Android | No | Reference | Studied for Android packaging and lifecycle ideas; no code copied. | Licensing and quality must be checked before any code reuse; none is planned. |
| **RetroTouch** | MIT | Reference for touch overlay ergonomics | No | Reference | The current overlay is original code; RetroTouch informed layout expectations only. | No dependency added. |
| **Paddleboat** | Apache-2.0 | Gamepad support library | No | Evaluated | The existing `GamepadManager` already covers key/generic-motion mapping without a new dependency. Re-evaluate if rumble or advanced controller features are needed. | Adds dependency and lifecycle surface for limited current benefit. |
| **AndroidX GameActivity** | Apache-2.0 | Alternative activity/surface integration | No | Evaluated | The project uses a plain `Activity` plus a custom `SurfaceView`, which is smaller and already verified. GameActivity would add AndroidX to a deliberately AndroidX-free app. | Revisit only if input-method or surface integration becomes a blocker. |
| **Android Game Mode / Swappy** | Apache-2.0 | Frame pacing and refresh-rate helpers | No | Evaluated | Frame limiting and refresh-rate control are not implemented; adding Swappy now would imply behavior that does not exist. | Candidate for M8 after real frame-time measurements. |
| **Oboe** | Apache-2.0 | Audio abstraction | No | Evaluated | The native host already uses AAudio with an OpenSL fallback directly, which is sufficient and avoids another dependency. | Revisit if device-specific audio latency requires Oboe's tuning paths. |
| **Vulkan** | Apache-2.0 (headers) | Alternative graphics backend | No | Not implemented | OpenGL ES 2 is the floor and OpenGL ES 3 is used when the driver accepts it; there is no Vulkan path. A Vulkan backend is a substantial project and is not claimed. | Do not list as supported until implemented and tested. |
| **SDL2** | zlib | Windowing/input abstraction | No | Rejected | The host already owns EGL, AAudio, and Android input; SDL would duplicate that layer and enlarge the APK. | None. |
| **PCSX2 / AetherSX2 / NetherSX2 / RetroArch / Play! / Wine** | Various | Emulation or compatibility layers | No | **Prohibited** | The project is a native static-recompilation port. Any emulator backend would violate the project's core requirement and user trust. | Never introduce, even as a fallback. |
| **Remote streaming** | N/A | Off-device execution | No | **Prohibited** | Game logic must execute natively on the Android device. | None. |

## 3. Current dependency surface

Shipped in the Android host APK:

- Kotlin standard library and Android platform APIs only.
- Native host built from this repository's C++ sources.
- No third-party runtime libraries are linked into `libsotc_core.so` beyond the Android NDK/platform libraries.

Build-time only:

- Celosia and PS2Recomp (GPL-3.0) in the host recompilation workspace.
- CMake, Ninja, GCC/Clang, Python tooling.

Not shipped:

- Game data, BIOS files, disc images, extracted assets, or recompiled game code.
- Any emulator or compatibility layer.

## 4. Licensing obligations

- Celosia and PS2Recomp are GPL-3.0. Any distributed binary derived from them must comply with GPL-3.0, including source availability for the corresponding version.
- This repository does not yet contain a license for the original host code; that decision must be made before distributing source or binaries beyond the current development preview.
- Game intellectual property remains with its rights holders. The project distributes no game content.

## 5. Review triggers

Re-open this matrix when:

- The pipeline produces a runnable core and packaging decisions are made.
- Rumble, advanced controller features, or input remapping are scheduled.
- Frame pacing or refresh-rate control is implemented.
- Audio latency work begins.
- Any new dependency is proposed for the Android host.
