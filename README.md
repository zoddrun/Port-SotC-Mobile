# Port SotC Mobile

A native Android port of **Shadow of the Colossus** (PlayStation 2, USA
`SCUS-97472`) built around static recompilation, not emulation.

> **Current status: host foundation, not playable yet.**
> The Android application, disc import/validation, input system, native
> EGL/GLES3 host for arm64, and AAudio output are implemented and build
> successfully. The statically recompiled game core (`libsotc_game.so`) has
> not been produced, so there is no gameplay yet. The app states this
> explicitly instead of showing placeholder gameplay.

## What this is

- A native Android application written in Kotlin with a C++20/NDK host.
- A staged, validating importer for a user-supplied ISO image.
- A native EGL/GLES 3 renderer and AAudio output path for `arm64-v8a`.
- A PS2 virtual controller (multitouch) plus physical gamepad support.
- A versioned C ABI for loading a separately generated recompiled game core.
- The developer-supplied application identity artwork as the launcher icon
  (adaptive, including themed-icon support; see
  [`docs/ICON_ASSET.md`](docs/ICON_ASSET.md)).

## What this is not

- Not a PS2 emulator or an emulator frontend.
- Not a wrapper around PCSX2, AetherSX2, NetherSX2, RetroArch, Play!, or Wine.
- Not a game-streaming client.
- Not a distribution of BIOS files, ISO images, game assets, or recompiled
  game code.

## Verified state

On 2026-11-09, on the development host and an Android 9 (API 28) `x86_64`
emulator with SwiftShader:

| Check | Result |
| --- | --- |
| `gradlew testDebugUnitTest` | 49 tests, 0 failures |
| `gradlew lintDebug` | Passed (warnings only) |
| `gradlew assembleDebug` | Passed for `arm64-v8a` and `x86_64` |
| Emulator install and launch | Verified |
| EGL/GLES 3 surface | Verified (EGL 1.4, GLES 3.0, `RGBA8888 depth24 stencil8`) |
| AAudio output | Verified (48 kHz stereo, callback counter advancing) |
| Pause/resume and lifecycle | Verified |
| Touch, multitouch, and layout editor | Verified with debug input logs |
| Disc import through SAF | Verified with a synthetic ISO9660 fixture |
| Original retail disc image | Validated on the development host (SCUS-97472, see below) |
| ARM64 hardware execution | Not verified (no ARM64 device available) |
| Gameplay | Not available (no game core) |

The runtime checks cover Android integration only. They do not cover ARM64
performance, thermal behaviour, real game rendering, or real game audio. See
[`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) for the full evidence and the
remaining limitations.

## Original disc image validation

A legally obtained USA retail image of `SCUS-97472` is present on the
development host under `original iso/` (gitignored; never committed) and was
validated on 2026-11-09 with `tools/inspect_original_iso.py`, which mirrors the
in-app `DiscValidator` logic using only the Python standard library:

| Check | Result |
| --- | --- |
| Serial | `SCUS-97472` (from `SYSTEM.CNF` boot file `SCUS_974.72`) |
| Boot ELF | `/SCUS_974.72`, 242,940 bytes, entry `0x00100010`, 2 loadable segments |
| Modules | `KERNEL.XFF`, `MANAGER.XFF`, `GAMECORE.XFF` all pass the `xff2` magic check |
| Volume | `PLAYSTATION`/`SCEA`, 2048-byte blocks, 1,315,408 sectors (matches image) |
| SHA-256 | `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37` |

The machine-readable report is written to `build/reports/original_iso.json`.
This validates the disc content itself; importing this specific image through
the on-device SAF flow has not yet been exercised.

## Implemented components

- **Disc layer:** ISO9660 parsing, `SYSTEM.CNF`, PS2 ELF validation, serial and
  module checks.
- **Import:** SAF copy with SHA-256, progress/cancel, free-space checks, atomic
  publish, interrupted-import recovery, and save preservation.
- **Storage:** per-game private directory with manifest, ISO copy, modules, and
  reserved saves.
- **Native host:** EGL/GLES 3 render thread, resolution-scaled offscreen render
  target, AAudio float stereo, diagnostic scene, status JSON, and core loader.
- **Input:** SCE_PAD-compatible button bits, two analog sticks with deadzone,
  multitouch layout editor, physical gamepad mapping, vibration setting.
- **UI:** main, import, game/diagnostics, settings, controller, and touch layout
  editor screens. English only.

## Quick build

Prerequisites: JDK 17, Android SDK 34, NDK `26.1.10909125`, CMake `3.22.1`.
Create an untracked `local.properties` with `sdk.dir`.

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-17"
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Full instructions, device installation, import steps, core side-loading, and
troubleshooting: [`docs/BUILDING_ANDROID.md`](docs/BUILDING_ANDROID.md).

## Why gameplay is not available yet

Static recompilation is a host-side build process, not something an Android
phone can perform on demand. It requires:

1. A legally obtained `SCUS-97472` disc image.
2. The GPL-3.0 Celosia/PS2Recomp toolchain.
3. Recompiling the boot ELF.
4. Capturing and relinking `KERNEL.XFF`, `MANAGER.XFF`, and `GAMECORE.XFF`.
5. Recompiling VU1 microcode and producing an ARM64 core.

That pipeline is not included in this repository and has not been executed
here. The Android host is ready to load a core that implements ABI version 1 in
`app/src/main/cpp/sotc_game_api.h`. Until then, the game screen runs a clearly
labelled diagnostic renderer, never fake gameplay.

## Documentation

- [`docs/RESEARCH.md`](docs/RESEARCH.md) - upstream audit and technical decisions.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - host, storage, import, input,
  render, audio, and core ABI design.
- [`docs/ANDROID_PORTING_PLAN.md`](docs/ANDROID_PORTING_PLAN.md) - phase status
  and exit criteria.
- [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) - supported disc, Android
  requirements, verified and unverified behaviour.
- [`docs/THIRD_PARTY_LICENSES.md`](docs/THIRD_PARTY_LICENSES.md) - licenses and
  distribution obligations.
- [`docs/BUILDING_ANDROID.md`](docs/BUILDING_ANDROID.md) - build and run guide.

## Legal

Users must supply their own legally obtained game disc image. This project does
not include or distribute copyrighted game content, PlayStation 2 BIOS files,
game assets, or a recompiled game core.

Celosia and PS2Recomp are GPL-3.0; any core produced from them carries
GPL-3.0 obligations. See `docs/THIRD_PARTY_LICENSES.md`.

The license for this repository's original code has not been declared yet.
