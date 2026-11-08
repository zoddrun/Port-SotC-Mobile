# PHASE 0 - Technical Research and Audit

Status: complete for the scope of this milestone.
Last updated: 2026-11-08.

This document records the upstream research that determined the architecture for
Port SotC Mobile. It is not a compatibility claim: no recompiled game core has
been produced yet, and no gameplay has been verified.

## 1. Objective and constraints

The project must execute Shadow of the Colossus (PlayStation 2, USA release
SCUS-97472) as native ARM64 code on Android. The following are explicitly out
of scope and must never be introduced:

- PS2 emulators or emulator frontends (PCSX2, AetherSX2, NetherSX2, Play!).
- RetroArch cores, Wine, or any compatibility layer.
- Remote or local game streaming.
- Bundled game data, ISO images, BIOS files, or extracted copyrighted assets.

The intended technical route is static recompilation: translate the original
MIPS R5900 program into native C/C++/ARM64 code ahead of time, then run that
translated program with a hardware abstraction layer for the PS2 subsystems it
uses. The user supplies their own legally obtained disc image.

## 2. Upstream projects inspected

The research checkouts were kept outside the repository in
`%TEMP%\opencode\sotc-research` and are not redistributed.

### 2.1 Celosia

- Repository: https://github.com/andai/Celosia (research checkout).
- License: GPL-3.0.
- Target: macOS on Apple Silicon and PlayStation Vita.
- Engine: a PS2Recomp-based recompilation plus a custom runtime and renderer.
- Reported state at audit time: boot, gameplay, audio and saves working on
  macOS for the supported release.

What matters for Android:

1. Celosia proves that a full static recompilation of this game can run.
2. Its supported release and asset expectations must be matched exactly; a
   different region or revision is not interchangeable.
3. The pipeline is host-driven. It needs a legally obtained ISO, a working
   recompiler toolchain, module capture runs, and a native build per target.
4. Celosia's Android support is not a drop-in solution. No Android app layer,
   SAF import flow, touch controller, Android audio backend, or Android save
   integration is provided by it.

The Celosia pipeline observed during the audit is:

1. Recompile the boot ELF (`SCUS_974.72`).
2. Run the recompiled program until it requests a module that has not been
   recompiled yet.
3. Capture the loaded module from a RAM dump.
4. Relink the captured `KERNEL.XFF`, `MANAGER.XFF`, and `GAMECORE.XFF` modules.
5. Recompile the relinked modules.
6. Capture and recompile VU1 microcode.
7. Build the native host.

Steps 2 through 6 require the disc image and substantial host tooling. They
cannot be performed on-device and are not part of this repository.

### 2.2 PS2Recomp

- Repository: https://github.com/ran-j/PS2Recomp (research checkout via Celosia).
- License: GPL-3.0.
- Role: the static recompiler and runtime used by Celosia.

Relevant findings:

- The runtime has a CMake path for Android and a NativeActivity-based runner.
- The Android runtime file inspected
  (`ps2xRuntime/src/lib/ps2_android_runtime.cpp`) is effectively an empty TODO.
  It does not provide EGL setup, audio, input, file access, or an application
  lifecycle.
- There is no Kotlin application, no Storage Access Framework import, no ISO
  validation, no touch layout, and no Android save management.

Conclusion: PS2Recomp is a viable basis for generating the game core, but it
does not provide the Android product. The Android host, storage model, input
system, diagnostics, and UX must be implemented by this project.

### 2.3 Fantaskink/SOTC

- Repository: https://github.com/Fantaskink/SOTC (research checkout).
- License: MIT.
- Target release: preview build `SCPS_150.97`.

Conclusion: not compatible with the USA retail release `SCUS-97472`. Its code
may be useful as a reference for tooling ideas, but it cannot be used as the
game core for this port. No code from it is currently included.

## 3. Disc structure facts used by the validator

The audit established the following facts, which are encoded in the Kotlin disc
layer and covered by synthetic-disc unit tests:

- The disc uses ISO9660 with a primary volume descriptor at sector 16.
- `SYSTEM.CNF` at the disc root declares the boot executable, for example:
  `BOOT2 = cdrom0:\SCUS_974.72;1`.
- The USA boot executable is `SCUS_974.72`.
- The serial is derived from the boot executable name (`SCUS-97472`).
- The PS2 boot executable is a 32-bit little-endian MIPS ELF with
  `e_machine = 8` (`EM_MIPS`) and type `ET_EXEC`.
- The engine modules expected by the recompilation pipeline are
  `KERNEL.XFF`, `MANAGER.XFF`, and `GAMECORE.XFF`.
- Recompiled module files start with the ASCII magic `xff2`.

Validation rejects a disc when the serial is unsupported, the boot file does
not match the release, the ELF header is invalid, or a required module is
missing or malformed. File names are never trusted on their own; the staged
copy is parsed.

## 4. Options considered

### Option A - Bundle an emulator

Rejected. It violates the project's core requirement and the AGENTS.md
prohibition on hiding an emulator behind a port. It would also create legal and
quality problems and would not be a native port.

### Option B - Wait for a complete upstream Android port

Rejected as a plan. No upstream project reviewed provides a complete Android
application for this release. Waiting would produce no software.

### Option C - Deliver the Android host now, generate the core separately

Chosen. This separates two independent problems:

1. **Android product work** (this repository): app, disc validation, staged
   import, storage, native engine host, EGL/GLES renderer, AAudio output,
   touch and gamepad input, settings, save isolation, lifecycle, diagnostics.
2. **Game core generation** (host pipeline, not in this repository): use the
   GPL-3.0 Celosia/PS2Recomp pipeline with a legally obtained `SCUS-97472`
   image to produce `libsotc_game.so`.

The host is designed against a documented, versioned C ABI. A core can be
produced and dropped in later without rewriting the Android application.

## 5. Architecture decisions resulting from the audit

- Kotlin application layer using platform `Activity` and custom views, with no
  AndroidX runtime UI dependency. This keeps the APK small and the behaviour
  predictable.
- C++20 native host (`libsotc_core.so`) owning EGL, GLES 3, AAudio, the render
  loop and the dynamic core loader.
- The game core is `libsotc_game.so`, loaded with `dlopen`, and must export
  `sotc_game_query_api`. ABI version 1 is defined in
  `app/src/main/cpp/sotc_game_api.h`.
- When no game core is present, the host renders a clearly labelled diagnostic
  scene. It never presents diagnostic content as gameplay.
- Import copies the user's ISO into app-private storage, validates the copy,
  extracts the module files, writes a SHA-256 manifest, and publishes the
  installation atomically. The original document is never deleted.
- Saves live inside the imported game directory so reinstalling the same title
  preserves them.

## 6. Licensing consequences

- Celosia and PS2Recomp are GPL-3.0. Any recompiled core produced from them is
  a derivative work and must be distributed under GPL-3.0 with corresponding
  source and notices. The core is not distributed by this repository.
- Fantaskink/SOTC is MIT but targets a different release and is not used.
- This repository currently contains no third-party runtime source code. The
  only test dependency is JUnit 4 (Eclipse Public License 1.0).
- See `THIRD_PARTY_LICENSES.md` for the full obligation list.

## 7. Open technical risks

1. **No core yet.** The largest risk is generating a working `SCUS-97472` core
   with the host pipeline. Until that exists, gameplay is unavailable.
2. **Renderer feature coverage.** A core may require GS features beyond the
   current host renderer. The ABI leaves rendering inside the core, so the
   core owns its GLES/Vulkan strategy; the host only provides the surface and
   resolution target.
3. **Performance.** PS2 emulation-class workloads are demanding. Static
   recompilation removes instruction decode overhead but not GS/VU workload.
   Thermal and frame-time measurements require real ARM64 hardware.
4. **Save format.** The host reserves a per-game save directory but the actual
   memory-card file format is owned by the game core. Cross-device save
   portability is not verified.
5. **Legal review.** Distribution of a GPL-3.0 core must be handled separately
   from this repository's own licensing.

## 8. Sources

- Celosia README and source tree (GPL-3.0), inspected 2026-11-08.
- PS2Recomp runtime and CMake files (GPL-3.0), inspected 2026-11-08.
- Fantaskink/SOTC README and release notes (MIT), inspected 2026-11-08.
- Project rules in `AGENTS.md`.
