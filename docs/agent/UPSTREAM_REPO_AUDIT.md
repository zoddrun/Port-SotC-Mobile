# Upstream Repository and Local Implementation Audit

> Agent memory file. Records what was inspected, at which exact revision, under which license, and what was adopted or rejected.
> Last updated: 2026-11-19

## 1. Purpose and evidence boundaries

This document combines two mandatory audit steps:

1. a complete audit of the local repository before any code was changed, and
2. a provenance-controlled review of the upstream repositories named in the expansion request.

Verification labels are used strictly throughout the agent documents:

| Label | Meaning |
|---|---|
| **IMPLEMENTED** | Source, resource, configuration, or script exists locally. |
| **COMPILED** | A compiled artifact exists and/or stored evidence records a successful compile or link. Not reproduced by this audit. |
| **PACKAGED** | The artifact is present in `jniLibs` and/or inside the APK. |
| **UNIT-TESTED** | A JVM test exists and passed in the stored JUnit XML. |
| **EMULATOR-VERIFIED** | Observed on the documented API 28 `x86_64` / SwiftShader path. Host diagnostics only. |
| **DEVICE-VERIFIED** | Observed on real ARM64 Android hardware. No such evidence exists. |
| **MISSING** | No local implementation or artifact found. |
| **BLOCKED** | Implementation exists but runtime validation cannot proceed with available evidence or hardware. |

Do not promote a lower level into a higher one. In particular, a successful EGL smoke test is **not** game rendering.

## 2. Local repository audit result

### 2.1 Headline verdict

The repository contains a substantial native Android host foundation and a compiled, packaged ARM64 runner artifact. It does **not** contain evidence of a playable port. No ARM64 device has ever loaded `libps2EntryRunner.so`.

> A real native Android host and a real compiled ARM64 runner are present locally, but no ARM64 device has ever loaded the runner, so gameplay compatibility remains unverified.

### 2.2 Inventory by area

| Area | Implemented | Compiled | Packaged | Unit-tested | Emulator-verified | Device-verified |
|---|---|---|---|---|---|---|
| Kotlin launcher, settings, import UI | Yes | Yes | Yes | Yes | Yes, host UI | No |
| ISO9660 / SYSTEM.CNF / ELF validation | Yes | Yes | Yes | Yes | Synthetic SAF import | No |
| Disc tree extraction | Yes | Yes | Yes | Yes | Reported | No |
| Host EGL/GLES diagnostics | Yes | Yes | Yes | GPU-info parsing | ES 2 only | No |
| ES 3 runtime path | Yes | Yes | Yes | No | No | No |
| Adaptive resolution (host only) | Yes | Yes | Yes | Settings/parser | Yes | No |
| Frame limiter (host only) | Yes | Yes | Yes | Sanitization only | No | No |
| Thermal ceiling (host only) | Yes | Yes | Yes | No | No | No |
| Low-memory direct-render fallback | Yes | Yes | Yes | No | Yes | No |
| Host AAudio engine | Yes | Yes | Yes | No | Startup/callbacks | No |
| OpenSL fallback | No | No | No | No | No | No |
| ABI-v1 `libsotc_game.so` core | Loader only | No core | No core | No | No | No |
| ARM64 runner `libps2EntryRunner.so` | Yes, via patched upstream chain | Yes | Yes | Static verifier | Cannot load on x86_64 | No |
| Runtime boot-path handoff | Yes | Yes | Yes | Yes | No | No |
| Runtime touch overlay | Patch exists | Yes | Yes | No | No | No |
| Runtime audio volume | Patch exists | Yes | Yes | No | No | No |
| Gameplay, game audio, saves | No verified execution | Runner exists | Runner packaged | No | No | No |
| First-party CI | No | No | No | No | No | No |

Stored JUnit evidence before this expansion: 17 suites, 95 tests, 0 failures, 0 errors, 0 skipped.

### 2.3 Two execution paths, one gameplay owner

- **Diagnostics / ABI-v1 path:** `GameActivity.kt` plus `libsotc_core.so`. Owns an EGL/GLES test renderer and is the only place where adaptive resolution, frame limiting, thermal ceilings, and low-memory fallback currently run. It is **not** the game.
- **Gameplay path:** `GameNativeActivity.kt` plus `lib/arm64-v8a/libps2EntryRunner.so`. Raylib's `NativeActivity` owns window creation, the frame loop, input polling, presentation, and audio. `PS2Runtime::run()` is the sole simulation owner.

The host `Engine` must never be started from `GameNativeActivity`. Work in this expansion connects settings to the runner, not to a second loop.

### 2.4 Artifacts and hashes

| Artifact | Value |
|---|---|
| Packaged runner | `app/src/main/jniLibs/arm64-v8a/libps2EntryRunner.so` |
| Runner size | `107,384,168` bytes |
| Runner SHA-256 | `134F2FBF3B0A5F260FFF8A01B5F36221AAEEC377242B602B7F6952B18B9AEB8C` |
| Unstripped runner | `777,574,008` bytes, BuildID `8c5f7557f979db6ad9bcc3b64785c64f6d83413c` |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| APK size (baseline) | `114,562,620` bytes |
| APK SHA-256 (baseline) | `D733DD5F95F50DA39F5BA7233E7DF72687B88BE8713CD90A2C4E2BE7921CA6ED` |
| Verifier result | `RUNNER_VERIFY_OK` |
| Original ISO SHA-256 | `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37` (private, ignored, untouched) |

APK native inventory at audit time: `libc++_shared.so` and `libsotc_core.so` for both `arm64-v8a` and `x86_64`, plus `arm64-v8a/libps2EntryRunner.so`. There is no x86_64 runner and no `libsotc_game.so`. **Changed later in this session (D24):** `app/build.gradle` switched to `c++_static`, so the current APK contains only `libsotc_core.so` for both ABIs and the ARM64 runner; `verify-apk.py` reports zero failures. The table above records the pre-change baseline that the audit inspected.

### 2.5 Defects and contradictions found by the audit

These are recorded here so they are fixed deliberately, not rediscovered.

1. **No ARM64 runtime evidence** — critical blocker; all gameplay claims remain unverified.
2. **Stale runner-status documentation** — several documents say the runner does not exist while others correctly say it is built and packaged. The truth is built, packaged, statically verified, never loaded.
3. **Patch 0001 status contradiction** — `KNOWN_ISSUES.md` says not compiled; build evidence says compiled and linked. Correct state: compiled/linked, not runtime-verified.
4. **Settings range mismatches** — host permits touch scale `0.6..1.6`, alpha `0.15..1.0`, deadzone `0..0.3`; runner clamps to `0.6..1.4`, `0.2..1.0`, `0..0.6`. Camera sensitivity host `0.4..2` fits runner `0.2..3`.
5. **`invertLookY` has no runner handoff** — UI and host touch view apply it; no `SOTC_INVERT_LOOK_Y` reader exists.
6. **`ImportActivity` checks the wrong library** — `CoreProbe.isInstalled` looks for `libsotc_game.so`, not the packaged runner, so the post-import message can be wrong.
7. **AAudio/OpenSL documentation error** — `audio_engine.cpp` is AAudio-only; CMake links only `aaudio`.
8. **ES 3 claim inconsistency** — historical claim of verified ES 3 surface conflicts with current honest status: ES 2 verified, ES 3 not.
9. **Stale test counts** — some documents say 49 tests; the stored XML and current runs report 95 before this expansion.
10. **Missing documented frame artifact** — `shots/run/frame_8.png` is referenced but absent.
11. **GPU display lifetime contradiction** — `gpu_info.cpp:188-190` says the EGL display is deliberately kept alive; `egl_renderer.cpp:225-235` calls `eglTerminate`. Requires review.
12. **No first-party CI** — only vendored third-party workflow files exist.
13. **FFmpeg disabled on Android** — documented limitation; cutscenes are expected to be stubbed.

Items 2, 3, 7, 8, and 9 are documentation corrections. Items 4 and 5 are addressed by honest captions and the runtime interface work in this expansion. Item 6 should be fixed in a follow-up import-flow change. Items 1, 10, 12, and 13 are tracked in `BLOCKERS.md` and `KNOWN_ISSUES.md`.

## 3. Upstream provenance

All inspected repositories are text/source only. No binaries, game assets, ISO data, BIOS files, or generated game code were copied. Short excerpts in the detailed research reports are from GPL-3.0 sources.

### 3.1 Exact revisions

| ID | Repository | URL | Commit inspected | License | Detailed report |
|---|---|---|---|---|---|
| A | Celosia upstream | `https://github.com/HenryKun55/celosia` | `1f8bb16d4fa1aff63d04a991893bde629efe802f` | GPL-3.0 | `docs/research/UPSTREAM_CELOSIA.md` |
| B | Celosia R36S fork | `https://github.com/amarqs182/celosia` | recorded in report; `r36s` working branch, `main` is contributions | GPL-3.0 | `docs/research/UPSTREAM_CELOSIA.md` |
| C | PS2Recomp | `https://github.com/ran-j/PS2Recomp` | `e5636981a1c9de368f6b71e819c3dc188dfedbf4` | GPL-3.0 | `docs/research/UPSTREAM_PLATFORM.md` |
| D | Android game samples | `https://github.com/android/games-samples` | `d7aab5fbbd1ea83f34e60f62d46e7654edaa104d` | Apache-2.0 | `docs/research/UPSTREAM_PLATFORM.md` |
| E | Oboe | `https://github.com/google/oboe` | `6925acbd57590ded8b449030e46b0bd5c017a2ca` | Apache-2.0 | `docs/research/UPSTREAM_PLATFORM.md` |
| F | sotc-vibe-pc | `https://github.com/LightVelox/sotc-vibe-pc` | `1ce4a5ea4955878bd2edabdc4e1a6b6f49a8e1ad` | see report | `docs/research/UPSTREAM_PLATFORM.md` |
| G | PCSX2 | `https://github.com/PCSX2/pcsx2` | `f0c84e93813ae3d5e9eefd0ac9edf1bbd86e315f` | GPL-3.0 | `docs/research/UPSTREAM_PLATFORM.md` |
| H | ANGLE | `https://github.com/google/angle` | `64a0b11458e24cc3afa7e04a2fb534109f30f7ab` | BSD-3-Clause | `docs/research/UPSTREAM_PLATFORM.md` |
| I | raylib | `https://github.com/raysan5/raylib` | tag `5.5` | zlib | `docs/research/RUNNER_INTEGRATION.md` |

The local WSL checkout used for the runner is `/root/celosia` at `f8c269e5a5dc3b7afd7b25d25fe56684e1ad96f5`, with patches 0001-0008 applied and unrelated developer capture-host fixes present. Its HEAD must not be reset or overwritten.

### 3.2 Upstream findings relevant to the port

- **Celosia upstream** targets macOS and PS Vita with static recompilation. It supports the PS2 SOTC build path used here; the project's validated serial remains `SCUS-97472`. Its `PS2Runtime`, recompiler, SPU2/IOP/DMA/VU1 and memory-card logic are the authority for behaviour. `SetTargetFPS` is presentation pacing only; the guest time step is separate. `SOTC_CAP20` / Steady 20 changes guest release cadence and must not be used as an app frame-limit setting.
- **R36S fork** documents Mali-G31 GLES 3.2 work, selective LTO, function ordering, draw-call reduction, texture-array batching, vertex-array caching, and frame-time instrumentation. These are ideas, not drop-in code. Its Linux/KMSDRM/SDL2, custom memory mapping, and driver-specific paths must not be copied. Anything adopted needs a measurable reason and a regression test, and most changes require rebuilding the WSL runner.
- **PS2Recomp** provides the static recompilation/runtime boundary and a useful stdout-to-logcat concept. Its Android sample uses raylib `NativeActivity`, has Gradle/AGP/NDK versions that conflict with this project, and has no Kotlin UI or SAF importer. Wholesale replacement is rejected; the logcat concept is adopted as an independently implemented pattern.
- **games-samples**: adopt `__builtin_available` / weak-symbol guards, 16 KB page-size awareness, and scenario-test structure. ADPF, Game Mode, and GameController are evaluate-later pending API guards. GameActivity and the Frame Pacing shell are rejected because they would replace the single gameplay owner.
- **Oboe**: evaluate later only. The host engine has one AAudio stream used for diagnostics, and the runner owns game audio. Adding Oboe now would create two audio engines or an unproven migration.
- **sotc-vibe-pc**: adopt only the automated scenario/regression/differential testing methodology. Different region, Windows, OpenGL 4.6; no generated code imported.
- **PCSX2**: reference oracle for PS2 semantics and graphics comparisons only. Never bundled, never a backend.
- **ANGLE**: optional host-side compatibility/testing reference only. Not a phone dependency, not bundled.

### 3.3 Import and change-management rules applied

1. Exact repository URL and commit SHA are recorded above before any reuse.
2. Licences are confirmed and retained in the detailed reports.
3. Only specific files, algorithms, and patterns are identified, never entire frameworks.
4. Duplicate existing functionality was checked first; existing Kotlin/UI/SAF/AAudio/EGL code is preserved.
5. New runtime behaviour is isolated in one proposed upstream patch (0009) and one environment channel, both testable without a phone.
6. Nothing is published or force-pushed; work is committed locally at milestones.

## 4. Decisions log

- Keep Kotlin + C++20 + EGL/GLES + AAudio host architecture.
- Keep the launcher renderer floor at OpenGL ES 2.0 with opportunistic ES3; GLSL ES 1.00 shaders.
- Keep raylib/Celosia as the sole gameplay owner; host `Engine` stays diagnostics/ABI-v1.
- Publish settings as process environment variables before `super.onCreate`; do not add a JNI settings path.
- Use `SOTC_FRAME_LIMIT`, not `SOTC_CAP20`, for the app's presentation cap.
- Preserve the current packaged runner until any rebuilt artifact passes the full no-phone gate.
- Reject wholesale GameActivity, Oboe-as-second-engine, PCSX2 runtime, ANGLE bundling, and verbatim Linux/Mali/KMSDRM code.

## 5. Related documents

- `docs/research/UPSTREAM_CELOSIA.md`
- `docs/research/UPSTREAM_PLATFORM.md`
- `docs/research/RUNNER_INTEGRATION.md`
- `docs/agent/GPU_COMPATIBILITY_MATRIX.md`
- `docs/agent/PERFORMANCE_ARCHITECTURE.md`
- `docs/agent/ANDROID_RUNTIME_INTEGRATION.md`
